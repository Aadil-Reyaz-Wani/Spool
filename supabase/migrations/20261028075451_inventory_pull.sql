begin;
set local lock_timeout = '5s';
set local statement_timeout = '30s';

-- A transactional per-account counter is held until commit. Sequence values and timestamps
-- alone can skip a transaction that allocated its value early but committed after a pull.
create table spool_private.inventory_change_counters (
    owner_id uuid primary key references auth.users(id) on delete cascade,
    value bigint not null check (value > 0)
);
create table spool_private.inventory_changes (
    owner_id uuid not null references auth.users(id) on delete cascade,
    spool_uuid uuid not null,
    position bigint not null check (position > 0),
    primary key (owner_id, spool_uuid),
    unique (owner_id, position)
);
revoke all on spool_private.inventory_change_counters, spool_private.inventory_changes from public, anon, authenticated;

create function spool_private.track_inventory_change() returns trigger
language plpgsql security definer set search_path = '' as $$
declare next_position bigint; parent uuid;
begin
    if tg_table_name = 'inventory_spools' then parent := new.uuid; else parent := new.spool_uuid; end if;
    insert into spool_private.inventory_change_counters as c (owner_id, value) values (new.owner_id, 1)
        on conflict (owner_id) do update set value = c.value + 1 returning value into next_position;
    insert into spool_private.inventory_changes (owner_id, spool_uuid, position) values (new.owner_id, parent, next_position)
        on conflict (owner_id, spool_uuid) do update set position = excluded.position;
    return new;
end;
$$;
revoke all on function spool_private.track_inventory_change() from public, anon, authenticated;
create trigger inventory_spools_pull after insert or update on public.inventory_spools
    for each row execute function spool_private.track_inventory_change();
create trigger inventory_logs_pull after insert or update on public.inventory_usage_logs
    for each row execute function spool_private.track_inventory_change();

-- Seed existing live and deleted aggregates without editing inventory values/revisions.
insert into spool_private.inventory_changes (owner_id, spool_uuid, position)
    select owner_id, uuid, row_number() over (partition by owner_id order by uuid) from public.inventory_spools;
insert into spool_private.inventory_change_counters (owner_id, value)
    select owner_id, max(position) from spool_private.inventory_changes group by owner_id;

create function public.pull_inventory(p_expected_owner uuid, p_cursor bigint) returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare owner uuid := auth.uid(); result jsonb;
begin
    if owner is null or owner is distinct from p_expected_owner then
        raise exception 'Authenticated account does not match download owner' using errcode = '42501';
    end if;
    if p_cursor is null or p_cursor < 0 then
        raise exception 'Invalid download cursor' using errcode = '22023';
    end if;
    -- One complete aggregate per page. Parent revisions make later replays harmless.
    with next_change as materialized (
        select spool_uuid, position from spool_private.inventory_changes
        where owner_id = owner and position > p_cursor order by position limit 1
    )
    select jsonb_build_object('owner_id', owner, 'cursor', p_cursor,
        'next_cursor', coalesce((select position from next_change), p_cursor),
        'has_more', exists (select 1 from spool_private.inventory_changes
            where owner_id = owner and position > (select position from next_change)),
        'snapshot', (select spool_private.inventory_snapshot(owner, spool_uuid) from next_change)) into result;
    return result;
end;
$$;
revoke all on function public.pull_inventory(uuid, bigint) from public, anon, authenticated;
grant execute on function public.pull_inventory(uuid, bigint) to authenticated;
commit;
