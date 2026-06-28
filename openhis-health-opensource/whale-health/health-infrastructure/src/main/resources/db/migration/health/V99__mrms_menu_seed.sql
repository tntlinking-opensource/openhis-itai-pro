-- MRMS Wave 3：病案管理菜单 + 权限种子（openhis-pro-ui views/mrms）
-- Flyway V99（须在 whale_sequence_seed_sync 之前）

-- ========== 1. 权限定义 ==========
INSERT INTO whale_permissions (
    id, name, display_name, parent_name, description,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('880020000000000001', 'Navigation.MrmsCaseRecordList', '病案检索', 'Navigation',
     '菜单入口: MrmsCaseRecordList — 病案检索列表', NULL,
     TIMESTAMP '2026-06-27 12:00:00+00', TIMESTAMP '2026-06-27 12:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('880020000000000002', 'Navigation.MrmsQcWorkbench', '质控工作台', 'Navigation',
     '菜单入口: MrmsQcWorkbench — 病案质控与归档', NULL,
     TIMESTAMP '2026-06-27 12:00:00+00', TIMESTAMP '2026-06-27 12:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('880020000000000003', 'Navigation.MrmsBorrow', '借阅复印', 'Navigation',
     '菜单入口: MrmsBorrow — 病案借阅复印审批', NULL,
     TIMESTAMP '2026-06-27 12:00:00+00', TIMESTAMP '2026-06-27 12:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('880020000000000004', 'Navigation.MrmsUpload', '4401上传', 'Navigation',
     '菜单入口: MrmsUpload — 医保病案首页手动上传', NULL,
     TIMESTAMP '2026-06-27 12:00:00+00', TIMESTAMP '2026-06-27 12:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('880020000000000005', 'Navigation.MrmsCaseRecordHomepage', '病案首页编辑', 'Navigation',
     '隐藏路由: MrmsCaseRecordHomepage — 全屏首页编辑', NULL,
     TIMESTAMP '2026-06-27 12:00:00+00', TIMESTAMP '2026-06-27 12:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('880020000000000006', 'Navigation.MrmsCaseRecordVolume', '卷宗预览', 'Navigation',
     '隐藏路由: MrmsCaseRecordVolume — 卷宗 PDF 预览', NULL,
     TIMESTAMP '2026-06-27 12:00:00+00', TIMESTAMP '2026-06-27 12:00:00+00', 1, 1, FALSE, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET
    name          = EXCLUDED.name,
    display_name  = EXCLUDED.display_name,
    parent_name   = EXCLUDED.parent_name,
    description   = EXCLUDED.description,
    is_deleted    = FALSE,
    deleted_at    = NULL,
    deleted_by    = NULL,
    updated_at    = EXCLUDED.updated_at;

-- ========== 2. 菜单 ==========
INSERT INTO whale_menus (
    id, code, name, display_name, icon, path, component, permission_name, parent_id,
    sort_order, is_enabled, is_hidden, is_external, external_url, menu_type, remark,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, keep_alive
)
VALUES
    ('880010000000000000', 'MrmsManagement', 'MrmsManagement', '病案管理', 'lucide:folder-archive',
     '/mrms', '', NULL, NULL, 12, TRUE, FALSE, FALSE, '', 'directory',
     '病案检索 / 质控 / 借阅 / 4401 手动上传', NULL,
     TIMESTAMP '2026-06-27 12:00:00+00', TIMESTAMP '2026-06-27 12:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE),

    ('880010000000000001', 'MrmsCaseRecordList', 'MrmsCaseRecordList', '病案检索', 'lucide:search',
     '/mrms/case-record', '/mrms/case-record/index', 'Navigation.MrmsCaseRecordList', '880010000000000000', 1,
     TRUE, FALSE, FALSE, '', 'menu', '病案室/医生检索与打开首页', NULL,
     TIMESTAMP '2026-06-27 12:00:00+00', TIMESTAMP '2026-06-27 12:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE),

    ('880010000000000002', 'MrmsQcWorkbench', 'MrmsQcWorkbench', '质控工作台', 'lucide:clipboard-check',
     '/mrms/qc-workbench', '/mrms/qc-workbench/index', 'Navigation.MrmsQcWorkbench', '880010000000000000', 2,
     TRUE, FALSE, FALSE, '', 'menu', '质控、归档、解档', NULL,
     TIMESTAMP '2026-06-27 12:00:00+00', TIMESTAMP '2026-06-27 12:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE),

    ('880010000000000003', 'MrmsBorrow', 'MrmsBorrow', '借阅复印', 'lucide:book-open',
     '/mrms/borrow', '/mrms/borrow/index', 'Navigation.MrmsBorrow', '880010000000000000', 3,
     TRUE, FALSE, FALSE, '', 'menu', '借阅申请审批与借还', NULL,
     TIMESTAMP '2026-06-27 12:00:00+00', TIMESTAMP '2026-06-27 12:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE),

    ('880010000000000004', 'MrmsUpload', 'MrmsUpload', '4401上传', 'lucide:upload-cloud',
     '/mrms/upload', '/mrms/upload/index', 'Navigation.MrmsUpload', '880010000000000000', 4,
     TRUE, FALSE, FALSE, '', 'menu', '医保病案首页手动上传（无自动上传）', NULL,
     TIMESTAMP '2026-06-27 12:00:00+00', TIMESTAMP '2026-06-27 12:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE),

    ('880010000000000005', 'MrmsCaseRecordHomepage', 'MrmsCaseRecordHomepage', '病案首页', 'lucide:file-text',
     '/mrms/case-record/homepage/:caseRecordId', '/mrms/case-record/homepage/index',
     'Navigation.MrmsCaseRecordHomepage', '880010000000000000', 10,
     TRUE, TRUE, FALSE, '', 'menu', '列表跳转全屏编辑，不单独展示', NULL,
     TIMESTAMP '2026-06-27 12:00:00+00', TIMESTAMP '2026-06-27 12:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE),

    ('880010000000000006', 'MrmsCaseRecordVolume', 'MrmsCaseRecordVolume', '卷宗预览', 'lucide:files',
     '/mrms/case-record/volume/:caseRecordId', '/mrms/case-record/volume/index',
     'Navigation.MrmsCaseRecordVolume', '880010000000000000', 11,
     TRUE, TRUE, FALSE, '', 'menu', '卷宗 PDF 预览，列表/质控跳转', NULL,
     TIMESTAMP '2026-06-27 12:00:00+00', TIMESTAMP '2026-06-27 12:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE)
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

-- ========== 3. 管理员角色授权（provider_key = 1） ==========
INSERT INTO whale_permission_grants (
    id, permission_name, provider_name, provider_key,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('880030000000000001', 'Navigation.MrmsCaseRecordList', 'R', '1', NULL,
     TIMESTAMP '2026-06-27 12:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('880030000000000002', 'Navigation.MrmsQcWorkbench', 'R', '1', NULL,
     TIMESTAMP '2026-06-27 12:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('880030000000000003', 'Navigation.MrmsBorrow', 'R', '1', NULL,
     TIMESTAMP '2026-06-27 12:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('880030000000000004', 'Navigation.MrmsUpload', 'R', '1', NULL,
     TIMESTAMP '2026-06-27 12:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('880030000000000005', 'Navigation.MrmsCaseRecordHomepage', 'R', '1', NULL,
     TIMESTAMP '2026-06-27 12:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('880030000000000006', 'Navigation.MrmsCaseRecordVolume', 'R', '1', NULL,
     TIMESTAMP '2026-06-27 12:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL)
ON CONFLICT (permission_name, provider_name, provider_key) DO UPDATE SET
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = TIMESTAMP '2026-06-27 12:00:00+00';
