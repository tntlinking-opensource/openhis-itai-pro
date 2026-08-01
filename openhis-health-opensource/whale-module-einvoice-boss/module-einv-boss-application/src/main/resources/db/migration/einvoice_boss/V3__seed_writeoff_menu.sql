-- 手动冲红菜单与权限（einvoice-boss）
INSERT INTO whale_permissions (
    id, name, display_name, parent_name, description,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('320900000000000040', 'einvoice-boss.writeoff.view', '手动冲红查看', 'EinvoiceBoss',
     '查看待冲红列表与冲红记录', NULL,
     TIMESTAMP '2026-07-29 00:00:00+00', TIMESTAMP '2026-07-29 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('320900000000000041', 'einvoice-boss.writeoff.operate', '手动冲红操作', 'EinvoiceBoss',
     '发起手动冲红', NULL,
     TIMESTAMP '2026-07-29 00:00:00+00', TIMESTAMP '2026-07-29 00:00:00+00', 1, 1, FALSE, NULL, NULL)
ON CONFLICT (id) DO NOTHING;

INSERT INTO whale_menus (
    id, code, name, display_name, icon, path, component, permission_name, parent_id,
    sort_order, is_enabled, is_hidden, is_external, external_url, menu_type, remark,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, keep_alive
)
VALUES
    ('320900000000000106', 'EinvoiceWriteOff', 'EinvoiceWriteOff', '手动冲红', 'lucide:file-x-2',
     '/finance/einvoice/writeoff', '/finance/einvoice/writeoff/index', 'einvoice-boss.writeoff.view', '320900000000000100', 6,
     TRUE, FALSE, FALSE, '', 'menu', 'EinvoiceBossWriteOffAppService · 手动冲红', NULL,
     TIMESTAMP '2026-07-29 00:00:00+00', TIMESTAMP '2026-07-29 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE)
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

INSERT INTO whale_permission_grants (
    id, permission_name, provider_name, provider_key,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('320900000000000042', 'einvoice-boss.writeoff.view', 'R', '1', NULL, TIMESTAMP '2026-07-29 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('320900000000000043', 'einvoice-boss.writeoff.operate', 'R', '1', NULL, TIMESTAMP '2026-07-29 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('320900000000000044', 'einvoice-boss.writeoff.view', 'R', '14', NULL, TIMESTAMP '2026-07-29 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('320900000000000045', 'einvoice-boss.writeoff.operate', 'R', '14', NULL, TIMESTAMP '2026-07-29 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL)
ON CONFLICT (permission_name, provider_name, provider_key) DO UPDATE SET
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = EXCLUDED.created_at;
