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

-- Fixed fixture identities deliberately fail on collision rather than touching an existing user.
insert into auth.users (id) values ('00000000-0000-4000-8000-0000000000a1'), ('00000000-0000-4000-8000-0000000000b1');
select pg_temp.assert_true((select count(*) = 3 from pg_class c join pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'public' and c.relname in ('inventory_spools', 'inventory_usage_logs', 'inventory_tombstones') and c.relrowsecurity), 'RLS enabled on all three tables');
select pg_temp.assert_true(not has_function_privilege('authenticated', 'spool_private.capture_inventory_deletion()', 'EXECUTE'), 'privileged deletion trigger is not callable');
select pg_temp.assert_true(not has_schema_privilege('authenticated', 'spool_private', 'USAGE'), 'private schema is not accessible');

set local role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-0000000000a1', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-4000-8000-0000000000a1","role":"authenticated"}', true);
insert into public.inventory_spools (uuid, brand, total_weight, current_weight, note, price)
    values ('10000000-0000-4000-8000-000000000001', 'Account A', 1000, 900, 'Original note', 20);
insert into public.inventory_usage_logs (uuid, spool_uuid, title, grams_used, price_per_print)
    values ('20000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', 'Print A', 100, 2);
select pg_temp.assert_true((select owner_id = auth.uid() and revision = 1 and created_at <= updated_at from public.inventory_spools
    where uuid = '10000000-0000-4000-8000-000000000001'), 'default ownership and server metadata');

select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-0000000000b1', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-4000-8000-0000000000b1","role":"authenticated"}', true);
select pg_temp.assert_true((select count(*) = 0 from public.inventory_spools), 'B cannot read A spools');
select pg_temp.assert_true((select count(*) = 0 from public.inventory_usage_logs), 'B cannot read A history');
-- A UUID collision across accounts must not leak or overwrite the other account's inventory.
insert into public.inventory_spools (uuid, brand) values
    ('10000000-0000-4000-8000-000000000001', 'Account B same UUID'),
    ('10000000-0000-4000-8000-000000000002', 'Account B only');
insert into public.inventory_usage_logs (uuid, spool_uuid, title, grams_used)
    values ('20000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', 'Print B same UUID', 1);
select pg_temp.assert_true((select count(*) = 2 from public.inventory_spools), 'B sees only its two spools');

select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-0000000000a1', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-4000-8000-0000000000a1","role":"authenticated"}', true);
select pg_temp.assert_true((select count(*) = 1 from public.inventory_spools), 'A cannot read B spools');
select pg_temp.assert_true((select count(*) = 1 from public.inventory_usage_logs), 'A cannot read B history');
select pg_temp.expect_error($q$insert into public.inventory_spools (owner_id, uuid) values
    ('00000000-0000-4000-8000-0000000000b1', '10000000-0000-4000-8000-000000000003')$q$, '42501', 'cannot forge spool owner');
select pg_temp.expect_error($q$insert into public.inventory_usage_logs (owner_id, uuid, spool_uuid, grams_used, deleted_at) values
    ('00000000-0000-4000-8000-0000000000b1', '20000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000002', 1, now())$q$, '42501', 'cannot forge history owner');
select pg_temp.expect_error($q$insert into public.inventory_usage_logs (uuid, spool_uuid, grams_used) values
    ('20000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000002', 1)$q$, '23503', 'history cannot reference another account parent');
with changed as (update public.inventory_spools set brand = 'Intrusion' where uuid = '10000000-0000-4000-8000-000000000002' returning uuid)
    select pg_temp.assert_true((select count(*) = 0 from changed), 'cross-account update changes zero rows');
select pg_temp.expect_error($q$update public.inventory_spools set owner_id = '00000000-0000-4000-8000-0000000000b1'$q$, '42501', 'cannot transfer spool ownership');
select pg_temp.expect_error($q$update public.inventory_spools set uuid = '10000000-0000-4000-8000-000000000004'$q$, '42501', 'cannot replace stable UUID');
select pg_temp.expect_error($q$update public.inventory_usage_logs set spool_uuid = '10000000-0000-4000-8000-000000000002'$q$, '42501', 'cannot move history parent');
select pg_temp.expect_error($q$update public.inventory_spools set revision = 999$q$, '42501', 'cannot forge revision');
select pg_temp.expect_error($q$update public.inventory_spools set updated_at = '2000-01-01'$q$, '42501', 'cannot forge update timestamp');
select pg_temp.expect_error($q$insert into public.inventory_spools (uuid, revision) values ('10000000-0000-4000-8000-000000000004', 999)$q$, '42501', 'cannot insert forged server metadata');
select pg_temp.expect_error($q$update public.inventory_spools set current_weight = -1$q$, '23514', 'reject negative weight');
select pg_temp.expect_error($q$update public.inventory_spools set price = 'NaN'$q$, '23514', 'reject NaN price');
select pg_temp.expect_error($q$update public.inventory_spools set total_weight = 'Infinity'$q$, '23514', 'reject infinite weight');
select pg_temp.expect_error($q$delete from public.inventory_spools$q$, '42501', 'no hard deletion of spools');
select pg_temp.expect_error($q$delete from public.inventory_usage_logs$q$, '42501', 'no hard deletion of history');
select pg_temp.expect_error($q$insert into public.inventory_tombstones default values$q$, '42501', 'no client-created tombstones');
select pg_temp.expect_error($q$update public.inventory_tombstones set payload = '{}'$q$, '42501', 'no client-edited recovery snapshots');
select pg_temp.expect_error($q$delete from public.inventory_tombstones$q$, '42501', 'no client-erased recovery snapshots');

