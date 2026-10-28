-- Run after all inventory migrations as postgres. All fixtures are rolled back.
begin;
set local lock_timeout = '5s';
set local statement_timeout = '30s';
create function pg_temp.assert_true(ok boolean, label text) returns void language plpgsql as $$
begin if ok is distinct from true then raise exception 'FAIL: %', label; end if; end; $$;
create function pg_temp.expect_error(statement text, expected_state text, label text) returns void language plpgsql as $$
begin
    begin execute statement;
    exception when others then
        if sqlstate = expected_state then return; end if;
        raise exception 'FAIL: % (expected %, got %: %)', label, expected_state, sqlstate, sqlerrm;
    end;
    raise exception 'FAIL: % unexpectedly succeeded', label;
end; $$;
insert into auth.users (id) values ('00000000-0000-4000-8000-0000000000c8'), ('00000000-0000-4000-8000-0000000000d8');

-- Each parent/history change records its aggregate using a transactional counter.
insert into public.inventory_spools (owner_id,uuid,brand,current_weight,total_weight) values
 ('00000000-0000-4000-8000-0000000000c8','10000000-0000-4000-8000-0000000000c8','PullQA',900,1000),
 ('00000000-0000-4000-8000-0000000000d8','10000000-0000-4000-8000-0000000000d8','OtherQA',500,1000);
insert into public.inventory_usage_logs (owner_id,uuid,spool_uuid,title,grams_used) values
 ('00000000-0000-4000-8000-0000000000c8','20000000-0000-4000-8000-0000000000c8','10000000-0000-4000-8000-0000000000c8','History',100);
create temp table test_positions as select value as original from spool_private.inventory_change_counters where owner_id='00000000-0000-4000-8000-0000000000c8';
select pg_temp.assert_true((select count(*)=1 from spool_private.inventory_changes where owner_id='00000000-0000-4000-8000-0000000000c8'), 'feed coalesces one complete aggregate');
select pg_temp.assert_true((select original=2 from test_positions), 'history also advances account cursor');
savepoint before_failed_write;
update public.inventory_spools set note='Rolled back' where owner_id='00000000-0000-4000-8000-0000000000c8';
rollback to before_failed_write;
select pg_temp.assert_true((select value=(select original from test_positions) from spool_private.inventory_change_counters where owner_id='00000000-0000-4000-8000-0000000000c8'), 'rolled-back writes roll back cursor counter');
select pg_temp.assert_true(not has_function_privilege('anon','public.pull_inventory(uuid,bigint)','EXECUTE'), 'anonymous RPC denied');
select pg_temp.assert_true(not has_table_privilege('authenticated','spool_private.inventory_changes','SELECT'), 'private feed cannot be read directly');
select pg_temp.assert_true(not has_table_privilege('authenticated','spool_private.inventory_change_counters','UPDATE'), 'clients cannot forge counters');
set local role authenticated;
select set_config('request.jwt.claims','{"sub":"00000000-0000-4000-8000-0000000000c8","role":"authenticated"}',true);
select pg_temp.expect_error($q$select public.pull_inventory('00000000-0000-4000-8000-0000000000d8',0)$q$,'42501','cross-account download rejected');
select pg_temp.expect_error($q$select public.pull_inventory(auth.uid(),-1)$q$,'22023','negative cursor rejected');
select pg_temp.expect_error($q$select public.pull_inventory(auth.uid(),null)$q$,'22023','null cursor rejected');
select pg_temp.assert_true(public.pull_inventory(auth.uid(),0)->>'owner_id'=auth.uid()::text,'response bound to authenticated owner');
select pg_temp.assert_true(public.pull_inventory(auth.uid(),0)->'snapshot'->'spool'->>'brand'='PullQA','initial inventory available');
select pg_temp.assert_true(jsonb_array_length(public.pull_inventory(auth.uid(),0)->'snapshot'->'logs')=1,'complete history included');
select pg_temp.assert_true(public.pull_inventory(auth.uid(),0)->'snapshot'->'logs'->0->>'title'='History','correct history fields');
select pg_temp.assert_true((public.pull_inventory(auth.uid(),0)->>'next_cursor')::bigint=2,'page advances to final committed aggregate position');
select pg_temp.assert_true(public.pull_inventory(auth.uid(),2)->'snapshot'='null'::jsonb,'empty page has no snapshot');
select pg_temp.assert_true(public.pull_inventory(auth.uid(),2)->>'next_cursor'='2','empty page preserves cursor');
select pg_temp.assert_true(public.pull_inventory(auth.uid(),2)->>'has_more'='false','empty page finishes');
reset role;
insert into public.inventory_spools (owner_id,uuid,brand) values ('00000000-0000-4000-8000-0000000000c8','10000000-0000-4000-8000-0000000000e8','Second');
update public.inventory_spools set current_weight=800 where owner_id='00000000-0000-4000-8000-0000000000c8' and uuid='10000000-0000-4000-8000-0000000000c8';
set local role authenticated;
select pg_temp.assert_true(public.pull_inventory(auth.uid(),2)->'snapshot'->'spool'->>'brand'='Second','ordered page after earlier aggregate moves forward');
select pg_temp.assert_true(public.pull_inventory(auth.uid(),2)->>'has_more'='true','more aggregates advertised');
select pg_temp.assert_true(public.pull_inventory(auth.uid(),3)->'snapshot'->'spool'->>'current_weight'='800','next page has latest aggregate');
reset role;
update public.inventory_usage_logs set deleted_at=clock_timestamp() where owner_id='00000000-0000-4000-8000-0000000000c8';
update public.inventory_spools set deleted_at=clock_timestamp() where owner_id='00000000-0000-4000-8000-0000000000c8' and uuid='10000000-0000-4000-8000-0000000000c8';
set local role authenticated;
select pg_temp.assert_true(public.pull_inventory(auth.uid(),4)->'snapshot'->'spool'->>'deleted_at' is not null,'parent tombstone delivered');
select pg_temp.assert_true(public.pull_inventory(auth.uid(),4)->'snapshot'->'logs'->0->>'deleted_at' is not null,'history tombstone delivered');
select set_config('request.jwt.claims','{"sub":"00000000-0000-4000-8000-0000000000d8","role":"authenticated"}',true);
select pg_temp.assert_true(public.pull_inventory(auth.uid(),0)->'snapshot'->'spool'->>'brand'='OtherQA','second account gets only own aggregate');
select pg_temp.assert_true(public.pull_inventory(auth.uid(),0)->>'has_more'='false','second account has no first-account pages');
select set_config('request.jwt.claims','{}',true);
select pg_temp.expect_error($q$select public.pull_inventory('00000000-0000-4000-8000-0000000000c8',0)$q$,'42501','missing subject rejected');
reset role;
delete from auth.users where id in ('00000000-0000-4000-8000-0000000000c8','00000000-0000-4000-8000-0000000000d8');
select pg_temp.assert_true(not exists (select 1 from spool_private.inventory_changes where owner_id in ('00000000-0000-4000-8000-0000000000c8','00000000-0000-4000-8000-0000000000d8')), 'account deletion removes feed');
select pg_temp.assert_true(not exists (select 1 from spool_private.inventory_change_counters where owner_id in ('00000000-0000-4000-8000-0000000000c8','00000000-0000-4000-8000-0000000000d8')), 'account deletion removes counters');
select 'PASS: inventory pull assertions' as result;
rollback;
