-- =============================================================================
-- 全量：中医科 OR0007.OR0016 → 中医内科 OR0007.OR0037
-- src=1784931337878017  dst=347661278785835008
-- 在途就诊一并改；中医科药房 LO0089 一并挂目标科
-- 规则表：与目标科业务键重叠的源行软删，其余 UPDATE
-- 回滚：V20260820f__rollback_org_merge_zhongyineike.sql
-- =============================================================================

BEGIN;

-- 常量校验
DO $$
DECLARE
  src_name text;
  dst_name text;
BEGIN
  SELECT name INTO src_name FROM adm_organization WHERE id = 1784931337878017;
  SELECT name INTO dst_name FROM adm_organization WHERE id = 347661278785835008;
  IF src_name IS DISTINCT FROM '中医科' THEN
    RAISE EXCEPTION '源科室名称=%（期望 中医科）', src_name;
  END IF;
  IF dst_name IS DISTINCT FROM '中医内科' THEN
    RAISE EXCEPTION '目标科室名称=%（期望 中医内科）', dst_name;
  END IF;
END $$;

-- -----------------------------------------------------------------------------
-- A. 快照（回滚真源）
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS _bak_20260820f_charge_item;
DROP TABLE IF EXISTS _bak_20260820f_med_req;
DROP TABLE IF EXISTS _bak_20260820f_encounter;
DROP TABLE IF EXISTS _bak_20260820f_svc_req;
DROP TABLE IF EXISTS _bak_20260820f_exec_loc;
DROP TABLE IF EXISTS _bak_20260820f_svc_loc_tpl;
DROP TABLE IF EXISTS _bak_20260820f_pr_assign;
DROP TABLE IF EXISTS _bak_20260820f_practitioner;
DROP TABLE IF EXISTS _bak_20260820f_location;
DROP TABLE IF EXISTS _bak_20260820f_print_log;

CREATE TABLE _bak_20260820f_charge_item AS
SELECT id, org_id, requesting_org_id, performing_org_id, cost_org_id, version, updated_at, is_deleted
  FROM adm_charge_item
 WHERE org_id = 1784931337878017 OR requesting_org_id = 1784931337878017;

CREATE TABLE _bak_20260820f_med_req AS
SELECT id, org_id, perform_org, version, updated_at, is_deleted
  FROM med_medication_request
 WHERE org_id = 1784931337878017 OR perform_org = 1784931337878017;

CREATE TABLE _bak_20260820f_encounter AS
SELECT id, organization_id, version, updated_at, is_deleted
  FROM adm_encounter
 WHERE organization_id = 1784931337878017;

CREATE TABLE _bak_20260820f_svc_req AS
SELECT id, org_id, version, updated_at, is_deleted
  FROM wkf_service_request
 WHERE org_id = 1784931337878017;

CREATE TABLE _bak_20260820f_exec_loc AS
SELECT id, organization_id, execution_org_id, activity_definition_id, activity_category_code,
       version, updated_at, is_deleted, deleted_at, deleted_by
  FROM cus_activity_execution_location
 WHERE organization_id = 1784931337878017 OR execution_org_id = 1784931337878017;

CREATE TABLE _bak_20260820f_svc_loc_tpl AS
SELECT id, organization_id, item_table, distribution_category_code, item_definition_id,
       method_code, def_location_id, version, updated_at, is_deleted, deleted_at, deleted_by
  FROM cus_service_location_template
 WHERE organization_id = 1784931337878017;

CREATE TABLE _bak_20260820f_pr_assign AS
SELECT id, org_id, version, updated_at, is_deleted
  FROM adm_pr_assign
 WHERE org_id = 1784931337878017;

CREATE TABLE _bak_20260820f_practitioner AS
SELECT id, org_id, version, updated_at, is_deleted
  FROM adm_practitioner
 WHERE org_id = 1784931337878017;

CREATE TABLE _bak_20260820f_location AS
SELECT id, organization_id, version, updated_at, is_deleted
  FROM adm_location
 WHERE organization_id = 1784931337878017;

CREATE TABLE _bak_20260820f_print_log AS
SELECT id, organization_id, created_at, is_deleted
  FROM prt_print_event_log
 WHERE organization_id = 1784931337878017;

DO $$
DECLARE
  n integer;
