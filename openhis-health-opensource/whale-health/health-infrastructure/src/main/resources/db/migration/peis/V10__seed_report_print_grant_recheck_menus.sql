-- PEIS V10：复查任务表 + 报告打印/发放/复查管理菜单（AS-PEIS-REP-001/002、AS-PEIS-REC-001）
-- 权限挂角色 provider_key=1（与 V9 种子一致）

CREATE TABLE IF NOT EXISTS peis_recheck_task (
  id int8 PRIMARY KEY,
  tenant_id int8,
  org_id int8,
  created_at timestamptz NOT NULL,
  updated_at timestamptz,
  created_by int8,
  updated_by int8,
  is_deleted bool NOT NULL DEFAULT false,
  deleted_at timestamptz,
  deleted_by int8,
  version int4 NOT NULL DEFAULT 0,
  encounter_id int8 NOT NULL,
  patient_id int8,
  status_enum varchar(64) NOT NULL DEFAULT 'requested',
  reason varchar(512),
  diagnostic_report_id int8,
  completed_at timestamptz,
  completed_by int8
);

CREATE INDEX IF NOT EXISTS idx_peis_recheck_task_encounter
  ON peis_recheck_task (encounter_id, is_deleted);

COMMENT ON TABLE peis_recheck_task IS '体检复查任务（Peis 04 §4.5 方案 A 本地 Task 语义，待 WKF Task 迁入）';

