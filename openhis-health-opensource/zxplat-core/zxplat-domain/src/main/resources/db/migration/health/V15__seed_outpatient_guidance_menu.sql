-- 门诊导诊：选患者打印检查/检验/治疗申请单（菜单 + 权限；暂仅管理员可见）

INSERT INTO zxplat_permissions (
    id, name, display_name, parent_name, description,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES (
    '310410000000000001', 'Navigation.Outpatientguidance', '门诊导诊', 'Navigation',
    '菜单入口: OutpatientGuidance — 收费队列选患者，打印检查/检验/治疗申请单', NULL,
    TIMESTAMP '2026-10-13 03:00:00+00', TIMESTAMP '2026-10-13 03:00:00+00',
    1, 1, FALSE, NULL, NULL
)
ON CONFLICT (id) DO NOTHING;

-- 仅管理员角色（provider_key = 1）
INSERT INTO zxplat_permission_grants (
    id, permission_name, provider_name, provider_key,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES (
    '310410000000000002', 'Navigation.Outpatientguidance', 'R', '1', NULL,
    TIMESTAMP '2026-10-13 03:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL
)
ON CONFLICT (permission_name, provider_name, provider_key) DO NOTHING;

INSERT INTO zxplat_menus (
    id, code, name, display_name, icon, path, component, permission_name, parent_id,
    sort_order, is_enabled, is_hidden, is_external, external_url, menu_type, remark,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, keep_alive
)
VALUES (
    '310410000000000010',
    'OutpatientGuidance',
    'OutpatientGuidance',
    '门诊导诊',
    'lucide:map-pinned',
    '/outpatient/guidance',
    '/outpatient/guidance/index',
    'Navigation.Outpatientguidance',
    NULL,
    7,
    TRUE, FALSE, FALSE, '', 'menu',
    '门诊导诊：选患者打印检查/检验/治疗申请单（一级菜单，暂仅管理员）',
    NULL,
    TIMESTAMP '2026-10-13 03:00:00+00', TIMESTAMP '2026-10-13 03:00:00+00',
    1, 1, FALSE, NULL, NULL, TRUE
)
ON CONFLICT (id) DO NOTHING;
