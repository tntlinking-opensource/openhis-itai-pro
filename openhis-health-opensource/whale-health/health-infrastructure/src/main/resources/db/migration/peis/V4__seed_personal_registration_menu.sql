-- PEIS 个检登记菜单（挂 PeisHub 下，sort_order=0 优先于目录）
-- 权限挂角色 provider_key=1（与 V2 种子一致）

INSERT INTO whale_permissions (
    id, name, display_name, parent_name, description,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('311100000000000005', 'Peis.Registration.View', '个检登记', 'Peis',
     '菜单入口: PeisPersonalRegistration', NULL,
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
    ('311100000000000105', 'PeisPersonalRegistration', 'PeisPersonalRegistration', '个检登记', 'lucide:clipboard-list',
     '/peis/personal-register', '/peis/personal-register/index', 'Peis.Registration.View', '311100000000000100', 0,
     TRUE, FALSE, FALSE, '', 'menu', 'PeisPersonalRegistrationAppService · AS-PEIS-REG-001', NULL,
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
    ('311100000000000205', 'Peis.Registration.View', 'R', '1', NULL,
     TIMESTAMP '2026-07-19 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL)
ON CONFLICT (permission_name, provider_name, provider_key) DO UPDATE SET
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = TIMESTAMP '2026-07-19 00:00:00+00';
