-- PEIS 体检配置菜单（目录 / 诊断与表达式 / 系统参数）
-- 权限挂角色 provider_key=1（与 Booking 种子一致）

INSERT INTO whale_permissions (
    id, name, display_name, parent_name, description,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('311100000000000001', 'Peis', '健康体检', NULL,
     'whale-health PEIS 模块入口权限分组', NULL,
     TIMESTAMP '2026-07-19 00:00:00+00', TIMESTAMP '2026-07-19 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('311100000000000002', 'Peis.Catalog.View', '体检目录', 'Peis',
     '菜单入口: PeisCatalogConfig', NULL,
     TIMESTAMP '2026-07-19 00:00:00+00', TIMESTAMP '2026-07-19 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('311100000000000003', 'Peis.Diagnosis.View', '诊断与表达式', 'Peis',
     '菜单入口: PeisDiagnosisConfig', NULL,
     TIMESTAMP '2026-07-19 00:00:00+00', TIMESTAMP '2026-07-19 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('311100000000000004', 'Peis.Settings.View', '系统参数', 'Peis',
     '菜单入口: PeisModuleSettings', NULL,
     TIMESTAMP '2026-07-19 00:00:00+00', TIMESTAMP '2026-07-19 00:00:00+00', 1, 1, FALSE, NULL, NULL)
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
    ('311100000000000100', 'PeisHub', 'PeisHub', '健康体检', 'lucide:heart-pulse',
     '/peis', '', NULL, NULL, 12, TRUE, FALSE, FALSE, '', 'directory',
     '体检配置与后续业务菜单根', NULL,
     TIMESTAMP '2026-07-19 00:00:00+00', TIMESTAMP '2026-07-19 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE),
    ('311100000000000101', 'PeisCatalogConfig', 'PeisCatalogConfig', '体检目录', 'lucide:folder-tree',
     '/peis/config/catalog', '/peis/config/catalog/index', 'Peis.Catalog.View', '311100000000000100', 1,
     TRUE, FALSE, FALSE, '', 'menu', 'PeisCatalogConfigAppService · AS-PEIS-CFG-001', NULL,
     TIMESTAMP '2026-07-19 00:00:00+00', TIMESTAMP '2026-07-19 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE),
    ('311100000000000102', 'PeisDiagnosisConfig', 'PeisDiagnosisConfig', '诊断与表达式', 'lucide:stethoscope',
     '/peis/config/diagnosis', '/peis/config/diagnosis/index', 'Peis.Diagnosis.View', '311100000000000100', 2,
     TRUE, FALSE, FALSE, '', 'menu', 'PeisDiagnosisConfigAppService · AS-PEIS-CFG-002 P0', NULL,
     TIMESTAMP '2026-07-19 00:00:00+00', TIMESTAMP '2026-07-19 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE),
    ('311100000000000103', 'PeisModuleSettings', 'PeisModuleSettings', '系统参数', 'lucide:settings-2',
     '/peis/config/settings', '/peis/config/settings/index', 'Peis.Settings.View', '311100000000000100', 3,
     TRUE, FALSE, FALSE, '', 'menu', 'PeisModuleSettingsAppService · AS-PEIS-CFG-003', NULL,
     TIMESTAMP '2026-07-19 00:00:00+00', TIMESTAMP '2026-07-19 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE)
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
    ('311100000000000201', 'Peis', 'R', '1', NULL, TIMESTAMP '2026-07-19 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('311100000000000202', 'Peis.Catalog.View', 'R', '1', NULL, TIMESTAMP '2026-07-19 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('311100000000000203', 'Peis.Diagnosis.View', 'R', '1', NULL, TIMESTAMP '2026-07-19 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('311100000000000204', 'Peis.Settings.View', 'R', '1', NULL, TIMESTAMP '2026-07-19 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL)
ON CONFLICT (permission_name, provider_name, provider_key) DO UPDATE SET
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = TIMESTAMP '2026-07-19 00:00:00+00';
