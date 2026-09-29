-- 收费折扣配置菜单（AS-BAS-CFG-002）：挂在「配置管理」下（与合管配备同级）
-- 前端组件：/config/chargediscountconfig/index
-- 父菜单：ConfigManagementRoot = 310400000000000123

INSERT INTO whale_permissions (
    id, name, display_name, parent_name, description,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES (
    '310700000000000210',
    'Navigation.ChargeDiscountConfig',
    '收费折扣配置',
    'Navigation',
    '菜单入口: ChargeDiscountConfig — 三大目录可打折大类/单项维护（配置管理）',
    NULL,
    TIMESTAMP '2026-09-25 00:00:00+00',
    TIMESTAMP '2026-09-25 00:00:00+00',
    1, 1, FALSE, NULL, NULL
)
ON CONFLICT (id) DO UPDATE SET
    name          = EXCLUDED.name,
    display_name  = EXCLUDED.display_name,
    parent_name   = EXCLUDED.parent_name,
    description   = EXCLUDED.description,
    is_deleted    = FALSE,
    deleted_at    = NULL,
    deleted_by    = NULL,
    updated_at    = EXCLUDED.updated_at;

INSERT INTO whale_menus (
    id, code, name, display_name, icon, path, component, permission_name, parent_id,
    sort_order, is_enabled, is_hidden, is_external, external_url, menu_type, remark,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, keep_alive
)
VALUES (
    '310400000000000210',
    'ChargeDiscountConfig',
    'ChargeDiscountConfig',
    '收费折扣配置',
    'lucide:percent',
    '/config/chargediscountconfig',
    '/config/chargediscountconfig/index',
    'Navigation.ChargeDiscountConfig',
    '310400000000000123',
    31,
    TRUE, FALSE, FALSE, '', 'menu',
    '配置管理-收费折扣配置（AS-BAS-CFG-002 / ChargeDiscountConfigAppService）',
    NULL,
    TIMESTAMP '2026-09-25 00:00:00+00',
    TIMESTAMP '2026-09-25 00:00:00+00',
    1, 1, FALSE, NULL, NULL, TRUE
)
ON CONFLICT (id) DO UPDATE SET
    code            = EXCLUDED.code,
    name            = EXCLUDED.name,
    display_name    = EXCLUDED.display_name,
    icon            = EXCLUDED.icon,
    path            = EXCLUDED.path,
    component       = EXCLUDED.component,
    permission_name = EXCLUDED.permission_name,
    parent_id       = EXCLUDED.parent_id,
    sort_order      = EXCLUDED.sort_order,
    is_enabled      = EXCLUDED.is_enabled,
    is_hidden       = EXCLUDED.is_hidden,
    remark          = EXCLUDED.remark,
    is_deleted      = FALSE,
    deleted_at      = NULL,
    deleted_by      = NULL,
    updated_at      = EXCLUDED.updated_at;

-- 授权给内置超管角色 provider_key=1（与平台种子惯例一致）
INSERT INTO whale_permission_grants (
    id, permission_name, provider_name, provider_key,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES (
    '310800000000000210',
    'Navigation.ChargeDiscountConfig',
    'R',
    '1',
    NULL,
    TIMESTAMP '2026-09-25 00:00:00+00',
    NULL,
    1, NULL, FALSE, NULL, NULL
)
ON CONFLICT (permission_name, provider_name, provider_key) DO UPDATE SET
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = TIMESTAMP '2026-09-25 00:00:00+00';
