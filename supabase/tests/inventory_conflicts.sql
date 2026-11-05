-- Run as postgres after the conflict-review migration. Every fixture is rolled back.
begin;
set local lock_timeout = '5s';
set local statement_timeout = '30s';
create function pg_temp.assert_true(ok boolean,label text) returns void language plpgsql as $$
begin if ok is distinct from true then raise exception 'FAIL: %',label; end if; end; $$;
create function pg_temp.expect_error(statement text,expected_state text,label text) returns void language plpgsql as $$
begin
    begin execute statement;
    exception when others then
        if sqlstate=expected_state then return; end if;
        raise exception 'FAIL: % (expected %, got %: %)',label,expected_state,sqlstate,sqlerrm;
    end;
    raise exception 'FAIL: % unexpectedly succeeded',label;
end; $$;
create function pg_temp.spool_payload(id uuid,weight double precision,brand text default 'Original') returns jsonb language sql as $$
 select jsonb_build_object('version',1,'uuid',id,'brand',brand,'material','PLA','color_name','Black','color_hex',123,
 'total_weight',1000,'current_weight',weight,'temp_nozzle',200,'temp_bed',60,'note','Review fixture','price',20,'timestamp',123,
 'dry_baseline_weight',null,'dry_baseline_tare_grams',null,'last_weighed_tare_grams',null,'last_dried_at',null); $$;
create function pg_temp.log_item(id uuid,parent uuid,title text,grams double precision,operation text default 'upsert') returns jsonb language sql as $$
 select jsonb_build_object('uuid',id,'operation',operation,'payload',jsonb_build_object('version',1,'uuid',id,'spool_uuid',parent,
 'title',title,'grams_used',grams,'timestamp',123,'is_failure',false,'price_per_print',grams*.02)); $$;
create function pg_temp.send(batch uuid,base bigint,weight double precision,logs jsonb default '[]',deleted boolean default false,
 parent uuid default '10000000-0000-4000-8000-000000000001',brand text default 'Original') returns jsonb language sql as $$
 select public.upload_inventory_batch(auth.uid(),batch,parent,base,pg_temp.spool_payload(parent,weight,brand),deleted,logs); $$;
insert into auth.users(id) values('00000000-0000-4000-8000-0000000000e9'),('00000000-0000-4000-8000-0000000000f9');
select pg_temp.assert_true(not has_function_privilege('anon','public.get_inventory_snapshot(uuid,uuid)','EXECUTE'),'anonymous snapshot execution denied');
select pg_temp.assert_true(not has_function_privilege('authenticated','spool_private.inventory_snapshot(uuid,uuid)','EXECUTE'),'private snapshot helper remains inaccessible');
set local role authenticated;
select set_config('request.jwt.claims','{"sub":"00000000-0000-4000-8000-0000000000e9","role":"authenticated"}',true);
select pg_temp.assert_true(pg_temp.send('30000000-0000-4000-8000-000000000001',0,900,
 jsonb_build_array(pg_temp.log_item('20000000-0000-4000-8000-000000000001','10000000-0000-4000-8000-000000000001','Original print',100)))->>'status'='applied','seed first account');
select set_config('request.jwt.claims','{"sub":"00000000-0000-4000-8000-0000000000f9","role":"authenticated"}',true);
select pg_temp.assert_true(pg_temp.send('30000000-0000-4000-8000-000000000001',0,500,'[]',false,'10000000-0000-4000-8000-000000000001','Other account')->>'status'='applied','same spool UUID isolated in second account');
reset role;
create temp table review_before as select
 (select value from spool_private.inventory_change_counters where owner_id='00000000-0000-4000-8000-0000000000e9') as position,
 (select count(*) from spool_private.inventory_upload_receipts) as receipts;
set local role authenticated;
select set_config('request.jwt.claims','{"sub":"00000000-0000-4000-8000-0000000000e9","role":"authenticated"}',true);
select pg_temp.assert_true(public.get_inventory_snapshot(auth.uid(),'10000000-0000-4000-8000-000000000001')->'spool'->>'current_weight'='900','review gets current account spool');
select pg_temp.assert_true(jsonb_array_length(public.get_inventory_snapshot(auth.uid(),'10000000-0000-4000-8000-000000000001')->'logs')=1,'review gets full history');
select pg_temp.expect_error($q$select public.get_inventory_snapshot('00000000-0000-4000-8000-0000000000f9','10000000-0000-4000-8000-000000000001')$q$,'42501','foreign expected owner rejected');
select pg_temp.expect_error($q$select public.get_inventory_snapshot(auth.uid(),null)$q$,'22023','null spool rejected');
select pg_temp.assert_true(public.get_inventory_snapshot(auth.uid(),'10000000-0000-4000-8000-000000000099')->'spool'='null'::jsonb,'missing spool has no fabricated version');
reset role;
select pg_temp.assert_true((select position=(select value from spool_private.inventory_change_counters where owner_id='00000000-0000-4000-8000-0000000000e9') and receipts=(select count(*) from spool_private.inventory_upload_receipts) from review_before),'review does not write inventory/feed/receipts');
set local role authenticated;
-- Another device adds a print; the reviewed cloud parent is revision 2.
select pg_temp.assert_true(pg_temp.send('30000000-0000-4000-8000-000000000002',1,800,
 jsonb_build_array(pg_temp.log_item('20000000-0000-4000-8000-000000000003','10000000-0000-4000-8000-000000000001','Cloud print',50)))->>'revision'='2','remote edit before resolution');
