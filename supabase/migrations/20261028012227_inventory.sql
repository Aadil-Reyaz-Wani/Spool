-- Account-owned cloud storage only. The app's local queue is not uploaded by this migration.
begin;
set local lock_timeout = '5s';
set local statement_timeout = '30s';

create schema if not exists spool_private;
revoke all on schema spool_private from public, anon, authenticated;

create table public.inventory_spools (
    owner_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
    uuid uuid not null,
    brand text not null default '',
    material text not null default '',
    color_name text not null default '',
    color_hex bigint not null default 0,
    total_weight double precision not null default 0 check (total_weight >= 0 and total_weight < 'Infinity'::double precision),
    current_weight double precision not null default 0 check (current_weight >= 0 and current_weight < 'Infinity'::double precision),
    temp_nozzle integer not null default 0,
    temp_bed integer not null default 0,
    note text not null default '',
    price double precision not null default 0 check (price >= 0 and price < 'Infinity'::double precision),
    timestamp bigint not null default 0,
    dry_baseline_weight double precision check (dry_baseline_weight >= 0 and dry_baseline_weight < 'Infinity'::double precision),
    dry_baseline_tare_grams double precision check (dry_baseline_tare_grams >= 0 and dry_baseline_tare_grams < 'Infinity'::double precision),
    last_weighed_tare_grams double precision check (last_weighed_tare_grams >= 0 and last_weighed_tare_grams < 'Infinity'::double precision),
    last_dried_at bigint,
    revision bigint not null default 1,
    created_at timestamptz not null default clock_timestamp(),
    updated_at timestamptz not null default clock_timestamp(),
    deleted_at timestamptz,
    primary key (owner_id, uuid)
);

create table public.inventory_usage_logs (
    owner_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
    uuid uuid not null,
    spool_uuid uuid not null,
    title text not null default '',
    grams_used double precision not null check (grams_used >= 0 and grams_used < 'Infinity'::double precision),
    timestamp bigint not null default 0,
    is_failure boolean not null default false,
    price_per_print double precision not null default 0 check (price_per_print >= 0 and price_per_print < 'Infinity'::double precision),
    revision bigint not null default 1,
    created_at timestamptz not null default clock_timestamp(),
    updated_at timestamptz not null default clock_timestamp(),
    deleted_at timestamptz,
    primary key (owner_id, uuid),
    foreign key (owner_id, spool_uuid) references public.inventory_spools(owner_id, uuid) on delete cascade
);

create table public.inventory_tombstones (
    owner_id uuid not null references auth.users(id) on delete cascade,
    entity_type text not null check (entity_type in ('spool', 'usage_log')),
    entity_uuid uuid not null,
    spool_uuid uuid not null,
    payload jsonb not null check (jsonb_typeof(payload) = 'object'),
    deleted_at timestamptz not null,
    recovery_expires_at timestamptz not null,
    primary key (owner_id, entity_type, entity_uuid)
);

create index inventory_spools_changes on public.inventory_spools (owner_id, updated_at, uuid);
create index inventory_usage_logs_changes on public.inventory_usage_logs (owner_id, updated_at, uuid);
create index inventory_usage_logs_parent on public.inventory_usage_logs (owner_id, spool_uuid);
create index inventory_tombstones_changes on public.inventory_tombstones (owner_id, deleted_at, entity_type, entity_uuid);

alter table public.inventory_spools enable row level security;
alter table public.inventory_usage_logs enable row level security;
alter table public.inventory_tombstones enable row level security;

create policy spools_read_own on public.inventory_spools for select to authenticated
    using (owner_id = (select auth.uid()));
create policy spools_insert_own on public.inventory_spools for insert to authenticated
    with check (owner_id = (select auth.uid()));
create policy spools_update_own on public.inventory_spools for update to authenticated
    using (owner_id = (select auth.uid())) with check (owner_id = (select auth.uid()));
create policy logs_read_own on public.inventory_usage_logs for select to authenticated
    using (owner_id = (select auth.uid()));
create policy logs_insert_own on public.inventory_usage_logs for insert to authenticated
    with check (owner_id = (select auth.uid()));
create policy logs_update_own on public.inventory_usage_logs for update to authenticated
    using (owner_id = (select auth.uid())) with check (owner_id = (select auth.uid()));
create policy tombstones_read_own on public.inventory_tombstones for select to authenticated
    using (owner_id = (select auth.uid()));

