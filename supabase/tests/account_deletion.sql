-- Run as postgres after the account deletion migration. Only disposable fixtures are touched.
-- Every fixture and deletion is rolled back. Existing account UUIDs are never selected.
begin;
set local lock_timeout = '5s';
set local statement_timeout = '30s';
create function pg_temp.assert_true(ok boolean, label text) returns void
language plpgsql as $$
begin if ok is distinct from true then raise exception 'FAIL: %', label; end if; end;
$$;
create function pg_temp.expect_error(statement text, expected_state text, label text) returns void
language plpgsql as $$
begin
    begin execute statement;
    exception when others then
        if sqlstate = expected_state then return; end if;
        raise exception 'FAIL: % (expected %, got %)', label, expected_state, sqlstate;
    end;
    raise exception 'FAIL: % (statement unexpectedly succeeded)', label;
end;
$$;
-- A collision aborts the transaction before any deletion; no existing account is reused.
insert into auth.users(id, email) values
 ('dd290000-0000-4000-8000-0000000000a1', 'deletion-fixture-a@example.invalid'),
 ('dd290000-0000-4000-8000-0000000000b2', 'deletion-fixture-b@example.invalid');
insert into public.inventory_spools(owner_id, uuid, brand) values
 ('dd290000-0000-4000-8000-0000000000a1','dd290000-0000-4000-8000-000000000011','Deletion A'),
 ('dd290000-0000-4000-8000-0000000000b2','dd290000-0000-4000-8000-000000000022','Preserved B');
insert into public.inventory_usage_logs(owner_id,uuid,spool_uuid,title,grams_used) values
 ('dd290000-0000-4000-8000-0000000000a1','dd290000-0000-4000-8000-000000000033','dd290000-0000-4000-8000-000000000011','A Print',10),
 ('dd290000-0000-4000-8000-0000000000b2','dd290000-0000-4000-8000-000000000044','dd290000-0000-4000-8000-000000000022','B Print',20);
update public.inventory_spools set deleted_at=clock_timestamp()
 where owner_id='dd290000-0000-4000-8000-0000000000a1';
insert into spool_private.inventory_upload_receipts values
 ('dd290000-0000-4000-8000-0000000000a1','dd290000-0000-4000-8000-000000000055','\x00','{}'),
 ('dd290000-0000-4000-8000-0000000000b2','dd290000-0000-4000-8000-000000000066','\x01','{}');
select pg_temp.assert_true(not has_function_privilege('anon','public.delete_spool_account(uuid)','EXECUTE'), 'anonymous execution denied');
select pg_temp.assert_true(has_function_privilege('authenticated','public.delete_spool_account(uuid)','EXECUTE'), 'authenticated self-deletion available');
set local role anon;
select pg_temp.expect_error($q$select public.delete_spool_account('dd290000-0000-4000-8000-0000000000a1')$q$,'42501','anonymous cannot delete');
set local role authenticated;
select set_config('request.jwt.claims','{"sub":"dd290000-0000-4000-8000-0000000000a1","role":"authenticated"}',true);
select set_config('request.jwt.claim.sub','dd290000-0000-4000-8000-0000000000a1',true);
select pg_temp.expect_error($q$select public.delete_spool_account('dd290000-0000-4000-8000-0000000000b2')$q$,'42501','A cannot delete B');
select pg_temp.expect_error('select public.delete_spool_account(null)','42501','null owner rejected');
select pg_temp.assert_true((public.delete_spool_account('dd290000-0000-4000-8000-0000000000a1')->>'deleted')::boolean,'self deletion confirmed');
select pg_temp.assert_true(public.delete_spool_account('dd290000-0000-4000-8000-0000000000a1')->>'owner_id'='dd290000-0000-4000-8000-0000000000a1','lost-response replay safe');
select set_config('request.jwt.claims','{}',true);
select set_config('request.jwt.claim.sub','',true);
select pg_temp.expect_error($q$select public.delete_spool_account('dd290000-0000-4000-8000-0000000000b2')$q$,'42501','missing subject cannot delete');
reset role;
select pg_temp.assert_true(not exists(select 1 from auth.users where id='dd290000-0000-4000-8000-0000000000a1'),'auth user deleted');
select pg_temp.assert_true(not exists(select 1 from public.inventory_spools where owner_id='dd290000-0000-4000-8000-0000000000a1'),'spools deleted');
select pg_temp.assert_true(not exists(select 1 from public.inventory_usage_logs where owner_id='dd290000-0000-4000-8000-0000000000a1'),'history deleted');
select pg_temp.assert_true(not exists(select 1 from public.inventory_tombstones where owner_id='dd290000-0000-4000-8000-0000000000a1'),'tombstones deleted');
select pg_temp.assert_true(not exists(select 1 from spool_private.inventory_upload_receipts where owner_id='dd290000-0000-4000-8000-0000000000a1'),'receipts deleted');
select pg_temp.assert_true(not exists(select 1 from spool_private.inventory_changes where owner_id='dd290000-0000-4000-8000-0000000000a1'),'changes deleted');
select pg_temp.assert_true(not exists(select 1 from spool_private.inventory_change_counters where owner_id='dd290000-0000-4000-8000-0000000000a1'),'counters deleted');
select pg_temp.assert_true(exists(select 1 from auth.users where id='dd290000-0000-4000-8000-0000000000b2'),'B auth preserved');
select pg_temp.assert_true(exists(select 1 from public.inventory_spools where owner_id='dd290000-0000-4000-8000-0000000000b2' and brand='Preserved B'),'B inventory preserved');
select pg_temp.assert_true(exists(select 1 from public.inventory_usage_logs where owner_id='dd290000-0000-4000-8000-0000000000b2' and title='B Print'),'B history preserved');
select pg_temp.assert_true(exists(select 1 from spool_private.inventory_upload_receipts where owner_id='dd290000-0000-4000-8000-0000000000b2'),'B receipt preserved');
rollback;
select 'PASS: self-deletion, owner isolation, cascade and replay; all fixtures rolled back' as result;
