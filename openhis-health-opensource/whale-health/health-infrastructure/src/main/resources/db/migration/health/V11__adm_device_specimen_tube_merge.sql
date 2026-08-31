-- adm_device + cus_specimen_tube_merge + adm_device_category_code terminology + menu seeds

CREATE TABLE IF NOT EXISTS "public"."adm_device" (
    "id" int8 NOT NULL,
    "tenant_id" int8,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    "version" int4 NOT NULL DEFAULT 0,
    "bus_no" varchar(255) NOT NULL,
    "name" varchar(255) NOT NULL,
    "py_str" varchar(255),
    "wb_str" varchar(255),
    "org_id" int8,
    "location_id" int8,
    "status_enum" varchar(255) NOT NULL DEFAULT 'ACTIVE',
    "category_code" varchar(255),
    "model_number" varchar(255),
    "serial_number" varchar(255),
    "manufacturer_id" int8,
    "manufacture_date" date,
    "expiration_date" date,
    "description" varchar(500),
    CONSTRAINT "adm_device_pkey" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX IF NOT EXISTS "uk_adm_device_bus_no"
    ON "public"."adm_device" ("tenant_id", "bus_no")
    WHERE "is_deleted" = false;

CREATE INDEX IF NOT EXISTS "idx_adm_device_org_id"
    ON "public"."adm_device" ("org_id");

CREATE INDEX IF NOT EXISTS "idx_adm_device_status_enum"
    ON "public"."adm_device" ("status_enum");

COMMENT ON TABLE adm_device IS '管理域检验/科室设备台账（合管设备）';
COMMENT ON COLUMN adm_device.bus_no IS '设备业务编码（BusinessSequence 生成）';
COMMENT ON COLUMN adm_device.org_id IS '归属科室（adm_organization.id，可空）';
COMMENT ON COLUMN adm_device.status_enum IS 'PublicationStatus.code';
COMMENT ON COLUMN adm_device.category_code IS '设备分类（术语 adm_device_category_code）';
COMMENT ON COLUMN adm_device.manufacturer_id IS '生产厂家（cus_supplier.id）';

CREATE TABLE IF NOT EXISTS "public"."cus_specimen_tube_merge" (
    "id" int8 NOT NULL,
    "tenant_id" int8,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    "version" int4 NOT NULL DEFAULT 0,
    "device_id" int8 NOT NULL,
    "target_table" varchar(255) NOT NULL,
    "target_id" int8 NOT NULL,
    "quantity" numeric(18,2),
    "unit_code" varchar(255),
    "display_order" int4,
    CONSTRAINT "cus_specimen_tube_merge_pkey" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX IF NOT EXISTS "uk_cus_specimen_tube_merge_target"
    ON "public"."cus_specimen_tube_merge" ("target_table", "target_id")
    WHERE "is_deleted" = false;

CREATE UNIQUE INDEX IF NOT EXISTS "uk_cus_specimen_tube_merge_device_target"
    ON "public"."cus_specimen_tube_merge" ("device_id", "target_table", "target_id")
    WHERE "is_deleted" = false;

CREATE INDEX IF NOT EXISTS "idx_cus_specimen_tube_merge_device_id"
    ON "public"."cus_specimen_tube_merge" ("device_id");

COMMENT ON TABLE cus_specimen_tube_merge IS '标本合管配置（设备绑定检验项目/耗材/采血费 activity）';
COMMENT ON COLUMN cus_specimen_tube_merge.device_id IS 'adm_device.id';
COMMENT ON COLUMN cus_specimen_tube_merge.target_table IS 'wkf_activity_definition 或 adm_device_definition';
COMMENT ON COLUMN cus_specimen_tube_merge.target_id IS '目标主键';

-- terminology: adm_device_category_code
INSERT INTO "ter_code_system" (
    "id", "name", "title", "description", "version", "status", "tenant_id",
    "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by"
) VALUES (
    '328000000000000901', 'adm_device_category_code', '检验设备分类', NULL, '1.0', '2', '1',
    '2026-08-22 00:00:00.000+00', NULL, '1', '1', false, NULL, NULL
) ON CONFLICT ("id") DO NOTHING;

INSERT INTO "ter_concept" (
    "id", "code_system_id", "code", "display", "definition", "parent_id", "sort_order", "is_active",
    "css_class", "list_class", "is_default", "tenant_id", "created_at", "updated_at", "created_by", "updated_by",
    "is_deleted", "deleted_at", "deleted_by"
) VALUES
    ('328000000000000911', '328000000000000901', 'BLOOD_COLLECTION', '采血', NULL, NULL, '1', true,
     NULL, 'default', NULL, '1', '2026-08-22 00:00:00.000+00', NULL, '1', '1', false, NULL, NULL),
    ('328000000000000912', '328000000000000901', 'BIOCHEMISTRY', '生化', NULL, NULL, '2', true,
     NULL, 'default', NULL, '1', '2026-08-22 00:00:00.000+00', NULL, '1', '1', false, NULL, NULL),
    ('328000000000000913', '328000000000000901', 'OTHER', '其它', NULL, NULL, '9', true,
     NULL, 'default', NULL, '1', '2026-08-22 00:00:00.000+00', NULL, '1', '1', false, NULL, NULL)
ON CONFLICT ("id") DO NOTHING;

INSERT INTO whale_permissions (
    id, name, display_name, parent_name, description,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
) VALUES
    ('328000000000000904', 'Navigation.LabDevice', '设备管理', 'Navigation',
     '菜单入口: LabDevice', NULL,
     TIMESTAMP '2026-08-22 00:00:00+00', TIMESTAMP '2026-08-22 00:00:00+00', 1, 1, FALSE, NULL, NULL),
    ('328000000000000905', 'Navigation.SpecimenTubeMerge', '合管配备', 'Navigation',
     '菜单入口: SpecimenTubeMerge', NULL,
     TIMESTAMP '2026-08-22 00:00:00+00', TIMESTAMP '2026-08-22 00:00:00+00', 1, 1, FALSE, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    display_name = EXCLUDED.display_name,
    parent_name = EXCLUDED.parent_name,
    description = EXCLUDED.description,
    is_deleted = FALSE,
    updated_at = EXCLUDED.updated_at;

INSERT INTO whale_menus (
    id, code, name, display_name, icon, path, component, permission_name, parent_id,
    sort_order, is_enabled, is_hidden, is_external, external_url, menu_type, remark,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, keep_alive
) VALUES
    ('328000000000000902', 'lab_device', 'LabDevice', '设备管理', 'lucide:cpu',
     '/basic/labdevice', '/basic/labdevice/index', 'Navigation.LabDevice', '2000', 25,
     TRUE, FALSE, FALSE, '', 'menu', '基础数据-设备管理（LabDeviceAppService）', NULL,
     TIMESTAMP '2026-08-22 00:00:00+00', TIMESTAMP '2026-08-22 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE),
    ('328000000000000903', 'specimen_tube_merge', 'SpecimenTubeMerge', '合管配备', 'lucide:git-merge',
     '/config/specimentubemerge', '/config/specimentubemerge/index', 'Navigation.SpecimenTubeMerge', '310400000000000123', 30,
     TRUE, FALSE, FALSE, '', 'menu', '配置管理-合管配备（SpecimenTubeMergeAppService）', NULL,
     TIMESTAMP '2026-08-22 00:00:00+00', TIMESTAMP '2026-08-22 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE)
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
) VALUES
    ('328000000000000906', 'Navigation.LabDevice', 'R', '1', NULL,
     TIMESTAMP '2026-08-22 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL),
    ('328000000000000907', 'Navigation.SpecimenTubeMerge', 'R', '1', NULL,
     TIMESTAMP '2026-08-22 00:00:00+00', NULL, 1, NULL, FALSE, NULL, NULL)
ON CONFLICT (permission_name, provider_name, provider_key) DO UPDATE SET
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = TIMESTAMP '2026-08-22 00:00:00+00';
