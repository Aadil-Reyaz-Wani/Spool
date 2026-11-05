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

create function pg_temp.spool_snapshot(weight double precision default 900) returns jsonb
language sql as $$ select jsonb_build_object('version',1,'uuid','10000000-0000-4000-8000-000000000001',
    'brand','Test','material','PLA','color_name','Black','color_hex',123,'total_weight',1000,'current_weight',weight,
    'temp_nozzle',200,'temp_bed',60,'note','Original','price',20,'timestamp',123,
    'dry_baseline_weight',null,'dry_baseline_tare_grams',null,'last_weighed_tare_grams',null,'last_dried_at',null); $$;
create function pg_temp.logs_snapshot(grams double precision default 100) returns jsonb
language sql as $$ select jsonb_build_array(jsonb_build_object('uuid','20000000-0000-4000-8000-000000000001','operation','upsert',
    'payload',jsonb_build_object('version',1,'uuid','20000000-0000-4000-8000-000000000001',
    'spool_uuid','10000000-0000-4000-8000-000000000001','title','Print','grams_used',grams,'timestamp',123,'is_failure',0,'price_per_print',2))); $$;
create function pg_temp.send(batch text, revision bigint, weight double precision default 900, logs jsonb default pg_temp.logs_snapshot(), deleted boolean default false) returns jsonb
language sql as $$ select public.upload_inventory_batch(auth.uid(), batch::uuid,
    '10000000-0000-4000-8000-000000000001', revision, pg_temp.spool_snapshot(weight), deleted, logs); $$;
insert into auth.users (id) values ('00000000-0000-4000-8000-0000000000a1'), ('00000000-0000-4000-8000-0000000000b1');
set local role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-0000000000a1', true);
select pg_temp.assert_true(pg_temp.send('30000000-0000-4000-8000-000000000001',0)->>'status' = 'applied', 'first batch applies');
select pg_temp.assert_true((select current_weight = 900 and revision = 1 from public.inventory_spools), 'weight and aggregate revision persisted');
select pg_temp.assert_true((select grams_used = 100 and not is_failure from public.inventory_usage_logs), 'history and SQLite boolean normalized');
select pg_temp.assert_true(pg_temp.send('30000000-0000-4000-8000-000000000001',0)->>'revision' = '1', 'lost-response retry returns original receipt');
select pg_temp.assert_true((select count(*) = 1 and max(revision) = 1 from public.inventory_usage_logs), 'retry does not duplicate history or increment revision');
select pg_temp.expect_error($q$select pg_temp.send('30000000-0000-4000-8000-000000000001',0,800)$q$, '22023', 'batch token cannot be reused with changed payload');
select pg_temp.assert_true(pg_temp.send('30000000-0000-4000-8000-000000000002',0,800)->>'status' = 'conflict', 'stale device preserves server');
select pg_temp.assert_true((pg_temp.send('30000000-0000-4000-8000-000000000002',0,800)->'server'->'spool'->>'current_weight')::double precision = 900, 'conflict carries remote snapshot');
select pg_temp.assert_true((select current_weight = 900 and revision = 1 from public.inventory_spools), 'conflict does not modify spool');
select pg_temp.assert_true(pg_temp.send('30000000-0000-4000-8000-000000000003',1,800,pg_temp.logs_snapshot(200))->>'status' = 'applied', 'next revision applies weight and edited log');
select pg_temp.assert_true((select current_weight = 800 and revision = 2 from public.inventory_spools), 'updated aggregate revision');
select pg_temp.assert_true((select grams_used = 200 from public.inventory_usage_logs), 'related history updated');
select pg_temp.assert_true(pg_temp.send('30000000-0000-4000-8000-000000000001',0)->>'revision' = '1', 'late retry returns old receipt after newer device change');
select pg_temp.assert_true((select current_weight = 800 and revision = 2 from public.inventory_spools), 'late retry cannot overwrite newer values');
select pg_temp.expect_error($q$select pg_temp.send('30000000-0000-4000-8000-000000000004',2,700,pg_temp.logs_snapshot(-1))$q$, '23514', 'invalid history aborts whole aggregate');
select pg_temp.assert_true((select current_weight = 800 and revision = 2 from public.inventory_spools), 'history failure rolls back already-written parent');
select pg_temp.assert_true((select grams_used = 200 from public.inventory_usage_logs), 'history failure preserves old log');
select pg_temp.expect_error($q$select pg_temp.send('30000000-0000-4000-8000-000000000007',2,700,pg_temp.logs_snapshot() || pg_temp.logs_snapshot())$q$, '22023', 'duplicate log UUIDs rejected');
select pg_temp.expect_error($q$select public.upload_inventory_batch(auth.uid(),'30000000-0000-4000-8000-000000000007','10000000-0000-4000-8000-000000000001',2,
    pg_temp.spool_snapshot() || '{"owner_id":"00000000-0000-4000-8000-0000000000b1"}',false,'[]')$q$, '22023', 'owner injection into snapshot rejected');
select pg_temp.expect_error($q$select pg_temp.send('30000000-0000-4000-8000-000000000007',2,700,
    jsonb_set(pg_temp.logs_snapshot(),'{0,payload,spool_uuid}','"10000000-0000-4000-8000-000000000002"'))$q$, '22023', 'cross-spool history injection rejected');
