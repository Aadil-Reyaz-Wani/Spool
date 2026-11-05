begin;
set local lock_timeout = '5s';
set local statement_timeout = '30s';

-- Read-only review/refetch. Writes still use the existing revision/idempotency RPC.
create function public.get_inventory_snapshot(p_expected_owner uuid, p_spool_uuid uuid) returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare owner uuid := auth.uid();
begin
    if owner is null or owner is distinct from p_expected_owner then
        raise exception 'Authenticated account does not match snapshot owner' using errcode = '42501';
    end if;
    if p_spool_uuid is null then
        raise exception 'Spool UUID is required' using errcode = '22023';
    end if;
    return spool_private.inventory_snapshot(owner, p_spool_uuid);
end;
$$;
revoke all on function public.get_inventory_snapshot(uuid, uuid) from public, anon, authenticated;
grant execute on function public.get_inventory_snapshot(uuid, uuid) to authenticated;
commit;