update public.inventory_spools set note = 'Edited note' where uuid = '10000000-0000-4000-8000-000000000001';
select pg_temp.assert_true((select revision = 2 and note = 'Edited note' and updated_at >= created_at from public.inventory_spools), 'normal edit increments server revision');
update public.inventory_usage_logs set deleted_at = '2000-01-01' where uuid = '20000000-0000-4000-8000-000000000001';
select pg_temp.assert_true((select deleted_at > '2020-01-01' and revision = 2 from public.inventory_usage_logs), 'deletion timestamp is server-owned');
select pg_temp.assert_true((select count(*) = 1 from public.inventory_tombstones), 'individual history deletion captures one snapshot');
select pg_temp.assert_true((select entity_type = 'usage_log' and payload->>'title' = 'Print A'
    and payload->>'spool_uuid' = '10000000-0000-4000-8000-000000000001'
    and recovery_expires_at = deleted_at + interval '30 days' from public.inventory_tombstones), 'history recovery snapshot and 30-day window');
select pg_temp.expect_error($q$update public.inventory_usage_logs set deleted_at = null$q$, '23514', 'stale update cannot resurrect history');
insert into public.inventory_usage_logs (uuid, spool_uuid, title, grams_used)
    values ('20000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000001', 'Second print A', 1);
update public.inventory_spools set deleted_at = now() where uuid = '10000000-0000-4000-8000-000000000001';
select pg_temp.assert_true((select count(*) = 2 and bool_and(deleted_at is not null) from public.inventory_usage_logs), 'parent soft deletion marks live history atomically');
select pg_temp.assert_true((select count(*) = 3 from public.inventory_tombstones), 'parent and remaining history captured without duplicate snapshot');
select pg_temp.assert_true((select payload->>'note' = 'Edited note' and payload->>'current_weight' = '900'
    from public.inventory_tombstones where entity_type = 'spool'), 'spool snapshot preserves pre-deletion values');
select pg_temp.expect_error($q$update public.inventory_spools set deleted_at = null$q$, '23514', 'stale update cannot resurrect spool');
select pg_temp.expect_error($q$update public.inventory_spools set note = 'Stale edit'$q$, '23514', 'stale edit cannot change a deleted spool');
select pg_temp.expect_error($q$insert into public.inventory_usage_logs (uuid, spool_uuid, grams_used) values
    ('20000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000001', 1)$q$, '23503', 'cannot add live history to deleted parent');

select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-0000000000b1', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-4000-8000-0000000000b1","role":"authenticated"}', true);
select pg_temp.assert_true((select count(*) = 0 from public.inventory_tombstones), 'B cannot read A recovery snapshots');
select pg_temp.assert_true((select count(*) = 2 and bool_and(deleted_at is null) from public.inventory_spools), 'A deletion does not affect B spools');
select pg_temp.assert_true((select count(*) = 1 and bool_and(deleted_at is null) from public.inventory_usage_logs), 'A deletion does not affect B history');

-- An expired/missing subject must not grant authenticated-role access by itself.
select set_config('request.jwt.claim.sub', '', true);
select set_config('request.jwt.claims', '{}', true);
select pg_temp.assert_true((select count(*) = 0 from public.inventory_spools), 'missing subject sees no spools');
select pg_temp.assert_true((select count(*) = 0 from public.inventory_usage_logs), 'missing subject sees no history');
select pg_temp.assert_true((select count(*) = 0 from public.inventory_tombstones), 'missing subject sees no recovery snapshots');
select pg_temp.expect_error($q$insert into public.inventory_spools (owner_id, uuid) values
    ('00000000-0000-4000-8000-0000000000a1', '10000000-0000-4000-8000-000000000003')$q$, '42501', 'missing subject cannot forge an owner');

set local role anon;
select pg_temp.expect_error('select * from public.inventory_spools', '42501', 'anonymous cannot read spools');
select pg_temp.expect_error('select * from public.inventory_usage_logs', '42501', 'anonymous cannot read history');
select pg_temp.expect_error('select * from public.inventory_tombstones', '42501', 'anonymous cannot read recovery snapshots');
select pg_temp.expect_error($q$insert into public.inventory_spools (uuid) values ('10000000-0000-4000-8000-000000000003')$q$, '42501', 'anonymous cannot write inventory');

reset role;
-- Account deletion is an administrator/auth operation and must erase only that account's cloud data.
delete from auth.users where id = '00000000-0000-4000-8000-0000000000a1';
select pg_temp.assert_true((select count(*) = 0 from public.inventory_spools where owner_id = '00000000-0000-4000-8000-0000000000a1'), 'account deletion removes owned spools');
select pg_temp.assert_true((select count(*) = 0 from public.inventory_usage_logs where owner_id = '00000000-0000-4000-8000-0000000000a1'), 'account deletion removes owned history');
select pg_temp.assert_true((select count(*) = 0 from public.inventory_tombstones where owner_id = '00000000-0000-4000-8000-0000000000a1'), 'account deletion removes owned snapshots');
select pg_temp.assert_true((select count(*) = 2 from public.inventory_spools where owner_id = '00000000-0000-4000-8000-0000000000b1'), 'account deletion preserves the other account');

rollback;
select 'PASS: inventory access, identity, validation, revisions, deletion snapshots and account cleanup; all test changes rolled back' as result;
