-- =============================================================================
-- 回滚：中医内科 ← 中医科（从 _bak_20260820f_* 还原）
-- =============================================================================

BEGIN;

DO $$
BEGIN
  IF to_regclass('public._bak_20260820f_charge_item') IS NULL THEN
    RAISE EXCEPTION '缺少 bak 表，无法回滚';
  END IF;
END $$;

UPDATE adm_charge_item t
   SET org_id = b.org_id,
       requesting_org_id = b.requesting_org_id,
       performing_org_id = b.performing_org_id,
       cost_org_id = b.cost_org_id,
       version = COALESCE(t.version, 0) + 1,
       updated_at = NOW()
  FROM _bak_20260820f_charge_item b
 WHERE t.id = b.id;

UPDATE med_medication_request t
   SET org_id = b.org_id,
       perform_org = b.perform_org,
       version = COALESCE(t.version, 0) + 1,
       updated_at = NOW()
  FROM _bak_20260820f_med_req b
 WHERE t.id = b.id;

UPDATE adm_encounter t
   SET organization_id = b.organization_id,
       version = COALESCE(t.version, 0) + 1,
       updated_at = NOW()
  FROM _bak_20260820f_encounter b
 WHERE t.id = b.id;

UPDATE wkf_service_request t
   SET org_id = b.org_id,
       version = COALESCE(t.version, 0) + 1,
       updated_at = NOW()
  FROM _bak_20260820f_svc_req b
 WHERE t.id = b.id;

UPDATE cus_activity_execution_location t
   SET organization_id = b.organization_id,
       execution_org_id = b.execution_org_id,
       is_deleted = b.is_deleted,
       deleted_at = b.deleted_at,
       deleted_by = b.deleted_by,
       version = COALESCE(t.version, 0) + 1,
       updated_at = NOW()
  FROM _bak_20260820f_exec_loc b
 WHERE t.id = b.id;

UPDATE cus_service_location_template t
   SET organization_id = b.organization_id,
       is_deleted = b.is_deleted,
       deleted_at = b.deleted_at,
       deleted_by = b.deleted_by,
       version = COALESCE(t.version, 0) + 1,
       updated_at = NOW()
  FROM _bak_20260820f_svc_loc_tpl b
 WHERE t.id = b.id;

UPDATE adm_pr_assign t
   SET org_id = b.org_id,
       version = COALESCE(t.version, 0) + 1,
       updated_at = NOW()
  FROM _bak_20260820f_pr_assign b
 WHERE t.id = b.id;

UPDATE adm_practitioner t
   SET org_id = b.org_id,
       version = COALESCE(t.version, 0) + 1,
       updated_at = NOW()
  FROM _bak_20260820f_practitioner b
 WHERE t.id = b.id;

UPDATE adm_location t
   SET organization_id = b.organization_id,
       version = COALESCE(t.version, 0) + 1,
       updated_at = NOW()
  FROM _bak_20260820f_location b
 WHERE t.id = b.id;

UPDATE prt_print_event_log t
   SET organization_id = b.organization_id
  FROM _bak_20260820f_print_log b
 WHERE t.id = b.id;

-- 校验：源科 encounter 应回到 278
DO $$
DECLARE
  n integer;
BEGIN
  SELECT COUNT(*) INTO n FROM adm_encounter WHERE organization_id = 1784931337878017;
  IF n <> 278 THEN RAISE EXCEPTION '回滚后 encounter 源科=%（期望 278）', n; END IF;
  SELECT COUNT(*) INTO n FROM adm_location WHERE organization_id = 1784931337878017;
  IF n <> 1 THEN RAISE EXCEPTION '回滚后 location 源科=%（期望 1）', n; END IF;
END $$;

COMMIT;
