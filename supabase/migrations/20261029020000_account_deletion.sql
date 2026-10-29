begin;
set local lock_timeout = '5s';
set local statement_timeout = '30s';

-- Expose only self-deletion, never a general auth-admin operation or service key.
create function public.delete_spool_account(p_expected_owner uuid) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare owner uuid := auth.uid();
begin
    if owner is null or auth.role() is distinct from 'authenticated' or
        p_expected_owner is null or owner is distinct from p_expected_owner then
        raise exception 'Account does not match authenticated user' using errcode = '42501';
    end if;
    -- Same account lock as upload: a concurrent upload cannot recreate deleted inventory.
    perform pg_advisory_xact_lock(hashtextextended(owner::text, 0));
    -- Foreign keys cascade inventory, history, tombstones, receipts, change cursors,
    -- identities and auth sessions in this transaction. No payload-bearing receipt survives.
    delete from auth.users where id = owner;
    -- Safe replay with the same still-valid JWT after a lost response.
    return jsonb_build_object('deleted', true, 'owner_id', owner);
end;
$$;
revoke all on function public.delete_spool_account(uuid) from public, anon, authenticated;
grant execute on function public.delete_spool_account(uuid) to authenticated;
commit;