-- Choosing local: preserve the unchanged common print, add the local print, delete cloud-only history.
select pg_temp.assert_true(pg_temp.send('30000000-0000-4000-8000-000000000003',2,890,jsonb_build_array(
 pg_temp.log_item('20000000-0000-4000-8000-000000000002','10000000-0000-4000-8000-000000000001','Local print',10),
 pg_temp.log_item('20000000-0000-4000-8000-000000000003','10000000-0000-4000-8000-000000000001','Cloud print',50,'delete')))->>'revision'='3','local choice applies conditional whole-version delta');
select pg_temp.assert_true((select current_weight=890 from public.inventory_spools),'chosen local weight applied');
select pg_temp.assert_true((select count(*)=2 from public.inventory_usage_logs where deleted_at is null),'chosen local live history applied');
select pg_temp.assert_true((select revision=1 from public.inventory_usage_logs where uuid='20000000-0000-4000-8000-000000000001'),'unchanged common history not rewritten');
select pg_temp.assert_true((select deleted_at is not null from public.inventory_usage_logs where uuid='20000000-0000-4000-8000-000000000003'),'cloud-only history deleted');
select pg_temp.assert_true(pg_temp.send('30000000-0000-4000-8000-000000000003',2,890,jsonb_build_array(
 pg_temp.log_item('20000000-0000-4000-8000-000000000002','10000000-0000-4000-8000-000000000001','Local print',10),
 pg_temp.log_item('20000000-0000-4000-8000-000000000003','10000000-0000-4000-8000-000000000001','Cloud print',50,'delete')))->>'revision'='3','resolution retry returns receipt');
select pg_temp.assert_true(pg_temp.send('30000000-0000-4000-8000-000000000004',3,700)->>'revision'='4','newer device update applies');
select pg_temp.assert_true(pg_temp.send('30000000-0000-4000-8000-000000000005',3,890)->>'status'='conflict','unseen newer change conflicts instead of being overwritten');
select pg_temp.assert_true((select current_weight=700 from public.inventory_spools),'stale resolution preserves newer cloud value');
-- A print deleted in cloud must be copied under a new UUID when chosen locally.
select pg_temp.assert_true(pg_temp.send('30000000-0000-4000-8000-000000000006',4,800,jsonb_build_array(
 pg_temp.log_item('20000000-0000-4000-8000-000000000001','10000000-0000-4000-8000-000000000001','Original print',100,'delete')))->>'revision'='5','remote history deletion applies');
select pg_temp.assert_true(pg_temp.send('30000000-0000-4000-8000-000000000007',5,890,jsonb_build_array(
 pg_temp.log_item('20000000-0000-4000-8000-000000000001','10000000-0000-4000-8000-000000000001','Original print',100)))->>'status'='conflict','deleted print identity cannot be resurrected');
select pg_temp.assert_true(pg_temp.send('30000000-0000-4000-8000-000000000008',5,890,jsonb_build_array(
 pg_temp.log_item('20000000-0000-4000-8000-000000000004','10000000-0000-4000-8000-000000000001','Original print',100)))->>'revision'='6','copying chosen print with new UUID succeeds');
select pg_temp.assert_true((select deleted_at is not null from public.inventory_usage_logs where uuid='20000000-0000-4000-8000-000000000001'),'original print tombstone retained');
select pg_temp.assert_true(pg_temp.send('30000000-0000-4000-8000-000000000009',6,890,'[]',true)->>'status'='applied','remote parent deletion applies');
select pg_temp.assert_true(public.get_inventory_snapshot(auth.uid(),'10000000-0000-4000-8000-000000000001')->'spool'->>'deleted_at' is not null,'review includes deleted parent');
select pg_temp.assert_true(not exists(select 1 from public.inventory_usage_logs where deleted_at is null),'parent deletion carries all history');
select pg_temp.assert_true(pg_temp.send('30000000-0000-4000-8000-000000000010',8,890)->>'status'='conflict','deleted parent cannot be resurrected');
select pg_temp.assert_true(pg_temp.send('30000000-0000-4000-8000-000000000011',0,890,jsonb_build_array(
 pg_temp.log_item('20000000-0000-4000-8000-000000000005','10000000-0000-4000-8000-000000000002','Original print',100),
 pg_temp.log_item('20000000-0000-4000-8000-000000000006','10000000-0000-4000-8000-000000000002','Local print',10)),false,'10000000-0000-4000-8000-000000000002')->>'revision'='1','new aggregate preserves chosen local version after deletion');
select pg_temp.assert_true((select count(*)=2 from public.inventory_usage_logs where deleted_at is null),'new aggregate copies its whole live history');
select set_config('request.jwt.claims','{"sub":"00000000-0000-4000-8000-0000000000f9","role":"authenticated"}',true);
select pg_temp.assert_true(public.get_inventory_snapshot(auth.uid(),'10000000-0000-4000-8000-000000000001')->'spool'->>'brand'='Other account','second account sees its own same-UUID version');
select pg_temp.assert_true(public.get_inventory_snapshot(auth.uid(),'10000000-0000-4000-8000-000000000002')->'spool'='null'::jsonb,'second account cannot read first-account copied spool');
select set_config('request.jwt.claims','{}',true);
select pg_temp.expect_error($q$select public.get_inventory_snapshot('00000000-0000-4000-8000-0000000000e9','10000000-0000-4000-8000-000000000001')$q$,'42501','missing subject rejected');
reset role;
select 'PASS: conflict review access and resolution protocol' as result;
rollback;
