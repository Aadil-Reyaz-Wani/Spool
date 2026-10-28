begin;
set local lock_timeout = '5s';
set local statement_timeout = '30s';

create table spool_private.inventory_upload_receipts (
    owner_id uuid not null references auth.users(id) on delete cascade,
    batch_id uuid not null,
    request_hash bytea not null,
    response jsonb not null,
    primary key (owner_id, batch_id)
);
revoke all on spool_private.inventory_upload_receipts from public, anon, authenticated;

-- All client writes now go through the aggregate revision/idempotency check.
do $$
declare t text; cols text;
begin
    foreach t in array array['inventory_spools', 'inventory_usage_logs'] loop
        select string_agg(quote_ident(attname), ',') into cols from pg_attribute
            where attrelid = format('public.%I', t)::regclass and attnum > 0 and not attisdropped;
        execute format('revoke all privileges (%s) on table public.%I from authenticated', cols, t);
        execute format('revoke insert, update, delete on table public.%I from authenticated', t);
    end loop;
end;
$$;

create function spool_private.inventory_snapshot(owner uuid, spool uuid) returns jsonb
language sql security invoker set search_path = '' as $$
    select jsonb_build_object('spool', (select to_jsonb(s) from public.inventory_spools s where s.owner_id = owner and s.uuid = spool),
        'logs', (select coalesce(jsonb_agg(to_jsonb(l) order by l.uuid), '[]'::jsonb)
            from public.inventory_usage_logs l where l.owner_id = owner and l.spool_uuid = spool));
$$;
revoke all on function spool_private.inventory_snapshot(uuid, uuid) from public, anon, authenticated;

create function public.upload_inventory_batch(
    p_expected_owner uuid, p_batch_id uuid, p_spool_uuid uuid, p_base_revision bigint,
    p_spool jsonb, p_deleted boolean, p_logs jsonb
) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
    owner uuid := auth.uid();
    fingerprint bytea;
    receipt spool_private.inventory_upload_receipts;
    current_spool public.inventory_spools;
    spool_value public.inventory_spools;
    log_value public.inventory_usage_logs;
    existing_log public.inventory_usage_logs;
    item jsonb;
    result jsonb;
    current_revision bigint;
