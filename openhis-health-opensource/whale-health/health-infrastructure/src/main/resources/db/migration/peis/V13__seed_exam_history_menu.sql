-- PEIS V13：学生/教职工体检历史菜单（AS-PEIS-ARCH-001 扩展 / PeisExamHistory）
-- 权限挂角色 provider_key=1（与既有种子一致）

INSERT INTO whale_permissions (
    id, name, display_name, parent_name, description,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('311100000000000011', 'Peis.ExamHistory.View', '体检历史', 'Peis',
     '菜单入口: PeisExamHistory · 学生/教职工历年体检与档案完整性', NULL,
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
    ('311100000000000111', 'PeisExamHistory', 'PeisExamHistory', '体检历史', 'lucide:history',
     '/peis/history', '/peis/history/index', 'Peis.ExamHistory.View', '311100000000000100', 11,
     TRUE, FALSE, FALSE, '', 'menu', 'PeisExamHistoryAppService · 历年体检+档案完整性', NULL,
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
    ('311100000000000211', 'Peis.ExamHistory.View', 'R', '1',
     NULL, TIMESTAMP '2026-07-25 00:00:00+00', TIMESTAMP '2026-07-25 00:00:00+00', 1, 1, FALSE, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET
    permission_name = EXCLUDED.permission_name,
    provider_name = EXCLUDED.provider_name,
    provider_key = EXCLUDED.provider_key,
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = EXCLUDED.updated_at;
