-- EInvoice Boss menu/permission seeds (from origin/master V2__EinvoiceBoss_Seed.sql)

INSERT INTO whale_permissions (
    id, name, display_name, parent_name, description,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('320900000000000001', 'EinvoiceBoss', '电子发票', NULL,
     'whale-einvoice-boss 模块入口权限分组', NULL,
     TIMESTAMP '2026-06-18 00:00:00+00', TIMESTAMP '2026-06-18 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('320900000000000002', 'einvoice-boss.compensation.view', '待补偿查看', 'EinvoiceBoss',
     '查看待补偿开票列表与提醒', NULL,
     TIMESTAMP '2026-06-18 00:00:00+00', TIMESTAMP '2026-06-18 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('320900000000000003', 'einvoice-boss.compensation.operate', '待补偿操作', 'EinvoiceBoss',
     '重新入队/立即开具/流水号查票', NULL,
     TIMESTAMP '2026-06-18 00:00:00+00', TIMESTAMP '2026-06-18 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('320900000000000004', 'einvoice-boss.document.view', '电子发票查询', 'EinvoiceBoss',
     '电子发票查询列表（P1）', NULL,
     TIMESTAMP '2026-06-18 00:00:00+00', TIMESTAMP '2026-06-18 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('320900000000000005', 'einvoice-boss.document.adjust', '电子发票调额', 'EinvoiceBoss',
     '金额调整页（P1）', NULL,
     TIMESTAMP '2026-06-18 00:00:00+00', TIMESTAMP '2026-06-18 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('320900000000000006', 'einvoice-boss.cashier.qr', '收费窗电子票', 'EinvoiceBoss',
     '收费窗 QR/进度（P1）', NULL,
     TIMESTAMP '2026-06-18 00:00:00+00', TIMESTAMP '2026-06-18 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('320900000000000007', 'einvoice-boss.contrast.view', '收费对照查看', 'EinvoiceBoss',
     '查看院内收费项与博思收费项对照（P2）', NULL,
     TIMESTAMP '2026-06-18 00:00:00+00', TIMESTAMP '2026-06-18 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('320900000000000008', 'einvoice-boss.contrast.manage', '收费对照维护', 'EinvoiceBoss',
     '新增/编辑/删除收费对照（P2）', NULL,
     TIMESTAMP '2026-06-18 00:00:00+00', TIMESTAMP '2026-06-18 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('320900000000000009', 'einvoice-boss.reconcile.view', '财务日结核对', 'EinvoiceBoss',
     '核对本地与博思总笔数（P2）', NULL,
     TIMESTAMP '2026-06-18 00:00:00+00', TIMESTAMP '2026-06-18 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('320900000000000010', 'einvoice-boss.common.issue', '通用票开具', 'EinvoiceBoss',
     '医疗通用电子票据手工开具（P3）', NULL,
     TIMESTAMP '2026-06-18 00:00:00+00', TIMESTAMP '2026-06-18 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('320900000000000011', 'einvoice-boss.account.view', '入账状态查询', 'EinvoiceBoss',
     '查询博思电子票据入账状态（P3）', NULL,
     TIMESTAMP '2026-06-18 00:00:00+00', TIMESTAMP '2026-06-18 00:00:00+00', 1, 1, FALSE, NULL, NULL)
ON CONFLICT (id) DO NOTHING;

INSERT INTO whale_menus (
    id, code, name, display_name, icon, path, component, permission_name, parent_id,
    sort_order, is_enabled, is_hidden, is_external, external_url, menu_type, remark,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, keep_alive
)
VALUES
    ('320900000000000100', 'EinvoiceBossHub', 'EinvoiceBossHub', '电子发票', 'lucide:file-badge',
     '/finance/einvoice', '', NULL, NULL, 14,
     TRUE, FALSE, FALSE, '', 'directory', 'module_id=einvoice-boss; top-level menu', NULL,
     TIMESTAMP '2026-06-18 00:00:00+00', TIMESTAMP '2026-06-18 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE),
    ('320900000000000101', 'EinvoiceCompensation', 'EinvoiceCompensation', '待补偿开票', 'lucide:alert-triangle',
     '/finance/einvoice/compensation', '/finance/einvoice/compensation/index', 'einvoice-boss.compensation.view', '320900000000000100', 1,
     TRUE, FALSE, FALSE, '', 'menu', 'EinvoiceBossCompensationAppService · 待补偿 P0', NULL,
     TIMESTAMP '2026-06-18 00:00:00+00', TIMESTAMP '2026-06-18 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE),
    ('320900000000000102', 'EinvoiceList', 'EinvoiceList', '电子发票查询', 'lucide:search',
     '/finance/einvoice/list', '/finance/einvoice/list/index', 'einvoice-boss.document.view', '320900000000000100', 2,
     TRUE, FALSE, FALSE, '', 'menu', 'EinvoiceBossListAppService · pageDocuments P1', NULL,
     TIMESTAMP '2026-06-18 00:00:00+00', TIMESTAMP '2026-06-18 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE),
    ('320900000000000103', 'EinvoiceContrast', 'EinvoiceContrast', '收费对照维护', 'lucide:git-compare',
     '/finance/einvoice/contrast', '/finance/einvoice/contrast/index', 'einvoice-boss.contrast.view', '320900000000000100', 3,
     TRUE, FALSE, FALSE, '', 'menu', 'EinvoiceBossContrastAppService · pageContrasts P2', NULL,
     TIMESTAMP '2026-06-18 00:00:00+00', TIMESTAMP '2026-06-18 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE),
    ('320900000000000104', 'EinvoiceReconcile', 'EinvoiceReconcile', '财务日结核对', 'lucide:scale',
     '/finance/einvoice/reconcile', '/finance/einvoice/reconcile/index', 'einvoice-boss.reconcile.view', '320900000000000100', 4,
     TRUE, FALSE, FALSE, '', 'menu', 'EinvoiceBossReconcileAppService · checkDailyTotal P2', NULL,
     TIMESTAMP '2026-06-18 00:00:00+00', TIMESTAMP '2026-06-18 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE),
    ('320900000000000105', 'EinvoiceCommon', 'EinvoiceCommon', '通用票开具', 'lucide:file-plus',
     '/finance/einvoice/common', '/finance/einvoice/common/index', 'einvoice-boss.common.issue', '320900000000000100', 5,
     TRUE, FALSE, FALSE, '', 'menu', 'EinvoiceBossCommonAppService · issueCommon P3', NULL,
     TIMESTAMP '2026-06-18 00:00:00+00', TIMESTAMP '2026-06-18 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE)
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
    ('320900000000000011', 'EinvoiceBoss', 'R', '1', NULL, TIMESTAMP '2026-06-18 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('320900000000000012', 'einvoice-boss.compensation.view', 'R', '1', NULL, TIMESTAMP '2026-06-18 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('320900000000000013', 'einvoice-boss.compensation.operate', 'R', '1', NULL, TIMESTAMP '2026-06-18 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('320900000000000014', 'einvoice-boss.document.view', 'R', '1', NULL, TIMESTAMP '2026-06-18 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('320900000000000015', 'einvoice-boss.document.adjust', 'R', '1', NULL, TIMESTAMP '2026-06-18 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('320900000000000016', 'einvoice-boss.cashier.qr', 'R', '1', NULL, TIMESTAMP '2026-06-18 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('320900000000000021', 'einvoice-boss.compensation.view', 'R', '14', NULL, TIMESTAMP '2026-06-18 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('320900000000000022', 'einvoice-boss.compensation.operate', 'R', '14', NULL, TIMESTAMP '2026-06-18 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('320900000000000023', 'einvoice-boss.document.view', 'R', '14', NULL, TIMESTAMP '2026-06-18 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('320900000000000024', 'einvoice-boss.contrast.view', 'R', '1', NULL, TIMESTAMP '2026-06-18 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('320900000000000025', 'einvoice-boss.contrast.manage', 'R', '1', NULL, TIMESTAMP '2026-06-18 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('320900000000000026', 'einvoice-boss.contrast.view', 'R', '14', NULL, TIMESTAMP '2026-06-18 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('320900000000000027', 'einvoice-boss.contrast.manage', 'R', '14', NULL, TIMESTAMP '2026-06-18 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('320900000000000028', 'einvoice-boss.reconcile.view', 'R', '1', NULL, TIMESTAMP '2026-06-18 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('320900000000000029', 'einvoice-boss.reconcile.view', 'R', '14', NULL, TIMESTAMP '2026-06-18 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('320900000000000030', 'einvoice-boss.common.issue', 'R', '1', NULL, TIMESTAMP '2026-06-18 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('320900000000000031', 'einvoice-boss.account.view', 'R', '1', NULL, TIMESTAMP '2026-06-18 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('320900000000000032', 'einvoice-boss.common.issue', 'R', '14', NULL, TIMESTAMP '2026-06-18 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('320900000000000033', 'einvoice-boss.account.view', 'R', '14', NULL, TIMESTAMP '2026-06-18 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('320900000000000034', 'EinvoiceBoss', 'R', '14', NULL, TIMESTAMP '2026-06-18 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('320900000000000035', 'einvoice-boss.document.adjust', 'R', '14', NULL, TIMESTAMP '2026-06-18 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('320900000000000036', 'einvoice-boss.cashier.qr', 'R', '14', NULL, TIMESTAMP '2026-06-18 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL)
ON CONFLICT (permission_name, provider_name, provider_key) DO UPDATE SET
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = EXCLUDED.created_at;

-- 电子发票提升为一级目录（系统不支持三级菜单）
UPDATE whale_menus SET parent_id = NULL, sort_order = 14, is_hidden = FALSE, is_enabled = TRUE,
    is_deleted = FALSE, deleted_at = NULL, deleted_by = NULL, updated_at = NOW()
WHERE id = '320900000000000100' AND code = 'EinvoiceBossHub' AND is_deleted = FALSE;
