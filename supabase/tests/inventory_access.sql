-- Run as postgres against a Supabase database after the inventory migration.
-- All fixtures and changes roll back, including the temporary auth users.
begin;
set local lock_timeout = '5s';
set local statement_timeout = '30s';

create function pg_temp.assert_true(ok boolean, label text) returns void
language plpgsql as $$
begin
    if ok is distinct from true then raise exception 'FAIL: %', label; end if;
end;
$$;
create function pg_temp.expect_error(statement text, expected_state text, label text) returns void
language plpgsql as $$
begin
    begin
        execute statement;
    exception when others then
        if sqlstate = expected_state then return; end if;
        raise exception 'FAIL: % (expected %, got %: %)', label, expected_state, sqlstate, sqlerrm;
    end;
    raise exception 'FAIL: % (statement unexpectedly succeeded)', label;
end;
$$;


insert into auth.users (id) values ('00000000-0000-4000-8000-0000000000a1'), ('00000000-0000-4000-8000-0000000000b1');
insert into public.inventory_spools (owner_id, uuid, brand) values
    ('00000000-0000-4000-8000-0000000000a1', '10000000-0000-4000-8000-000000000001', 'Private A'),
    ('00000000-0000-4000-8000-0000000000b1', '10000000-0000-4000-8000-000000000001', 'Private B');
insert into public.inventory_usage_logs (owner_id, uuid, spool_uuid, grams_used) values
    ('00000000-0000-4000-8000-0000000000a1', '20000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', 1);
update public.inventory_spools set deleted_at = now() where owner_id = '00000000-0000-4000-8000-0000000000a1';
select pg_temp.assert_true((select count(*) = 3 from pg_class c join pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'public' and c.relname in ('inventory_spools','inventory_usage_logs','inventory_tombstones') and c.relrowsecurity), 'RLS enabled');
set local role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-0000000000b1', true);
select pg_temp.assert_true((select count(*) = 1 and bool_and(brand = 'Private B') from public.inventory_spools), 'B only reads its spool');
select pg_temp.assert_true((select count(*) = 0 from public.inventory_usage_logs), 'B cannot read A history');
select pg_temp.assert_true((select count(*) = 0 from public.inventory_tombstones), 'B cannot read A recovery snapshots');
select pg_temp.expect_error('select * from spool_private.inventory_upload_receipts', '42501', 'receipts are private');
select pg_temp.expect_error($q$insert into public.inventory_spools (uuid) values ('10000000-0000-4000-8000-000000000002')$q$, '42501', 'direct insert cannot bypass protocol');
select pg_temp.expect_error($q$update public.inventory_spools set brand = 'Overwrite'$q$, '42501', 'direct edit cannot bypass revision check');
select pg_temp.expect_error($q$update public.inventory_spools set revision = 999$q$, '42501', 'cannot forge revision');
select pg_temp.expect_error($q$update public.inventory_spools set owner_id = auth.uid()$q$, '42501', 'cannot transfer ownership');
select pg_temp.expect_error($q$insert into public.inventory_usage_logs (uuid, spool_uuid, grams_used) values ('20000000-0000-4000-8000-000000000002','10000000-0000-4000-8000-000000000001',1)$q$, '42501', 'direct history insert denied');
select pg_temp.expect_error($q$update public.inventory_usage_logs set grams_used = 5$q$, '42501', 'direct history edit denied');
select pg_temp.expect_error('delete from public.inventory_spools', '42501', 'no hard-delete spools');
select pg_temp.expect_error('delete from public.inventory_usage_logs', '42501', 'no hard-delete history');
select pg_temp.expect_error('insert into public.inventory_tombstones default values', '42501', 'no fabricated recovery snapshots');
select pg_temp.expect_error($q$update public.inventory_tombstones set payload = '{}'$q$, '42501', 'snapshots immutable');
select pg_temp.expect_error('delete from public.inventory_tombstones', '42501', 'snapshots retained');
select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-0000000000a1', true);
select pg_temp.assert_true((select count(*) = 1 and bool_and(brand = 'Private A') from public.inventory_spools), 'A only reads its spool');
select pg_temp.assert_true((select count(*) = 1 from public.inventory_usage_logs), 'A reads its history');
select pg_temp.assert_true((select count(*) = 2 from public.inventory_tombstones), 'A reads its recovery snapshots');
select pg_temp.assert_true((select bool_and(recovery_expires_at = deleted_at + interval '30 days') from public.inventory_tombstones), '30-day recovery window');
select set_config('request.jwt.claim.sub', '', true);
select set_config('request.jwt.claims', '{}', true);
select pg_temp.assert_true((select count(*) = 0 from public.inventory_spools), 'missing subject sees no inventory');
select pg_temp.assert_true((select count(*) = 0 from public.inventory_tombstones), 'missing subject sees no snapshots');
set local role anon;
select pg_temp.expect_error('select * from public.inventory_spools', '42501', 'anonymous spool reads denied');
select pg_temp.expect_error('select * from public.inventory_usage_logs', '42501', 'anonymous history reads denied');
select pg_temp.expect_error('select * from public.inventory_tombstones', '42501', 'anonymous snapshot reads denied');
rollback;
select 'PASS: inventory access; all fixtures rolled back' as result;
