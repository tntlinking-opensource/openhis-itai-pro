-- Dev seed: 小程序 BFF 联调（医护工号 / PEIS 套餐 / 团检名册）
-- 联调契约见 whale-doc/whale-module-miniapp/02-小程序API对照表.md §6

-- --- adm_practitioner 工号（bindPractitioner 按 bus_no 查档）---
UPDATE "adm_practitioner" SET "bus_no" = 'PR0001', "updated_at" = now()
WHERE "id" = 308791351298756608 AND ("bus_no" IS NULL OR "bus_no" = '');

UPDATE "adm_practitioner" SET "bus_no" = 'PR0002', "updated_at" = now()
WHERE "id" = 322963602326687744 AND ("bus_no" IS NULL OR "bus_no" = '');

UPDATE "adm_practitioner" SET "bus_no" = 'PR0003', "updated_at" = now()
WHERE "id" = 322965022887120896 AND ("bus_no" IS NULL OR "bus_no" = '');

-- --- PEIS 体检小项（category=PEIS）---
INSERT INTO "wkf_activity_definition" (
    "id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by",
    "is_deleted", "version", "category_code", "bus_no", "name",
    "package_unit_code", "type_enum", "status_enum", "children_flag"
) VALUES
(920000000000000011, 1, now(), now(), 1, 1, false, 0, 'PEIS', 'PEIS-ITEM-01', '一般检查', '6', '1', 'ACTIVE', false),
(920000000000000012, 1, now(), now(), 1, 1, false, 0, 'PEIS', 'PEIS-ITEM-02', '血常规', '6', '1', 'ACTIVE', false)
ON CONFLICT ("id") DO NOTHING;

-- --- PEIS 体检套餐 ---
INSERT INTO "wkf_activity_definition" (
    "id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by",
    "is_deleted", "version", "category_code", "bus_no", "name",
    "package_unit_code", "type_enum", "status_enum", "children_flag"
) VALUES
(920000000000000001, 1, now(), now(), 1, 1, false, 0, 'PEIS', 'PEIS-PKG-A', '教职工基础套餐', '6', '1', 'ACTIVE', true),
(920000000000000002, 1, now(), now(), 1, 1, false, 0, 'PEIS', 'PEIS-PKG-B', '教职工升级套餐', '6', '1', 'ACTIVE', true)
ON CONFLICT ("id") DO NOTHING;

INSERT INTO "wkf_activity_definition_children" (
    "id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by",
    "is_deleted", "version", "activity_id", "children_id", "children_num", "children_name"
) VALUES
(920000000000000021, 1, now(), now(), 1, 1, false, 0, 920000000000000001, 920000000000000011, 1, '一般检查'),
(920000000000000022, 1, now(), now(), 1, 1, false, 0, 920000000000000001, 920000000000000012, 1, '血常规'),
(920000000000000023, 1, now(), now(), 1, 1, false, 0, 920000000000000002, 920000000000000011, 1, '一般检查'),
(920000000000000024, 1, now(), now(), 1, 1, false, 0, 920000000000000002, 920000000000000012, 1, '血常规')
ON CONFLICT ("id") DO NOTHING;

-- --- 团检单位 / 合同 / 分组 / 名册（patient=李先生 325580322788872192）---
INSERT INTO peis_group_company (
    id, tenant_id, parent_id, name, created_at, is_deleted, version
) VALUES (
    920000000000000101, 1, NULL, '东北师范大学（联调）', now(), false, 0
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO peis_group_contract (
    id, tenant_id, company_id, package_activity_id, contract_name,
    valid_from, valid_to, pay_status_code, created_at, is_deleted, version
) VALUES (
    920000000000000201, 1, 920000000000000101, 920000000000000001,
    '2026春季教职工团检合同', DATE '2026-01-01', DATE '2026-12-31',
    'PAID', now(), false, 0
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO peis_group_contract_package (
    id, tenant_id, contract_id, package_activity_id, sort_order, created_at, is_deleted, version
) VALUES
(920000000000000211, 1, 920000000000000201, 920000000000000001, 0, now(), false, 0),
(920000000000000212, 1, 920000000000000201, 920000000000000002, 1, now(), false, 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO peis_group_batch (
    id, tenant_id, company_id, contract_id, batch_name, package_activity_id,
    booking_date, status_code, created_at, is_deleted, version
) VALUES (
    920000000000000301, 1, 920000000000000101, 920000000000000201,
    '2026春季教职工团检', 920000000000000001, DATE '2026-09-01', 'DRAFT',
    now(), false, 0
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO peis_group_batch_package (
    id, tenant_id, batch_id, package_activity_id, sort_order, created_at, is_deleted, version
) VALUES
(920000000000000311, 1, 920000000000000301, 920000000000000001, 0, now(), false, 0),
(920000000000000312, 1, 920000000000000301, 920000000000000002, 1, now(), false, 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO peis_group_roster (
    id, tenant_id, batch_id, company_id, patient_id, full_name, student_no,
    pay_status_code, arrival_status_code, created_at, is_deleted, version
) VALUES (
    920000000000000401, 1, 920000000000000301, 920000000000000101,
    325580322788872192, '李先生', 'DEV2026001',
    'UNPAID', 'PENDING', now(), false, 0
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO peis_group_roster_package (
    id, tenant_id, roster_id, package_activity_id, sort_order, created_at, is_deleted, version
) VALUES
(920000000000000411, 1, 920000000000000401, 920000000000000001, 0, now(), false, 0),
(920000000000000412, 1, 920000000000000401, 920000000000000002, 1, now(), false, 0)
ON CONFLICT (id) DO NOTHING;