BEGIN
  SELECT COUNT(*) INTO n FROM _bak_20260820f_charge_item; IF n <> 15559 THEN RAISE EXCEPTION 'bak charge=%', n; END IF;
  SELECT COUNT(*) INTO n FROM _bak_20260820f_med_req; IF n <> 4551 THEN RAISE EXCEPTION 'bak med_req=%', n; END IF;
  SELECT COUNT(*) INTO n FROM _bak_20260820f_encounter; IF n <> 278 THEN RAISE EXCEPTION 'bak encounter=%', n; END IF;
  SELECT COUNT(*) INTO n FROM _bak_20260820f_svc_req; IF n <> 170 THEN RAISE EXCEPTION 'bak svc_req=%', n; END IF;
  SELECT COUNT(*) INTO n FROM _bak_20260820f_exec_loc; IF n <> 217 THEN RAISE EXCEPTION 'bak exec_loc=%', n; END IF;
  SELECT COUNT(*) INTO n FROM _bak_20260820f_svc_loc_tpl; IF n <> 32 THEN RAISE EXCEPTION 'bak svc_loc_tpl=%', n; END IF;
  SELECT COUNT(*) INTO n FROM _bak_20260820f_pr_assign; IF n <> 13 THEN RAISE EXCEPTION 'bak pr_assign=%', n; END IF;
  SELECT COUNT(*) INTO n FROM _bak_20260820f_practitioner; IF n <> 2 THEN RAISE EXCEPTION 'bak practitioner=%', n; END IF;
  SELECT COUNT(*) INTO n FROM _bak_20260820f_location; IF n <> 1 THEN RAISE EXCEPTION 'bak location=%', n; END IF;
  SELECT COUNT(*) INTO n FROM _bak_20260820f_print_log; IF n <> 2 THEN RAISE EXCEPTION 'bak print_log=%', n; END IF;
END $$;

-- -----------------------------------------------------------------------------
-- B. 主数据 / 人员 / 位置
-- -----------------------------------------------------------------------------
UPDATE adm_location
   SET organization_id = 347661278785835008,
       version = COALESCE(version, 0) + 1,
       updated_at = NOW()
 WHERE organization_id = 1784931337878017;

UPDATE adm_practitioner
   SET org_id = 347661278785835008,
       version = COALESCE(version, 0) + 1,
       updated_at = NOW()
 WHERE org_id = 1784931337878017;

UPDATE adm_pr_assign
   SET org_id = 347661278785835008,
       version = COALESCE(version, 0) + 1,
       updated_at = NOW()
 WHERE org_id = 1784931337878017;

-- -----------------------------------------------------------------------------
-- C. 规则：重叠软删，其余改挂
-- -----------------------------------------------------------------------------
-- 执行规则：与目标科同 activity_definition_id + activity_category_code → 软删源行
UPDATE cus_activity_execution_location s
   SET is_deleted = true,
       deleted_at = NOW(),
       version = COALESCE(s.version, 0) + 1,
       updated_at = NOW()
 WHERE s.organization_id = 1784931337878017
   AND COALESCE(s.is_deleted, false) = false
   AND EXISTS (
         SELECT 1 FROM cus_activity_execution_location d
          WHERE d.organization_id = 347661278785835008
            AND COALESCE(d.is_deleted, false) = false
            AND d.activity_definition_id IS NOT DISTINCT FROM s.activity_definition_id
            AND d.activity_category_code IS NOT DISTINCT FROM s.activity_category_code
       );

UPDATE cus_activity_execution_location
   SET organization_id = 347661278785835008,
       version = COALESCE(version, 0) + 1,
       updated_at = NOW()
 WHERE organization_id = 1784931337878017
   AND COALESCE(is_deleted, false) = false;

-- 取药/发料模板：业务键重叠 → 软删源行
UPDATE cus_service_location_template s
   SET is_deleted = true,
       deleted_at = NOW(),
       version = COALESCE(s.version, 0) + 1,
       updated_at = NOW()
 WHERE s.organization_id = 1784931337878017
   AND COALESCE(s.is_deleted, false) = false
   AND EXISTS (
         SELECT 1 FROM cus_service_location_template d
          WHERE d.organization_id = 347661278785835008
            AND COALESCE(d.is_deleted, false) = false
            AND d.item_table IS NOT DISTINCT FROM s.item_table
            AND d.distribution_category_code IS NOT DISTINCT FROM s.distribution_category_code
            AND d.item_definition_id IS NOT DISTINCT FROM s.item_definition_id
            AND d.method_code IS NOT DISTINCT FROM s.method_code
            AND d.def_location_id IS NOT DISTINCT FROM s.def_location_id
       );

UPDATE cus_service_location_template
   SET organization_id = 347661278785835008,
       version = COALESCE(version, 0) + 1,
       updated_at = NOW()
 WHERE organization_id = 1784931337878017
   AND COALESCE(is_deleted, false) = false;

-- -----------------------------------------------------------------------------
-- D. 交易业务全量改挂
-- -----------------------------------------------------------------------------
UPDATE adm_encounter
   SET organization_id = 347661278785835008,
       version = COALESCE(version, 0) + 1,
       updated_at = NOW()
 WHERE organization_id = 1784931337878017;

UPDATE adm_charge_item
   SET org_id = CASE WHEN org_id = 1784931337878017 THEN 347661278785835008 ELSE org_id END,
       requesting_org_id = CASE WHEN requesting_org_id = 1784931337878017 THEN 347661278785835008 ELSE requesting_org_id END,
       performing_org_id = CASE WHEN performing_org_id = 1784931337878017 THEN 347661278785835008 ELSE performing_org_id END,
       cost_org_id = CASE WHEN cost_org_id = 1784931337878017 THEN 347661278785835008 ELSE cost_org_id END,
       version = COALESCE(version, 0) + 1,
       updated_at = NOW()
 WHERE org_id = 1784931337878017
    OR requesting_org_id = 1784931337878017
    OR performing_org_id = 1784931337878017
    OR cost_org_id = 1784931337878017;

