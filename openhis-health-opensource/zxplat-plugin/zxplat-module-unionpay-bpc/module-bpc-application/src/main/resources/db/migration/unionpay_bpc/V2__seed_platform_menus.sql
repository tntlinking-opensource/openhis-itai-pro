-- UnionPay BPC menu/permission seeds (from origin/master V3__UnionPayBpc_FinanceOrderMenu.sql)

INSERT INTO zxplat_permissions (
    id, name, display_name, parent_name, description,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('321000000000000001', 'UnionPayBpc', '银联收单', NULL,
     'zxplat-unionpay-bpc 模块权限分组', NULL,
     TIMESTAMP '2026-06-30 00:00:00+00', TIMESTAMP '2026-06-30 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('321000000000000002', 'unionpay-bpc.order.view', '银联订单查询', 'UnionPayBpc',
     '财务银联 BPC 订单分页查询（P1）', NULL,
     TIMESTAMP '2026-06-30 00:00:00+00', TIMESTAMP '2026-06-30 00:00:00+00', 1, 1, FALSE, NULL, NULL)
ON CONFLICT (id) DO NOTHING;

INSERT INTO zxplat_menus (
    id, code, name, display_name, icon, path, component, permission_name, parent_id,
    sort_order, is_enabled, is_hidden, is_external, external_url, menu_type, remark,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, keep_alive
)
VALUES
    ('321000000000000100', 'UnionPayBpcHub', 'UnionPayBpcHub', '银联收单', 'lucide:credit-card',
     '/finance/unionpay-bpc', '', NULL, NULL, 15,
     TRUE, FALSE, FALSE, '', 'directory', 'module_id=unionpay-bpc; top-level menu', NULL,
     TIMESTAMP '2026-06-30 00:00:00+00', TIMESTAMP '2026-06-30 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE),
    ('321000000000000101', 'UnionPayBpcOrders', 'UnionPayBpcOrders', '银联订单查询', 'lucide:search',
     '/finance/unionpay-bpc/orders', '/finance/unionpay-bpc/orders/index', 'unionpay-bpc.order.view', '321000000000000100', 1,
     TRUE, FALSE, FALSE, '', 'menu', 'UnionPayBpcAppService · pageOrders P1', NULL,
     TIMESTAMP '2026-06-30 00:00:00+00', TIMESTAMP '2026-06-30 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE)
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

INSERT INTO zxplat_permission_grants (
    id, permission_name, provider_name, provider_key,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('321000000000000011', 'UnionPayBpc', 'R', '1', NULL, TIMESTAMP '2026-06-30 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('321000000000000012', 'unionpay-bpc.order.view', 'R', '1', NULL, TIMESTAMP '2026-06-30 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('321000000000000013', 'UnionPayBpc', 'R', '14', NULL, TIMESTAMP '2026-06-30 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('321000000000000014', 'unionpay-bpc.order.view', 'R', '14', NULL, TIMESTAMP '2026-06-30 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL)
ON CONFLICT (permission_name, provider_name, provider_key) DO UPDATE SET
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = EXCLUDED.created_at;