begin
    if owner is null or owner is distinct from p_expected_owner then
        raise exception 'Authenticated account does not match upload owner' using errcode = '42501';
    end if;
    if p_batch_id is null or p_spool_uuid is null or p_base_revision is null or p_base_revision < 0
        or p_deleted is null or jsonb_typeof(p_spool) is distinct from 'object'
        or jsonb_typeof(p_logs) is distinct from 'array' then
        raise exception 'Invalid upload envelope' using errcode = '22023';
    end if;
    if octet_length(p_spool::text) + octet_length(p_logs::text) > 4194304 or jsonb_array_length(p_logs) > 10000 then
        raise exception 'Upload is too large' using errcode = '22023';
    end if;
    fingerprint := sha256(convert_to(jsonb_build_object('spool_uuid', p_spool_uuid, 'base_revision', p_base_revision,
        'spool', p_spool, 'deleted', p_deleted, 'logs', p_logs)::text, 'UTF8'));
    -- Serializes same-account first creation, receipts and concurrent spool batches, including absent rows.
    perform pg_advisory_xact_lock(hashtextextended(owner::text, 0));
    select * into receipt from spool_private.inventory_upload_receipts r where r.owner_id = owner and r.batch_id = p_batch_id;
    if found then
        if receipt.request_hash <> fingerprint then
            raise exception 'A batch ID cannot be reused for different content' using errcode = '22023';
        end if;
        return receipt.response;
    end if;
    if p_spool->>'version' is distinct from '1' or (p_spool->>'uuid')::uuid is distinct from p_spool_uuid
        or p_spool - array['version','uuid','brand','material','color_name','color_hex','total_weight','current_weight',
            'temp_nozzle','temp_bed','note','price','timestamp','dry_baseline_weight','dry_baseline_tare_grams',
            'last_weighed_tare_grams','last_dried_at'] <> '{}'::jsonb then
        raise exception 'Invalid spool snapshot' using errcode = '22023';
    end if;
    if exists (select 1 from jsonb_array_elements(p_logs) x group by x->>'uuid' having count(*) > 1) then
        raise exception 'Duplicate history UUID in upload' using errcode = '22023';
    end if;
    for item in select * from jsonb_array_elements(p_logs) loop
        if jsonb_typeof(item) is distinct from 'object' or item->>'operation' not in ('upsert','delete')
            or item->>'operation' is null or item->>'uuid' is null
            or item - array['uuid','operation','payload'] <> '{}'::jsonb
            or jsonb_typeof(item->'payload') is distinct from 'object'
            or item->'payload'->>'version' is distinct from '1'
            or (item->'payload'->>'uuid')::uuid is distinct from (item->>'uuid')::uuid
            or (item->'payload'->>'spool_uuid')::uuid is distinct from p_spool_uuid
            or (item->'payload') - array['version','uuid','spool_uuid','title','grams_used','timestamp','is_failure','price_per_print'] <> '{}'::jsonb then
            raise exception 'Invalid history snapshot' using errcode = '22023';
        end if;
        select * into existing_log from public.inventory_usage_logs l where l.owner_id = owner and l.uuid = (item->>'uuid')::uuid;
        if found then
            if existing_log.spool_uuid <> p_spool_uuid then
                raise exception 'History UUID already belongs to another spool' using errcode = '23514';
            end if;
        end if;
    end loop;
    select * into current_spool from public.inventory_spools s where s.owner_id = owner and s.uuid = p_spool_uuid for update;
    current_revision := coalesce(current_spool.revision, 0);
    if current_revision <> p_base_revision or current_spool.deleted_at is not null then
        result := jsonb_build_object('status', 'conflict', 'batch_id', p_batch_id, 'spool_uuid', p_spool_uuid,
            'revision', current_revision, 'server', spool_private.inventory_snapshot(owner, p_spool_uuid));
    else
        -- A deleted history UUID cannot be recreated by a stale upload even if its parent is live.
        if exists (select 1 from jsonb_array_elements(p_logs) x join public.inventory_usage_logs l
            on l.owner_id = owner and l.uuid = (x->>'uuid')::uuid
            where l.deleted_at is not null and x->>'operation' = 'upsert') then
            result := jsonb_build_object('status', 'conflict', 'batch_id', p_batch_id, 'spool_uuid', p_spool_uuid,
                'revision', current_revision, 'server', spool_private.inventory_snapshot(owner, p_spool_uuid));
        else
            spool_value := jsonb_populate_record(null::public.inventory_spools, p_spool);
            insert into public.inventory_spools (owner_id, uuid, brand, material, color_name, color_hex, total_weight,
                current_weight, temp_nozzle, temp_bed, note, price, timestamp, dry_baseline_weight,
                dry_baseline_tare_grams, last_weighed_tare_grams, last_dried_at)
            values (owner, p_spool_uuid, spool_value.brand, spool_value.material, spool_value.color_name, spool_value.color_hex,
                spool_value.total_weight, spool_value.current_weight, spool_value.temp_nozzle, spool_value.temp_bed,
                spool_value.note, spool_value.price, spool_value.timestamp, spool_value.dry_baseline_weight,
                spool_value.dry_baseline_tare_grams, spool_value.last_weighed_tare_grams, spool_value.last_dried_at)
            on conflict (owner_id, uuid) do update set
                brand = excluded.brand, material = excluded.material, color_name = excluded.color_name, color_hex = excluded.color_hex,
                total_weight = excluded.total_weight, current_weight = excluded.current_weight, temp_nozzle = excluded.temp_nozzle,
                temp_bed = excluded.temp_bed, note = excluded.note, price = excluded.price, timestamp = excluded.timestamp,
                dry_baseline_weight = excluded.dry_baseline_weight, dry_baseline_tare_grams = excluded.dry_baseline_tare_grams,
                last_weighed_tare_grams = excluded.last_weighed_tare_grams, last_dried_at = excluded.last_dried_at;
            for item in select * from jsonb_array_elements(p_logs) loop
                select * into existing_log from public.inventory_usage_logs l where l.owner_id = owner and l.uuid = (item->>'uuid')::uuid;
                if found and existing_log.deleted_at is not null then continue; end if;
                -- SQLite snapshots use 0/1; Postgres snapshots use booleans. Accept only these representations.
                if item->'payload'->>'is_failure' not in ('0','1','false','true') or item->'payload'->>'is_failure' is null then
                    raise exception 'Invalid failure flag' using errcode = '22023';
                end if;
                log_value := jsonb_populate_record(null::public.inventory_usage_logs, (item->'payload') ||
                    jsonb_build_object('is_failure', item->'payload'->>'is_failure' in ('1','true')));
                insert into public.inventory_usage_logs (owner_id, uuid, spool_uuid, title, grams_used, timestamp, is_failure, price_per_print)
                values (owner, log_value.uuid, p_spool_uuid, log_value.title, log_value.grams_used,
                    log_value.timestamp, log_value.is_failure, log_value.price_per_print)
                on conflict (owner_id, uuid) do update set title = excluded.title, grams_used = excluded.grams_used,
                    timestamp = excluded.timestamp, is_failure = excluded.is_failure, price_per_print = excluded.price_per_print;
                if item->>'operation' = 'delete' then
                    update public.inventory_usage_logs set deleted_at = clock_timestamp() where owner_id = owner and uuid = log_value.uuid;
                end if;
            end loop;
            if p_deleted then
                update public.inventory_spools set deleted_at = clock_timestamp() where owner_id = owner and uuid = p_spool_uuid;
            end if;
            select s.revision into current_revision from public.inventory_spools s where s.owner_id = owner and s.uuid = p_spool_uuid;
            result := jsonb_build_object('status', 'applied', 'batch_id', p_batch_id, 'spool_uuid', p_spool_uuid, 'revision', current_revision);
        end if;
    end if;
    insert into spool_private.inventory_upload_receipts values (owner, p_batch_id, fingerprint, result);
    return result;
end;
$$;
revoke all on function public.upload_inventory_batch(uuid, uuid, uuid, bigint, jsonb, boolean, jsonb) from public, anon, authenticated;
grant execute on function public.upload_inventory_batch(uuid, uuid, uuid, bigint, jsonb, boolean, jsonb) to authenticated;
commit;
