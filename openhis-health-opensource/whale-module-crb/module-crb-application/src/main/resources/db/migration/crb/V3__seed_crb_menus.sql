-- 传染病监测（CRB）：编码对照 + 推送监控菜单 / 权限 / 角色授权
-- permissions/menus/grants 使用 CRB 89007x 族（89001/89002/89003 已被 V10 报表占用）

INSERT INTO whale_permissions (
    id, name, display_name, parent_name, description,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('890072000000000001', 'Navigation.Crb', '传染病监测', 'Navigation',
     '菜单目录: Crb — 编码对照与推送监控', NULL,
     TIMESTAMP '2026-07-29 16:00:00+00', TIMESTAMP '2026-07-29 16:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('890072000000000002', 'Navigation.CrbDeptMap', '科室对照', 'Navigation',
     '菜单入口: CrbDeptMap — 科室编码对照维护', NULL,
     TIMESTAMP '2026-07-29 16:00:00+00', TIMESTAMP '2026-07-29 16:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('890072000000000003', 'Navigation.CrbLabItemMap', '检验对照', 'Navigation',
     '菜单入口: CrbLabItemMap — 检验项目编码对照维护', NULL,
     TIMESTAMP '2026-07-29 16:00:00+00', TIMESTAMP '2026-07-29 16:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('890072000000000004', 'Navigation.CrbDrugMap', '药品对照', 'Navigation',
     '菜单入口: CrbDrugMap — 药品编码对照维护', NULL,
     TIMESTAMP '2026-07-29 16:00:00+00', TIMESTAMP '2026-07-29 16:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('890072000000000005', 'Navigation.CrbPushMonitor', '推送监控', 'Navigation',
     '菜单入口: CrbPushMonitor — EMR 推送任务监控与重试', NULL,
     TIMESTAMP '2026-07-29 16:00:00+00', TIMESTAMP '2026-07-29 16:00:00+00', 1, 1, FALSE, NULL, NULL)
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
    ('890071000000000000', 'CrbManagement', 'CrbManagement', '传染病监测', 'lucide:activity',
     '/crb', '', NULL, NULL, 15, TRUE, FALSE, FALSE, '', 'directory',
     '科室/检验/药品对照与推送监控', NULL,
     TIMESTAMP '2026-07-29 16:00:00+00', TIMESTAMP '2026-07-29 16:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE),

    ('890071000000000001', 'CrbDeptMap', 'CrbDeptMap', '科室对照', 'lucide:building-2',
     '/crb/dept-map', '/crb/dept-map/index', 'Navigation.CrbDeptMap', '890071000000000000', 1,
     TRUE, FALSE, FALSE, '', 'menu', '院内科室与国家科室编码对照', NULL,
     TIMESTAMP '2026-07-29 16:00:00+00', TIMESTAMP '2026-07-29 16:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE),

    ('890071000000000002', 'CrbLabItemMap', 'CrbLabItemMap', '检验对照', 'lucide:flask-conical',
     '/crb/lab-item-map', '/crb/lab-item-map/index', 'Navigation.CrbLabItemMap', '890071000000000000', 2,
     TRUE, FALSE, FALSE, '', 'menu', '检验项目与国家项目编码对照', NULL,
     TIMESTAMP '2026-07-29 16:00:00+00', TIMESTAMP '2026-07-29 16:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE),

    ('890071000000000003', 'CrbDrugMap', 'CrbDrugMap', '药品对照', 'lucide:pill',
     '/crb/drug-map', '/crb/drug-map/index', 'Navigation.CrbDrugMap', '890071000000000000', 3,
     TRUE, FALSE, FALSE, '', 'menu', '院内药品与国家药品编码对照', NULL,
     TIMESTAMP '2026-07-29 16:00:00+00', TIMESTAMP '2026-07-29 16:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE),

    ('890071000000000004', 'CrbPushMonitor', 'CrbPushMonitor', '推送监控', 'lucide:radio-tower',
     '/crb/push-monitor', '/crb/push-monitor/index', 'Navigation.CrbPushMonitor', '890071000000000000', 4,
     TRUE, FALSE, FALSE, '', 'menu', '传染病 EMR 推送任务监控', NULL,
     TIMESTAMP '2026-07-29 16:00:00+00', TIMESTAMP '2026-07-29 16:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE)
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

INSERT INTO whale_permission_grants (
    id, permission_name, provider_name, provider_key,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('890073000000000001', 'Navigation.Crb', 'R', '1', NULL,
     TIMESTAMP '2026-07-29 16:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('890073000000000002', 'Navigation.CrbDeptMap', 'R', '1', NULL,
     TIMESTAMP '2026-07-29 16:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('890073000000000003', 'Navigation.CrbLabItemMap', 'R', '1', NULL,
     TIMESTAMP '2026-07-29 16:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('890073000000000004', 'Navigation.CrbDrugMap', 'R', '1', NULL,
     TIMESTAMP '2026-07-29 16:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('890073000000000005', 'Navigation.CrbPushMonitor', 'R', '1', NULL,
     TIMESTAMP '2026-07-29 16:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL)
ON CONFLICT (permission_name, provider_name, provider_key) DO UPDATE SET
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = TIMESTAMP '2026-07-29 16:00:00+00';
