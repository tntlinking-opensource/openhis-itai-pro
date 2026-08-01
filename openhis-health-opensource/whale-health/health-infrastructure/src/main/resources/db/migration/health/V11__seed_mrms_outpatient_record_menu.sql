-- 病案管理：门诊病历查询菜单 / 权限 / 角色授权
-- permissions/menus/grants 使用 MRMS 8800x 族续号 …007（…001～…006 已用于病案检索等，避开 310800… 冲突段）

INSERT INTO whale_permissions (
    id, name, display_name, parent_name, description,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('880020000000000007', 'Navigation.MrmsOutpatientRecordList', '门诊病历查询', 'Navigation',
     '菜单入口: MrmsOutpatientRecordList — 门诊病历跨患者查询/打印', NULL,
     TIMESTAMP '2026-07-29 12:00:00+00', TIMESTAMP '2026-07-29 12:00:00+00', 1, 1, FALSE, NULL, NULL)
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
VALUES
    ('880010000000000007', 'MrmsOutpatientRecordList', 'MrmsOutpatientRecordList', '门诊病历查询', 'lucide:file-search',
     '/mrms/outpatient-record', '/mrms/outpatient-record/index', 'Navigation.MrmsOutpatientRecordList', '880010000000000000', 5,
     TRUE, FALSE, FALSE, '', 'menu', '病案室查询门诊 EMR 文书并打印', NULL,
     TIMESTAMP '2026-07-29 12:00:00+00', TIMESTAMP '2026-07-29 12:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE)
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
    menu_type       = EXCLUDED.menu_type,
    remark          = EXCLUDED.remark,
    is_deleted      = FALSE,
    deleted_at      = NULL,
    deleted_by      = NULL,
    updated_at      = EXCLUDED.updated_at,
    keep_alive      = EXCLUDED.keep_alive;

-- 授权：与病案检索同角色 provider_key=1（超管/病案角色种子口径）；id 用 880030…007 避开 310800… 段
INSERT INTO whale_permission_grants (
    id, permission_name, provider_name, provider_key,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('880030000000000007', 'Navigation.MrmsOutpatientRecordList', 'R', '1', NULL,
     TIMESTAMP '2026-07-29 12:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL)
ON CONFLICT (permission_name, provider_name, provider_key) DO UPDATE SET
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = TIMESTAMP '2026-07-29 12:00:00+00';