UPDATE med_medication_request
   SET org_id = CASE WHEN org_id = 1784931337878017 THEN 347661278785835008 ELSE org_id END,
       perform_org = CASE WHEN perform_org = 1784931337878017 THEN 347661278785835008 ELSE perform_org END,
       version = COALESCE(version, 0) + 1,
       updated_at = NOW()
 WHERE org_id = 1784931337878017
    OR perform_org = 1784931337878017;

UPDATE wkf_service_request
   SET org_id = 347661278785835008,
       version = COALESCE(version, 0) + 1,
       updated_at = NOW()
 WHERE org_id = 1784931337878017;

UPDATE prt_print_event_log
   SET organization_id = 347661278785835008,
       updated_at = NOW()
 WHERE organization_id = 1784931337878017;

-- -----------------------------------------------------------------------------
-- E. 残留校验：源科有效引用必须为 0（软删规则除外）
-- -----------------------------------------------------------------------------
DO $$
DECLARE
  n integer;
BEGIN
  SELECT COUNT(*) INTO n FROM adm_encounter WHERE organization_id = 1784931337878017;
  IF n <> 0 THEN RAISE EXCEPTION '残留 encounter=%', n; END IF;

  SELECT COUNT(*) INTO n FROM adm_charge_item
   WHERE org_id = 1784931337878017 OR requesting_org_id = 1784931337878017
      OR performing_org_id = 1784931337878017 OR cost_org_id = 1784931337878017;
  IF n <> 0 THEN RAISE EXCEPTION '残留 charge_item=%', n; END IF;

  SELECT COUNT(*) INTO n FROM med_medication_request
   WHERE org_id = 1784931337878017 OR perform_org = 1784931337878017;
  IF n <> 0 THEN RAISE EXCEPTION '残留 med_req=%', n; END IF;

  SELECT COUNT(*) INTO n FROM wkf_service_request WHERE org_id = 1784931337878017;
  IF n <> 0 THEN RAISE EXCEPTION '残留 svc_req=%', n; END IF;

  SELECT COUNT(*) INTO n FROM adm_practitioner WHERE org_id = 1784931337878017;
  IF n <> 0 THEN RAISE EXCEPTION '残留 practitioner=%', n; END IF;

  SELECT COUNT(*) INTO n FROM adm_pr_assign WHERE org_id = 1784931337878017;
  IF n <> 0 THEN RAISE EXCEPTION '残留 pr_assign=%', n; END IF;

  SELECT COUNT(*) INTO n FROM adm_location WHERE organization_id = 1784931337878017;
  IF n <> 0 THEN RAISE EXCEPTION '残留 location=%', n; END IF;

  -- 规则：未删且仍挂源科必须为 0
  SELECT COUNT(*) INTO n FROM cus_activity_execution_location
   WHERE organization_id = 1784931337878017 AND COALESCE(is_deleted, false) = false;
  IF n <> 0 THEN RAISE EXCEPTION '残留 exec_loc 未删=%', n; END IF;

  SELECT COUNT(*) INTO n FROM cus_service_location_template
   WHERE organization_id = 1784931337878017 AND COALESCE(is_deleted, false) = false;
  IF n <> 0 THEN RAISE EXCEPTION '残留 svc_loc_tpl 未删=%', n; END IF;
END $$;

-- 回读摘要
SELECT 'encounter_dst' AS k, COUNT(*) AS n FROM adm_encounter WHERE organization_id = 347661278785835008
UNION ALL
SELECT 'charge_req_dst', COUNT(*) FROM adm_charge_item WHERE requesting_org_id = 347661278785835008
UNION ALL
SELECT 'med_req_dst', COUNT(*) FROM med_medication_request WHERE org_id = 347661278785835008
UNION ALL
SELECT 'location_dst', COUNT(*) FROM adm_location WHERE organization_id = 347661278785835008
UNION ALL
SELECT 'practitioner_dst', COUNT(*) FROM adm_practitioner WHERE org_id = 347661278785835008
UNION ALL
SELECT 'exec_loc_soft_del', COUNT(*) FROM cus_activity_execution_location
 WHERE id IN (SELECT id FROM _bak_20260820f_exec_loc) AND is_deleted = true
UNION ALL
SELECT 'exec_loc_moved', COUNT(*) FROM cus_activity_execution_location
 WHERE organization_id = 347661278785835008
   AND id IN (SELECT id FROM _bak_20260820f_exec_loc)
   AND COALESCE(is_deleted, false) = false
UNION ALL
SELECT 'svc_tpl_soft_del', COUNT(*) FROM cus_service_location_template
 WHERE id IN (SELECT id FROM _bak_20260820f_svc_loc_tpl) AND is_deleted = true
UNION ALL
SELECT 'svc_tpl_moved', COUNT(*) FROM cus_service_location_template
 WHERE organization_id = 347661278785835008
   AND id IN (SELECT id FROM _bak_20260820f_svc_loc_tpl)
   AND COALESCE(is_deleted, false) = false;

COMMIT;
