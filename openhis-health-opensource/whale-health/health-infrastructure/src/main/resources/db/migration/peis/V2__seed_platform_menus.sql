-- PEIS ModularFlyway V2 · platform seeds（menus / perms / ter_concept / print template）
-- history: flyway_schema_history_peis
-- folded: V2–V13 seed content（dependency-safe order；菜单 sort_order 取终态）
-- 权限挂角色 provider_key=1（与 Booking 种子一致）
-- 终态菜单序：登记(0) → 收费(1) → 目录(2) → 诊断(3) → 参数(4) / 公告(4) → 团检(5) → 分科(6) → 总检(7) → 报告打印(8) → 报告发放(9) → 复查(10) → 体检历史(11)

-- ========== from V2__seed_platform_menus.sql（Catalog/Diagnosis/Settings sort_order 已含 V5 终态） ==========

INSERT INTO whale_permissions (
    id, name, display_name, parent_name, description,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('311100000000000001', 'Peis', '健康体检', NULL,
     'whale-health PEIS 模块入口权限分组', NULL,
     TIMESTAMP '2026-07-19 00:00:00+00', TIMESTAMP '2026-07-19 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('311100000000000002', 'Peis.Catalog.View', '体检目录', 'Peis',
     '菜单入口: PeisCatalogConfig', NULL,
     TIMESTAMP '2026-07-19 00:00:00+00', TIMESTAMP '2026-07-19 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('311100000000000003', 'Peis.Diagnosis.View', '诊断与表达式', 'Peis',
     '菜单入口: PeisDiagnosisConfig', NULL,
     TIMESTAMP '2026-07-19 00:00:00+00', TIMESTAMP '2026-07-19 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('311100000000000004', 'Peis.Settings.View', '系统参数', 'Peis',
     '菜单入口: PeisModuleSettings', NULL,
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
    ('311100000000000100', 'PeisHub', 'PeisHub', '健康体检', 'lucide:heart-pulse',
     '/peis', '', NULL, NULL, 12, TRUE, FALSE, FALSE, '', 'directory',
     '体检配置与后续业务菜单根', NULL,
     TIMESTAMP '2026-07-19 00:00:00+00', TIMESTAMP '2026-07-19 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE),
    ('311100000000000101', 'PeisCatalogConfig', 'PeisCatalogConfig', '体检目录', 'lucide:folder-tree',
     '/peis/config/catalog', '/peis/config/catalog/index', 'Peis.Catalog.View', '311100000000000100', 2,
     TRUE, FALSE, FALSE, '', 'menu', 'PeisCatalogConfigAppService · AS-PEIS-CFG-001', NULL,
     TIMESTAMP '2026-07-19 00:00:00+00', TIMESTAMP '2026-07-19 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE),
    ('311100000000000102', 'PeisDiagnosisConfig', 'PeisDiagnosisConfig', '诊断与表达式', 'lucide:stethoscope',
     '/peis/config/diagnosis', '/peis/config/diagnosis/index', 'Peis.Diagnosis.View', '311100000000000100', 3,
     TRUE, FALSE, FALSE, '', 'menu', 'PeisDiagnosisConfigAppService · AS-PEIS-CFG-002 P0', NULL,
     TIMESTAMP '2026-07-19 00:00:00+00', TIMESTAMP '2026-07-19 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE),
    ('311100000000000103', 'PeisModuleSettings', 'PeisModuleSettings', '系统参数', 'lucide:settings-2',
     '/peis/config/settings', '/peis/config/settings/index', 'Peis.Settings.View', '311100000000000100', 4,
     TRUE, FALSE, FALSE, '', 'menu', 'PeisModuleSettingsAppService · AS-PEIS-CFG-003', NULL,
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
    ('311100000000000201', 'Peis', 'R', '1', NULL, TIMESTAMP '2026-07-19 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('311100000000000202', 'Peis.Catalog.View', 'R', '1', NULL, TIMESTAMP '2026-07-19 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('311100000000000203', 'Peis.Diagnosis.View', 'R', '1', NULL, TIMESTAMP '2026-07-19 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('311100000000000204', 'Peis.Settings.View', 'R', '1', NULL, TIMESTAMP '2026-07-19 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL)
ON CONFLICT (permission_name, provider_name, provider_key) DO UPDATE SET
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = TIMESTAMP '2026-07-19 00:00:00+00';

-- ========== from V3__seed_peis_dx_source.sql ==========
-- 体检诊断目录来源：condition_definition_source / peis_dx
-- 与院内诊断 hospital_dx 隔离，不纳入医生站西医检索默认集合

INSERT INTO "ter_concept" (
    "id", "code_system_id", "code", "display", "definition", "parent_id", "sort_order",
    "is_active", "css_class", "list_class", "is_default", "tenant_id",
    "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by"
)
VALUES
(
    '900006034',
    '279125561775755266',
    'peis_dx',
    '体检诊断',
    'PEIS 体检诊断建议库（不进医生站开诊断候选）',
    NULL,
    '10',
    true,
    NULL,
    'default',
    NULL,
    '1',
    TIMESTAMP '2026-07-19 00:00:00+00',
    TIMESTAMP '2026-07-19 00:00:00+00',
    '1',
    '1',
    false,
    NULL,
    NULL
)
ON CONFLICT ("id") DO UPDATE SET
    code = EXCLUDED.code,
    display = EXCLUDED.display,
    definition = EXCLUDED.definition,
    sort_order = EXCLUDED.sort_order,
    is_active = TRUE,
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = EXCLUDED.updated_at;

-- ========== from V4__seed_personal_registration_menu.sql ==========
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

-- ========== from V5__seed_physical_exam_charge_menu.sql ==========
-- PEIS 体检收费菜单（health · AS-PE-FIN-001，挂 PeisHub；sort_order=1，紧随个检登记）
-- 权限挂角色 provider_key=1（与 V2/V4 种子一致）

INSERT INTO whale_permissions (
    id, name, display_name, parent_name, description,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('311100000000000006', 'Peis.Charge.View', '体检收费', 'Peis',
     '菜单入口: PhysicalExamCharge · AS-PE-FIN-001', NULL,
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

-- 目录/诊断/参数 sort_order 终态已写入上方 V2 菜单种子（登记0/收费1/目录2/诊断3/参数4）

INSERT INTO whale_menus (
    id, code, name, display_name, icon, path, component, permission_name, parent_id,
    sort_order, is_enabled, is_hidden, is_external, external_url, menu_type, remark,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, keep_alive
)
VALUES
    ('311100000000000106', 'PhysicalExamCharge', 'PhysicalExamCharge', '体检收费', 'lucide:wallet',
     '/peis/charge', '/peis/charge/index', 'Peis.Charge.View', '311100000000000100', 1,
     TRUE, FALSE, FALSE, '', 'menu', 'PhysicalExamChargeAppService · AS-PE-FIN-001', NULL,
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
    ('311100000000000206', 'Peis.Charge.View', 'R', '1', NULL,
     TIMESTAMP '2026-07-19 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL)
ON CONFLICT (permission_name, provider_name, provider_key) DO UPDATE SET
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = TIMESTAMP '2026-07-19 00:00:00+00';

-- ========== from V6__peis_group_check_and_menu.sql (menu seeds only) ==========
INSERT INTO whale_permissions (
    id, name, display_name, parent_name, description,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('311100000000000007', 'Peis.GroupCheck.View', '团检管理', 'Peis',
     '菜单入口: PeisGroupCheckAdmin · AS-PEIS-GRP-001', NULL,
     TIMESTAMP '2026-07-19 00:00:00+00', TIMESTAMP '2026-07-19 00:00:00+00', 1, 1, FALSE, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    display_name = EXCLUDED.display_name,
    is_deleted = FALSE,
    updated_at = EXCLUDED.updated_at;

INSERT INTO whale_menus (
    id, code, name, display_name, icon, path, component, permission_name, parent_id,
    sort_order, is_enabled, is_hidden, is_external, external_url, menu_type, remark,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, keep_alive
)
VALUES
    ('311100000000000107', 'PeisGroupCheck', 'PeisGroupCheck', '团检管理', 'lucide:users',
     '/peis/group-check', '/peis/group-check/index', 'Peis.GroupCheck.View', '311100000000000100', 5,
     TRUE, FALSE, FALSE, '', 'menu', 'PeisGroupCheckAdminAppService · AS-PEIS-GRP-001', NULL,
     TIMESTAMP '2026-07-19 00:00:00+00', TIMESTAMP '2026-07-19 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE)
ON CONFLICT (id) DO UPDATE SET
    path = EXCLUDED.path,
    component = EXCLUDED.component,
    is_deleted = FALSE,
    updated_at = EXCLUDED.updated_at;

INSERT INTO whale_permission_grants (
    id, permission_name, provider_name, provider_key,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('311100000000000207', 'Peis.GroupCheck.View', 'R', '1', NULL,
     TIMESTAMP '2026-07-19 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL)
ON CONFLICT (permission_name, provider_name, provider_key) DO UPDATE SET
    is_deleted = FALSE,
    updated_at = TIMESTAMP '2026-07-19 00:00:00+00';

-- ========== from V8__seed_dept_exam_desk_menu.sql ==========
-- PEIS 分科诊台菜单（AS-PEIS-DES-001，挂 PeisHub）
-- 权限挂角色 provider_key=1（与 V2/V4 种子一致）

INSERT INTO whale_permissions (
    id, name, display_name, parent_name, description,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('311100000000000008', 'Peis.DeptDesk.View', '分科诊台', 'Peis',
     '菜单入口: PeisDeptExamDesk · AS-PEIS-DES-001', NULL,
     TIMESTAMP '2026-07-21 00:00:00+00', TIMESTAMP '2026-07-21 00:00:00+00', 1, 1, FALSE, NULL, NULL)
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
    ('311100000000000108', 'PeisDeptExamDesk', 'PeisDeptExamDesk', '分科诊台', 'lucide:stethoscope',
     '/peis/dept-desk', '/peis/dept-desk/index', 'Peis.DeptDesk.View', '311100000000000100', 6,
     TRUE, FALSE, FALSE, '', 'menu', 'PeisDeptExamDeskAppService · AS-PEIS-DES-001', NULL,
     TIMESTAMP '2026-07-21 00:00:00+00', TIMESTAMP '2026-07-21 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE)
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
    ('311100000000000208', 'Peis.DeptDesk.View', 'R', '1', NULL,
     TIMESTAMP '2026-07-21 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL)
ON CONFLICT (permission_name, provider_name, provider_key) DO UPDATE SET
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = TIMESTAMP '2026-07-21 00:00:00+00';

-- ========== from V9__seed_master_review_menu.sql ==========
-- PEIS 总检诊台菜单（AS-PEIS-MAS-001，挂 PeisHub）
-- 权限挂角色 provider_key=1（与 V2/V4/V8 种子一致）

INSERT INTO whale_permissions (
    id, name, display_name, parent_name, description,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('311100000000000009', 'Peis.MasterReview.View', '总检诊台', 'Peis',
     '菜单入口: PeisMasterReviewDesk · AS-PEIS-MAS-001', NULL,
     TIMESTAMP '2026-07-21 00:00:00+00', TIMESTAMP '2026-07-21 00:00:00+00', 1, 1, FALSE, NULL, NULL)
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
    ('311100000000000109', 'PeisMasterReviewDesk', 'PeisMasterReviewDesk', '总检诊台', 'lucide:clipboard-check',
     '/peis/master-review', '/peis/master-review/index', 'Peis.MasterReview.View', '311100000000000100', 7,
     TRUE, FALSE, FALSE, '', 'menu', 'PeisMasterReviewDeskAppService · AS-PEIS-MAS-001', NULL,
     TIMESTAMP '2026-07-21 00:00:00+00', TIMESTAMP '2026-07-21 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE)
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
    ('311100000000000209', 'Peis.MasterReview.View', 'R', '1', NULL,
     TIMESTAMP '2026-07-21 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL)
ON CONFLICT (permission_name, provider_name, provider_key) DO UPDATE SET
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = TIMESTAMP '2026-07-21 00:00:00+00';

-- ========== from V10__seed_report_print_grant_recheck_menus.sql (menu seeds only) ==========
-- 报告打印 menu id 010/110/210
INSERT INTO whale_permissions (
    id, name, display_name, parent_name, description,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('311100000000000010', 'Peis.ReportPrint.View', '报告打印', 'Peis',
     '菜单入口: PeisReportPrint · AS-PEIS-REP-001', NULL,
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
    ('311100000000000110', 'PeisReportPrint', 'PeisReportPrint', '报告打印', 'lucide:printer',
     '/peis/report/print', '/peis/report/print/index', 'Peis.ReportPrint.View', '311100000000000100', 8,
     TRUE, FALSE, FALSE, '', 'menu', 'PeisReportPrintAppService · AS-PEIS-REP-001', NULL,
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
    ('311100000000000210', 'Peis.ReportPrint.View', 'R', '1', NULL,
     TIMESTAMP '2026-07-25 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL)
ON CONFLICT (permission_name, provider_name, provider_key) DO UPDATE SET
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = TIMESTAMP '2026-07-25 00:00:00+00';

-- 报告发放 menu id 011/111/211
INSERT INTO whale_permissions (
    id, name, display_name, parent_name, description,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('311100000000000011', 'Peis.ReportGrant.View', '报告发放', 'Peis',
     '菜单入口: PeisReportGrant · AS-PEIS-REP-002', NULL,
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
    ('311100000000000111', 'PeisReportGrant', 'PeisReportGrant', '报告发放', 'lucide:package-check',
     '/peis/report/grant', '/peis/report/grant/index', 'Peis.ReportGrant.View', '311100000000000100', 9,
     TRUE, FALSE, FALSE, '', 'menu', 'PeisReportGrantAppService · AS-PEIS-REP-002', NULL,
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
    ('311100000000000211', 'Peis.ReportGrant.View', 'R', '1', NULL,
     TIMESTAMP '2026-07-25 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL)
ON CONFLICT (permission_name, provider_name, provider_key) DO UPDATE SET
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = TIMESTAMP '2026-07-25 00:00:00+00';

-- 复查管理 menu id 012/112/212
INSERT INTO whale_permissions (
    id, name, display_name, parent_name, description,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('311100000000000012', 'Peis.Recheck.View', '复查管理', 'Peis',
     '菜单入口: PeisRecheckManage · AS-PEIS-REC-001', NULL,
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
    ('311100000000000112', 'PeisRecheckManage', 'PeisRecheckManage', '复查管理', 'lucide:refresh-cw',
     '/peis/recheck', '/peis/recheck/index', 'Peis.Recheck.View', '311100000000000100', 10,
     TRUE, FALSE, FALSE, '', 'menu', 'PeisRecheckManageAppService · AS-PEIS-REC-001', NULL,
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
    ('311100000000000212', 'Peis.Recheck.View', 'R', '1', NULL,
     TIMESTAMP '2026-07-25 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL)
ON CONFLICT (permission_name, provider_name, provider_key) DO UPDATE SET
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = TIMESTAMP '2026-07-25 00:00:00+00';

-- ========== from V11__seed_peis_report_print_template.sql ==========
-- PEIS 体检总检报告打印模板（scenario_code = peis-report-print）
INSERT INTO "public"."prt_template" (
    "id", "tenant_id", "organization_id", "bus_no", "name", "status", "version",
    "print_json", "schema_version", "sample_print_data",
    "created_at", "updated_at", "created_by", "updated_by",
    "is_deleted", "deleted_at", "deleted_by", "scenario_code"
) VALUES (
    329000000000000001,
    1,
    NULL,
    'PT202607250001',
    '体检总检报告',
    'published',
    1,
    '{"schemaVersion":1,"engine":"Print","panels":[{"name":"1","index":0,"width":210,"height":297,"paperType":"A4","paperFooter":575.4,"paperHeader":0,"printElements":[{"options":{"top":16,"left":28.3,"field":"hospitalName","width":539.4,"height":24,"fontSize":16,"hideTitle":true,"textAlign":"center","fontWeight":"bold","testData":"示例医院"},"printElementType":{"type":"text","title":"文本"}},{"options":{"top":44,"left":28.3,"title":"体检总检报告","width":539.4,"height":18,"fontSize":13,"textAlign":"center","fontWeight":"bold"},"printElementType":{"type":"text","title":"文本"}},{"options":{"top":72,"left":28.3,"field":"patientName","title":"姓名","width":120,"height":16,"fontSize":10,"textAlign":"left"},"printElementType":{"type":"text","title":"姓名"}},{"options":{"top":72,"left":160,"field":"genderEnumText","title":"性别","width":80,"height":16,"fontSize":10,"textAlign":"left"},"printElementType":{"type":"text","title":"性别"}},{"options":{"top":72,"left":252,"field":"ageText","title":"年龄","width":80,"height":16,"fontSize":10,"textAlign":"left"},"printElementType":{"type":"text","title":"年龄"}},{"options":{"top":92,"left":28.3,"field":"busNo","title":"流水号","width":200,"height":16,"fontSize":10,"textAlign":"left"},"printElementType":{"type":"text","title":"流水号"}},{"options":{"top":92,"left":240,"field":"checkDateText","title":"定稿时间","width":200,"height":16,"fontSize":10,"textAlign":"left"},"printElementType":{"type":"text","title":"定稿时间"}},{"options":{"top":116,"left":28.3,"width":539.4,"height":1,"borderWidth":1},"printElementType":{"type":"hline","title":"横线"}},{"options":{"top":124,"left":28.3,"title":"综述","width":539.4,"height":14,"fontSize":10,"fontWeight":"bold","textAlign":"left"},"printElementType":{"type":"text","title":"文本"}},{"options":{"top":140,"left":28.3,"field":"summaryText","width":539.4,"height":60,"fontSize":10,"textAlign":"left","lineHeight":14},"printElementType":{"type":"longText","title":"长文"}},{"options":{"top":208,"left":28.3,"title":"结论","width":539.4,"height":14,"fontSize":10,"fontWeight":"bold","textAlign":"left"},"printElementType":{"type":"text","title":"文本"}},{"options":{"top":224,"left":28.3,"field":"conclusionText","width":539.4,"height":60,"fontSize":10,"textAlign":"left","lineHeight":14},"printElementType":{"type":"longText","title":"长文"}},{"options":{"top":292,"left":28.3,"title":"检查结果","width":539.4,"height":14,"fontSize":10,"fontWeight":"bold","textAlign":"left"},"printElementType":{"type":"text","title":"文本"}},{"options":{"top":308,"left":28.3,"field":"observationLines","width":539.4,"height":200,"fontSize":9,"textAlign":"center","lineHeight":14,"tableBorder":"border","tableHeaderBorder":"border","tableHeaderRepeat":"first","tableBodyRowBorder":"border","tableBodyRowHeight":20,"tableBodyCellBorder":"border","tableHeaderRowHeight":22,"tableHeaderCellBorder":"border","columns":[[{"title":"项目","field":"itemName","width":160,"colspan":1,"rowspan":1,"align":"left","checked":true,"columnId":"itemName"},{"title":"结果","field":"resultText","width":120,"colspan":1,"rowspan":1,"align":"center","checked":true,"columnId":"resultText"},{"title":"单位","field":"unitText","width":80,"colspan":1,"rowspan":1,"align":"center","checked":true,"columnId":"unitText"},{"title":"参考范围","field":"refRangeText","width":179.4,"colspan":1,"rowspan":1,"align":"center","checked":true,"columnId":"refRangeText"}]]},"printElementType":{"type":"table","title":"表格","editable":true,"columnResizable":true}}],"paperNumberContinue":true,"paperNumberDisabled":true,"watermarkOptions":{"width":200,"format":"YYYY-MM-DD HH:mm","height":200,"rotate":25,"content":"","fontSize":"14px","fillStyle":"rgba(184, 184, 184, 0.3)","timestamp":false},"panelLayoutOptions":{"layoutType":"column","paperStyle":"","paperWidth":0,"paperHeight":0,"layoutRowGap":0,"layoutColumnGap":0}}]}'::jsonb,
    1,
    '{"hospitalName":"示例医院","patientName":"张三","genderEnumText":"男","ageText":"25岁","busNo":"PE202607250001","checkDateText":"2026-07-25 10:30","summaryText":"各科室检查未见明显异常。","conclusionText":"体检合格。","observationLines":[{"itemName":"身高","resultText":"175","unitText":"cm","refRangeText":""},{"itemName":"体重","resultText":"70","unitText":"kg","refRangeText":""},{"itemName":"血压","resultText":"120/80","unitText":"mmHg","refRangeText":""}]}'::jsonb,
    TIMESTAMP '2026-07-25 22:00:00+08',
    TIMESTAMP '2026-07-25 22:00:00+08',
    1,
    1,
    FALSE,
    NULL,
    NULL,
    'peis-report-print'
)
ON CONFLICT ("id") DO UPDATE SET
    "print_json" = EXCLUDED."print_json",
    "sample_print_data" = EXCLUDED."sample_print_data",
    "name" = EXCLUDED."name",
    "bus_no" = EXCLUDED."bus_no",
    "scenario_code" = EXCLUDED."scenario_code",
    "version" = EXCLUDED."version",
    "updated_at" = EXCLUDED."updated_at";

-- ========== from V12__seed_miniapp_home_notice_menu.sql ==========
-- PEIS V12：小程序首页公告管理菜单（内容存 peis_config · peis.miniapp.home_notice）

INSERT INTO whale_permissions (
    id, name, display_name, parent_name, description,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('311100000000000013', 'Peis.Announcement.View', '小程序首页公告', 'Peis',
     '菜单入口: PeisAnnouncement · TipTap 编辑 peis.miniapp.home_notice', NULL,
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
    ('311100000000000113', 'PeisAnnouncement', 'PeisAnnouncement', '小程序首页公告', 'lucide:megaphone',
     '/peis/config/announcement', '/peis/config/announcement/index', 'Peis.Announcement.View', '311100000000000100', 4,
     TRUE, FALSE, FALSE, '', 'menu', 'PeisAnnouncementAppService · 首页公告 WYSIWYG', NULL,
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
    ('311100000000000213', 'Peis.Announcement.View', 'R', '1',
     NULL, TIMESTAMP '2026-07-25 00:00:00+00', TIMESTAMP '2026-07-25 00:00:00+00', 1, 1, FALSE, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET
    permission_name = EXCLUDED.permission_name,
    provider_name = EXCLUDED.provider_name,
    provider_key = EXCLUDED.provider_key,
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = EXCLUDED.updated_at;

-- ========== from V13__seed_exam_history_menu.sql ==========
-- NOTE: original V13 reused ReportGrant ids 011/111/211; greenfield assigns 014/114/214 to keep both menus.
-- PEIS V13：学生/教职工体检历史菜单（AS-PEIS-ARCH-001 扩展 / PeisExamHistory）
-- 权限挂角色 provider_key=1（与既有种子一致）

INSERT INTO whale_permissions (
    id, name, display_name, parent_name, description,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('311100000000000014', 'Peis.ExamHistory.View', '体检历史', 'Peis',
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
    ('311100000000000114', 'PeisExamHistory', 'PeisExamHistory', '体检历史', 'lucide:history',
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
    ('311100000000000214', 'Peis.ExamHistory.View', 'R', '1',
     NULL, TIMESTAMP '2026-07-25 00:00:00+00', TIMESTAMP '2026-07-25 00:00:00+00', 1, 1, FALSE, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET
    permission_name = EXCLUDED.permission_name,
    provider_name = EXCLUDED.provider_name,
    provider_key = EXCLUDED.provider_key,
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = EXCLUDED.updated_at;