select pg_temp.assert_true(pg_temp.send('30000000-0000-4000-8000-000000000004',2,750,pg_temp.logs_snapshot(250))->>'status' = 'applied', 'failed batch left no receipt and can be corrected');
select pg_temp.assert_true(pg_temp.send('30000000-0000-4000-8000-000000000005',3,750,pg_temp.logs_snapshot(250),true)->>'status' = 'applied', 'parent deletion applies');
select pg_temp.assert_true((select deleted_at is not null from public.inventory_spools), 'parent marked deleted');
select pg_temp.assert_true((select deleted_at is not null from public.inventory_usage_logs), 'history marked deleted in same operation');
select pg_temp.assert_true((select count(*) = 2 from public.inventory_tombstones), 'deletion captures parent and history');
select pg_temp.assert_true((select (payload->>'current_weight')::double precision = 750 from public.inventory_tombstones where entity_type = 'spool'), 'snapshot includes latest unsynced values');
select pg_temp.assert_true(pg_temp.send('30000000-0000-4000-8000-000000000005',3,750,pg_temp.logs_snapshot(250),true)->>'status' = 'applied', 'delete retries idempotently');
select pg_temp.assert_true((select count(*) = 2 from public.inventory_tombstones), 'delete retry creates no duplicate tombstones');
select pg_temp.assert_true(pg_temp.send('30000000-0000-4000-8000-000000000006',5)->>'status' = 'conflict', 'deleted UUID cannot be resurrected even with matching revision');
select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-0000000000b1', true);
select pg_temp.assert_true(pg_temp.send('30000000-0000-4000-8000-000000000001',0)->>'status' = 'applied', 'same IDs in different account are independent');
select pg_temp.assert_true((select count(*) = 1 and bool_and(deleted_at is null) from public.inventory_spools), 'B isolated from A deletion');
select pg_temp.expect_error($q$select public.upload_inventory_batch('00000000-0000-4000-8000-0000000000a1','30000000-0000-4000-8000-000000000008','10000000-0000-4000-8000-000000000001',0,pg_temp.spool_snapshot(),false,'[]')$q$, '42501', 'wrong token cannot upload another owner queue');
select pg_temp.assert_true(pg_temp.send('30000000-0000-4000-8000-000000000009',1,1000,
    jsonb_set(pg_temp.logs_snapshot(),'{0,operation}','"delete"'))->>'status' = 'applied', 'individual print deletion restores weight atomically');
select pg_temp.assert_true((select current_weight = 1000 and revision = 2 and deleted_at is null from public.inventory_spools), 'individual deletion keeps parent live');
select pg_temp.assert_true((select count(*) = 1 from public.inventory_tombstones), 'individual print snapshot captured');
select pg_temp.assert_true(pg_temp.send('30000000-0000-4000-8000-000000000010',2)->>'status' = 'conflict', 'deleted history cannot be resurrected under a live parent');
select pg_temp.assert_true((select current_weight = 1000 and revision = 2 from public.inventory_spools), 'history conflict preserves parent weight too');
select pg_temp.expect_error($q$select public.upload_inventory_batch(auth.uid(),'30000000-0000-4000-8000-000000000011',
    '10000000-0000-4000-8000-000000000002',0,
    jsonb_set(pg_temp.spool_snapshot(),'{uuid}','"10000000-0000-4000-8000-000000000002"'),false,
    replace(pg_temp.logs_snapshot()::text,'10000000-0000-4000-8000-000000000001','10000000-0000-4000-8000-000000000002')::jsonb)$q$,
    '23514', 'cannot reuse a history UUID under another spool');
select pg_temp.assert_true(public.upload_inventory_batch(auth.uid(),'30000000-0000-4000-8000-000000000012',
    '10000000-0000-4000-8000-000000000002',0,
    jsonb_set(pg_temp.spool_snapshot(),'{uuid}','"10000000-0000-4000-8000-000000000002"'),true,
    replace(replace(pg_temp.logs_snapshot()::text,'10000000-0000-4000-8000-000000000001','10000000-0000-4000-8000-000000000002'),
        '20000000-0000-4000-8000-000000000001','20000000-0000-4000-8000-000000000002')::jsonb)->>'status' = 'applied',
    'created and deleted offline inventory still gets cloud recovery snapshots');
select pg_temp.assert_true((select count(*) = 3 from public.inventory_tombstones), 'new deleted aggregate captures spool and print');
select set_config('request.jwt.claim.sub', '', true);
select set_config('request.jwt.claims', '{}', true);
select pg_temp.expect_error($q$select pg_temp.send('30000000-0000-4000-8000-000000000008',0)$q$, '42501', 'no subject cannot upload');
set local role anon;
select pg_temp.expect_error($q$select pg_temp.send('30000000-0000-4000-8000-000000000008',0)$q$, '42501', 'anonymous cannot execute upload RPC');
reset role;
delete from auth.users where id = '00000000-0000-4000-8000-0000000000a1';
select pg_temp.assert_true((select count(*) = 0 from spool_private.inventory_upload_receipts where owner_id = '00000000-0000-4000-8000-0000000000a1'), 'account deletion erases receipts');
select pg_temp.assert_true((select count(*) = 2 from public.inventory_spools where owner_id = '00000000-0000-4000-8000-0000000000b1'), 'account deletion preserves B');
rollback;
select 'PASS: atomic upload, retries, revisions, conflicts, deletions and ownership; all fixtures rolled back' as result;