-- Explicit column grants keep identity and server metadata outside client control.
revoke all on public.inventory_spools, public.inventory_usage_logs, public.inventory_tombstones from public, anon, authenticated;
grant select on public.inventory_spools, public.inventory_usage_logs, public.inventory_tombstones to authenticated;
grant insert (owner_id, uuid, brand, material, color_name, color_hex, total_weight, current_weight,
    temp_nozzle, temp_bed, note, price, timestamp, dry_baseline_weight, dry_baseline_tare_grams,
    last_weighed_tare_grams, last_dried_at, deleted_at) on public.inventory_spools to authenticated;
grant update (brand, material, color_name, color_hex, total_weight, current_weight,
    temp_nozzle, temp_bed, note, price, timestamp, dry_baseline_weight, dry_baseline_tare_grams,
    last_weighed_tare_grams, last_dried_at, deleted_at) on public.inventory_spools to authenticated;
grant insert (owner_id, uuid, spool_uuid, title, grams_used, timestamp, is_failure, price_per_print, deleted_at)
    on public.inventory_usage_logs to authenticated;
grant update (title, grams_used, timestamp, is_failure, price_per_print, deleted_at)
    on public.inventory_usage_logs to authenticated;

create function spool_private.prepare_inventory_write() returns trigger
language plpgsql security invoker set search_path = '' as $$
declare
    parent_deleted_at timestamptz;
begin
    if tg_op = 'UPDATE' then
        if new.owner_id is distinct from old.owner_id or new.uuid is distinct from old.uuid then
            raise exception 'Inventory identity is immutable' using errcode = '23514';
        end if;
        if tg_table_name = 'inventory_usage_logs' then
            if new.spool_uuid is distinct from old.spool_uuid then
                raise exception 'History parent is immutable' using errcode = '23514';
            end if;
        end if;
        if old.deleted_at is not null then
            raise exception 'Deleted inventory requires an explicit recovery operation' using errcode = '23514';
        end if;
        new.revision := old.revision + 1;
        new.created_at := old.created_at;
    else
        new.revision := 1;
        new.created_at := clock_timestamp();
    end if;
    new.updated_at := clock_timestamp();
    if new.deleted_at is not null then
        new.deleted_at := new.updated_at;
    end if;
    if tg_table_name = 'inventory_usage_logs' and new.deleted_at is null then
        -- SHARE also serializes child writes against a concurrent parent soft deletion.
        select s.deleted_at into parent_deleted_at from public.inventory_spools s
            where s.owner_id = new.owner_id and s.uuid = new.spool_uuid for share;
        if not found or parent_deleted_at is not null then
            raise exception 'History requires a live spool owned by this account' using errcode = '23503';
        end if;
    end if;
    return new;
end;
$$;

-- Only triggers invoke this privileged function. The owner and parent come from a checked row.
create function spool_private.capture_inventory_deletion() returns trigger
language plpgsql security definer set search_path = '' as $$
declare
    snapshot jsonb;
    kind text;
    parent_uuid uuid;
begin
    if new.deleted_at is null then return new; end if;
    if tg_op = 'UPDATE' then
        if old.deleted_at is not null then return new; end if;
        snapshot := to_jsonb(old);
    else
        snapshot := to_jsonb(new);
    end if;
    if tg_table_name = 'inventory_spools' then
        kind := 'spool';
        parent_uuid := new.uuid;
    else
        kind := 'usage_log';
        parent_uuid := new.spool_uuid;
    end if;
    insert into public.inventory_tombstones
        (owner_id, entity_type, entity_uuid, spool_uuid, payload, deleted_at, recovery_expires_at)
    values (new.owner_id, kind, new.uuid, parent_uuid,
        jsonb_build_object('version', 1) || snapshot, new.deleted_at, new.deleted_at + interval '30 days');
    if kind = 'spool' then
        update public.inventory_usage_logs set deleted_at = new.deleted_at
            where owner_id = new.owner_id and spool_uuid = new.uuid and deleted_at is null;
    end if;
    return new;
end;
$$;

revoke all on function spool_private.prepare_inventory_write() from public, anon, authenticated;
revoke all on function spool_private.capture_inventory_deletion() from public, anon, authenticated;

create trigger prepare_spool_write before insert or update on public.inventory_spools
    for each row execute function spool_private.prepare_inventory_write();
create trigger prepare_usage_log_write before insert or update on public.inventory_usage_logs
    for each row execute function spool_private.prepare_inventory_write();
create trigger capture_spool_deletion after insert or update on public.inventory_spools
    for each row execute function spool_private.capture_inventory_deletion();
create trigger capture_usage_log_deletion after insert or update on public.inventory_usage_logs
    for each row execute function spool_private.capture_inventory_deletion();

comment on table public.inventory_spools is 'Cloud inventory foundation; writes require the later sync/conflict protocol before enabling app uploads.';
comment on table public.inventory_tombstones is 'Server-owned recovery snapshots. Do not purge until retention and device reconciliation are implemented.';

commit;