-- 报告打印 menu id 010/110/210
INSERT INTO whale_permissions (
    id, name, display_name, parent_name, description,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('311100000000000010', 'Peis.ReportPrint.View', '报告打印', 'Peis',
     '菜单入口: PeisReportPrint · AS-PEIS-REP-001', NULL,
     TIMESTAMP '2026-07-25 00:00:00+00', TIMESTAMP '2026-07-25 00:00:00+00', 1, 1, FALSE, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    display_name = EXCLUDED.display_name,
    parent_name = EXCLUDED.parent_name,
    description = EXCLUDED.description,
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = EXCLUDED.updated_at;

INSERT INTO whale_menus (
    id, code, name, display_name, icon, path, component, permission_name, parent_id,
    sort_order, is_enabled, is_hidden, is_external, external_url, menu_type, remark,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, keep_alive
)
VALUES
    ('311100000000000110', 'PeisReportPrint', 'PeisReportPrint', '报告打印', 'lucide:printer',
     '/peis/report/print', '/peis/report/print/index', 'Peis.ReportPrint.View', '311100000000000100', 8,
     TRUE, FALSE, FALSE, '', 'menu', 'PeisReportPrintAppService · AS-PEIS-REP-001', NULL,
     TIMESTAMP '2026-07-25 00:00:00+00', TIMESTAMP '2026-07-25 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE)
ON CONFLICT (id) DO UPDATE SET
    code = EXCLUDED.code,
    name = EXCLUDED.name,
    display_name = EXCLUDED.display_name,
    icon = EXCLUDED.icon,
    path = EXCLUDED.path,
    component = EXCLUDED.component,
    permission_name = EXCLUDED.permission_name,
    parent_id = EXCLUDED.parent_id,
    sort_order = EXCLUDED.sort_order,
    is_enabled = EXCLUDED.is_enabled,
    is_hidden = EXCLUDED.is_hidden,
    remark = EXCLUDED.remark,
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = EXCLUDED.updated_at;

INSERT INTO whale_permission_grants (
    id, permission_name, provider_name, provider_key,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('311100000000000210', 'Peis.ReportPrint.View', 'R', '1', NULL,
     TIMESTAMP '2026-07-25 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL)
ON CONFLICT (permission_name, provider_name, provider_key) DO UPDATE SET
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = TIMESTAMP '2026-07-25 00:00:00+00';

-- 报告发放 menu id 011/111/211
INSERT INTO whale_permissions (
    id, name, display_name, parent_name, description,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('311100000000000011', 'Peis.ReportGrant.View', '报告发放', 'Peis',
     '菜单入口: PeisReportGrant · AS-PEIS-REP-002', NULL,
     TIMESTAMP '2026-07-25 00:00:00+00', TIMESTAMP '2026-07-25 00:00:00+00', 1, 1, FALSE, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    display_name = EXCLUDED.display_name,
    parent_name = EXCLUDED.parent_name,
    description = EXCLUDED.description,
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = EXCLUDED.updated_at;

INSERT INTO whale_menus (
    id, code, name, display_name, icon, path, component, permission_name, parent_id,
    sort_order, is_enabled, is_hidden, is_external, external_url, menu_type, remark,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, keep_alive
)
VALUES
    ('311100000000000111', 'PeisReportGrant', 'PeisReportGrant', '报告发放', 'lucide:package-check',
     '/peis/report/grant', '/peis/report/grant/index', 'Peis.ReportGrant.View', '311100000000000100', 9,
     TRUE, FALSE, FALSE, '', 'menu', 'PeisReportGrantAppService · AS-PEIS-REP-002', NULL,
     TIMESTAMP '2026-07-25 00:00:00+00', TIMESTAMP '2026-07-25 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE)
ON CONFLICT (id) DO UPDATE SET
    code = EXCLUDED.code,
    name = EXCLUDED.name,
    display_name = EXCLUDED.display_name,
    icon = EXCLUDED.icon,
    path = EXCLUDED.path,
    component = EXCLUDED.component,
    permission_name = EXCLUDED.permission_name,
    parent_id = EXCLUDED.parent_id,
    sort_order = EXCLUDED.sort_order,
    is_enabled = EXCLUDED.is_enabled,
    is_hidden = EXCLUDED.is_hidden,
    remark = EXCLUDED.remark,
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = EXCLUDED.updated_at;

INSERT INTO whale_permission_grants (
    id, permission_name, provider_name, provider_key,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('311100000000000211', 'Peis.ReportGrant.View', 'R', '1', NULL,
     TIMESTAMP '2026-07-25 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL)
ON CONFLICT (permission_name, provider_name, provider_key) DO UPDATE SET
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = TIMESTAMP '2026-07-25 00:00:00+00';

-- 复查管理 menu id 012/112/212
INSERT INTO whale_permissions (
    id, name, display_name, parent_name, description,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('311100000000000012', 'Peis.Recheck.View', '复查管理', 'Peis',
     '菜单入口: PeisRecheckManage · AS-PEIS-REC-001', NULL,
     TIMESTAMP '2026-07-25 00:00:00+00', TIMESTAMP '2026-07-25 00:00:00+00', 1, 1, FALSE, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    display_name = EXCLUDED.display_name,
    parent_name = EXCLUDED.parent_name,
    description = EXCLUDED.description,
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = EXCLUDED.updated_at;

INSERT INTO whale_menus (
    id, code, name, display_name, icon, path, component, permission_name, parent_id,
    sort_order, is_enabled, is_hidden, is_external, external_url, menu_type, remark,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, keep_alive
)
VALUES
    ('311100000000000112', 'PeisRecheckManage', 'PeisRecheckManage', '复查管理', 'lucide:refresh-cw',
     '/peis/recheck', '/peis/recheck/index', 'Peis.Recheck.View', '311100000000000100', 10,
     TRUE, FALSE, FALSE, '', 'menu', 'PeisRecheckManageAppService · AS-PEIS-REC-001', NULL,
     TIMESTAMP '2026-07-25 00:00:00+00', TIMESTAMP '2026-07-25 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE)
ON CONFLICT (id) DO UPDATE SET
    code = EXCLUDED.code,
    name = EXCLUDED.name,
    display_name = EXCLUDED.display_name,
    icon = EXCLUDED.icon,
    path = EXCLUDED.path,
    component = EXCLUDED.component,
    permission_name = EXCLUDED.permission_name,
    parent_id = EXCLUDED.parent_id,
    sort_order = EXCLUDED.sort_order,
    is_enabled = EXCLUDED.is_enabled,
    is_hidden = EXCLUDED.is_hidden,
    remark = EXCLUDED.remark,
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = EXCLUDED.updated_at;

INSERT INTO whale_permission_grants (
    id, permission_name, provider_name, provider_key,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('311100000000000212', 'Peis.Recheck.View', 'R', '1', NULL,
     TIMESTAMP '2026-07-25 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL)
ON CONFLICT (permission_name, provider_name, provider_key) DO UPDATE SET
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = TIMESTAMP '2026-07-25 00:00:00+00';
