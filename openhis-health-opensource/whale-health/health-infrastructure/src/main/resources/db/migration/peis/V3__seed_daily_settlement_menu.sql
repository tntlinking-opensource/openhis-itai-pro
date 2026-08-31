-- PEIS ModularFlyway V3 · 体检日结菜单（health · AS-PE-FIN-003，挂 PeisHub）
-- history: flyway_schema_history_peis
-- 权限挂角色 provider_key=1（与 V2 种子一致）
-- 菜单序：体检日结 sort_order=13，紧随团检合同对账

INSERT INTO whale_permissions (
    id, name, display_name, parent_name, description,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('311100000000000016', 'Peis.DailySettlement.View', '体检日结', 'Peis',
     '菜单入口: PhysicalExamDailySettlement · AS-PE-FIN-003', NULL,
     TIMESTAMP '2026-08-26 00:00:00+00', TIMESTAMP '2026-08-26 00:00:00+00', 1, 1, FALSE, NULL, NULL)
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
    ('311100000000000116', 'PhysicalExamDailySettlement', 'PhysicalExamDailySettlement', '体检日结',
     'lucide:calendar-check',
     '/peis/daily-settlement', '/peis/daily-settlement/index',
     'Peis.DailySettlement.View', '311100000000000100', 13,
     TRUE, FALSE, FALSE, '', 'menu',
     'PhysicalExamDailySettlementAppService · AS-PE-FIN-003', NULL,
     TIMESTAMP '2026-08-26 00:00:00+00', TIMESTAMP '2026-08-26 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE)
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
    ('311100000000000216', 'Peis.DailySettlement.View', 'R', '1', NULL,
     TIMESTAMP '2026-08-26 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL)
ON CONFLICT (permission_name, provider_name, provider_key) DO UPDATE SET
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = TIMESTAMP '2026-08-26 00:00:00+00';
