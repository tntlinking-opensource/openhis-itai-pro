-- Campus ModularFlyway V2 · platform seed 最终态
-- 含：东北师大校区主数据 + 校医院菜单/权限（原 V3 合入）
-- history: flyway_schema_history_campus
INSERT INTO "public"."cmp_campus_site" (
    "id",
    "tenant_id",
    "created_at",
    "is_deleted",
    "version",
    "campus_code",
    "campus_name",
    "short_name",
    "is_primary",
    "active_flag",
    "sort_order"
) VALUES
(
    910000000000000001,
    1,
    now(),
    false,
    0,
    'BENBU',
    '自由校区',
    '自由校区',
    true,
    true,
    0
),
(
    910000000000000002,
    1,
    now(),
    false,
    0,
    'JINGYUE',
    '净月校区',
    '净月校区',
    false,
    true,
    1
)
ON CONFLICT ("id") DO NOTHING;

-- Campus 校医院菜单（组织 / 档案 / 公费）
-- 权限挂角色 provider_key=1（与 PEIS 种子一致）
-- ID 前缀 3121…，避免与 PEIS 3111… 冲突

INSERT INTO zxplat_permissions (
    id, name, display_name, parent_name, description,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('312100000000000001', 'Campus', '校医院', NULL,
     'zxplat-module-campus 模块入口权限分组', NULL,
     TIMESTAMP '2026-07-19 00:00:00+00', TIMESTAMP '2026-07-19 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('312100000000000002', 'Campus.Site.View', '校区管理', 'Campus',
     '菜单入口: CampusSiteManage', NULL,
     TIMESTAMP '2026-07-19 00:00:00+00', TIMESTAMP '2026-07-19 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('312100000000000003', 'Campus.College.View', '学院管理', 'Campus',
     '菜单入口: CampusCollegeManage', NULL,
     TIMESTAMP '2026-07-19 00:00:00+00', TIMESTAMP '2026-07-19 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('312100000000000004', 'Campus.Department.View', '部门管理', 'Campus',
     '菜单入口: CampusDepartmentManage', NULL,
     TIMESTAMP '2026-07-19 00:00:00+00', TIMESTAMP '2026-07-19 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('312100000000000005', 'Campus.Student.View', '学生档案', 'Campus',
     '菜单入口: CampusStudentManage', NULL,
     TIMESTAMP '2026-07-19 00:00:00+00', TIMESTAMP '2026-07-19 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('312100000000000006', 'Campus.Staff.View', '教职工档案', 'Campus',
     '菜单入口: CampusStaffManage', NULL,
     TIMESTAMP '2026-07-19 00:00:00+00', TIMESTAMP '2026-07-19 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('312100000000000007', 'Campus.GfConfig.View', '公费配置', 'Campus',
     '菜单入口: CampusGfConfig', NULL,
     TIMESTAMP '2026-07-19 00:00:00+00', TIMESTAMP '2026-07-19 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('312100000000000008', 'Campus.GfRatioCatalog.View', '目录比例', 'Campus',
     '菜单入口: CampusGfRatioCatalog', NULL,
     TIMESTAMP '2026-07-19 00:00:00+00', TIMESTAMP '2026-07-19 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('312100000000000009', 'Campus.GfRatioItem.View', '项目比例', 'Campus',
     '菜单入口: CampusGfRatioItem', NULL,
     TIMESTAMP '2026-07-19 00:00:00+00', TIMESTAMP '2026-07-19 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('312100000000000010', 'Campus.GfApproval.View', '比例审批', 'Campus',
     '菜单入口: CampusGfApprovalTodo', NULL,
     TIMESTAMP '2026-07-19 00:00:00+00', TIMESTAMP '2026-07-19 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('312100000000000011', 'Campus.GfRecord.View', '申请记录', 'Campus',
     '菜单入口: CampusGfRecord', NULL,
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

INSERT INTO zxplat_menus (
    id, code, name, display_name, icon, path, component, permission_name, parent_id,
    sort_order, is_enabled, is_hidden, is_external, external_url, menu_type, remark,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, keep_alive
)
VALUES
    ('312100000000000100', 'CampusHub', 'CampusHub', '校医院', 'lucide:graduation-cap',
     '/campus', '', NULL, NULL, 13, TRUE, FALSE, FALSE, '', 'directory',
     'Campus 组织/档案/公费菜单根', NULL,
     TIMESTAMP '2026-07-19 00:00:00+00', TIMESTAMP '2026-07-19 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE),
    ('312100000000000101', 'CampusSiteManage', 'CampusSiteManage', '校区管理', 'lucide:map-pin',
     '/campus/org/campus', '/campus/org/campus/index', 'Campus.Site.View', '312100000000000100', 1,
     TRUE, FALSE, FALSE, '', 'menu', 'CampusSiteAppService · ORG-CMP', NULL,
     TIMESTAMP '2026-07-19 00:00:00+00', TIMESTAMP '2026-07-19 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE),
    ('312100000000000102', 'CampusCollegeManage', 'CampusCollegeManage', '学院管理', 'lucide:building-2',
     '/campus/org/college', '/campus/org/college/index', 'Campus.College.View', '312100000000000100', 2,
     TRUE, FALSE, FALSE, '', 'menu', 'CampusCollegeAppService · ORG-COL', NULL,
     TIMESTAMP '2026-07-19 00:00:00+00', TIMESTAMP '2026-07-19 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE),
    ('312100000000000103', 'CampusDepartmentManage', 'CampusDepartmentManage', '部门管理', 'lucide:users',
     '/campus/org/department', '/campus/org/department/index', 'Campus.Department.View', '312100000000000100', 3,
     TRUE, FALSE, FALSE, '', 'menu', 'CampusDepartmentAppService · ORG-DEP', NULL,
     TIMESTAMP '2026-07-19 00:00:00+00', TIMESTAMP '2026-07-19 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE),
    ('312100000000000104', 'CampusStudentManage', 'CampusStudentManage', '学生档案', 'lucide:user-round',
     '/campus/student', '/campus/student/index', 'Campus.Student.View', '312100000000000100', 4,
     TRUE, FALSE, FALSE, '', 'menu', 'CampusStudentAppService · STU', NULL,
     TIMESTAMP '2026-07-19 00:00:00+00', TIMESTAMP '2026-07-19 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE),
    ('312100000000000105', 'CampusStaffManage', 'CampusStaffManage', '教职工档案', 'lucide:badge-check',
     '/campus/staff', '/campus/staff/index', 'Campus.Staff.View', '312100000000000100', 5,
     TRUE, FALSE, FALSE, '', 'menu', 'CampusStaffAppService · STF', NULL,
     TIMESTAMP '2026-07-19 00:00:00+00', TIMESTAMP '2026-07-19 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE),
    ('312100000000000106', 'CampusGfConfig', 'CampusGfConfig', '公费配置', 'lucide:settings',
     '/campus/gf/config', '/campus/gf/config/index', 'Campus.GfConfig.View', '312100000000000100', 6,
     TRUE, FALSE, FALSE, '', 'menu', 'CampusGfConfigAppService · GF-CFG', NULL,
     TIMESTAMP '2026-07-19 00:00:00+00', TIMESTAMP '2026-07-19 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE),
    ('312100000000000107', 'CampusGfRatioCatalog', 'CampusGfRatioCatalog', '目录比例', 'lucide:folder-tree',
     '/campus/gf/ratio-catalog', '/campus/gf/ratio-catalog/index', 'Campus.GfRatioCatalog.View', '312100000000000100', 7,
     TRUE, FALSE, FALSE, '', 'menu', 'CampusGfRatioCatalogAppService · GF-RATIO', NULL,
     TIMESTAMP '2026-07-19 00:00:00+00', TIMESTAMP '2026-07-19 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE),
    ('312100000000000108', 'CampusGfRatioItem', 'CampusGfRatioItem', '项目比例', 'lucide:list-tree',
     '/campus/gf/ratio-item', '/campus/gf/ratio-item/index', 'Campus.GfRatioItem.View', '312100000000000100', 8,
     TRUE, FALSE, FALSE, '', 'menu', 'CampusGfRatioItemAppService · GF-RATIO', NULL,
     TIMESTAMP '2026-07-19 00:00:00+00', TIMESTAMP '2026-07-19 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE),
    ('312100000000000109', 'CampusGfApprovalTodo', 'CampusGfApprovalTodo', '比例审批', 'lucide:clipboard-check',
     '/campus/gf/todo', '/campus/gf/todo/index', 'Campus.GfApproval.View', '312100000000000100', 9,
     TRUE, FALSE, FALSE, '', 'menu', 'CampusGfApprovalAppService · GF-APR', NULL,
     TIMESTAMP '2026-07-19 00:00:00+00', TIMESTAMP '2026-07-19 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE),
    ('312100000000000110', 'CampusGfRecord', 'CampusGfRecord', '申请记录', 'lucide:file-text',
     '/campus/gf/record', '/campus/gf/record/index', 'Campus.GfRecord.View', '312100000000000100', 10,
     TRUE, FALSE, FALSE, '', 'menu', 'CampusGfRequestRecordAppService · GF-RATIO', NULL,
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

INSERT INTO zxplat_permission_grants (
    id, permission_name, provider_name, provider_key,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('312100000000000201', 'Campus', 'R', '1', NULL, TIMESTAMP '2026-07-19 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('312100000000000202', 'Campus.Site.View', 'R', '1', NULL, TIMESTAMP '2026-07-19 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('312100000000000203', 'Campus.College.View', 'R', '1', NULL, TIMESTAMP '2026-07-19 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('312100000000000204', 'Campus.Department.View', 'R', '1', NULL, TIMESTAMP '2026-07-19 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('312100000000000205', 'Campus.Student.View', 'R', '1', NULL, TIMESTAMP '2026-07-19 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('312100000000000206', 'Campus.Staff.View', 'R', '1', NULL, TIMESTAMP '2026-07-19 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('312100000000000207', 'Campus.GfConfig.View', 'R', '1', NULL, TIMESTAMP '2026-07-19 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('312100000000000208', 'Campus.GfRatioCatalog.View', 'R', '1', NULL, TIMESTAMP '2026-07-19 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('312100000000000209', 'Campus.GfRatioItem.View', 'R', '1', NULL, TIMESTAMP '2026-07-19 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('312100000000000210', 'Campus.GfApproval.View', 'R', '1', NULL, TIMESTAMP '2026-07-19 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('312100000000000211', 'Campus.GfRecord.View', 'R', '1', NULL, TIMESTAMP '2026-07-19 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET
    permission_name = EXCLUDED.permission_name,
    provider_name = EXCLUDED.provider_name,
    provider_key = EXCLUDED.provider_key,
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = EXCLUDED.updated_at;
