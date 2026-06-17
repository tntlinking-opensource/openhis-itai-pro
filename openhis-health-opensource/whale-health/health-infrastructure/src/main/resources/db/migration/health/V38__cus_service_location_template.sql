-- cus_service_location_template（建表）
-- Flyway V38

CREATE TABLE IF NOT EXISTS "public"."cus_service_location_template" (
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
    "organization_id" int8 NOT NULL,
    "item_table" varchar(255) NOT NULL,
    "distribution_category_code" varchar(255) NOT NULL,
    "def_location_id" int8 NOT NULL,
    "item_definition_id" int8,
    "method_code" varchar(255),
    "start_time" time(6),
    "end_time" time(6),
    "priority" int4 NOT NULL,
    "display_order" int4,
    "remark" varchar(255),
    CONSTRAINT "cus_service_location_template_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE cus_service_location_template IS '自定义 service location template';

COMMENT ON COLUMN cus_service_location_template.id IS '主键';
COMMENT ON COLUMN cus_service_location_template.tenant_id IS '租户标识';
COMMENT ON COLUMN cus_service_location_template.created_at IS '创建时间';
COMMENT ON COLUMN cus_service_location_template.updated_at IS '最后更新时间';
COMMENT ON COLUMN cus_service_location_template.created_by IS '创建人';
COMMENT ON COLUMN cus_service_location_template.updated_by IS '最后更新人';
COMMENT ON COLUMN cus_service_location_template.is_deleted IS '软删标记';
COMMENT ON COLUMN cus_service_location_template.deleted_at IS '删除时间';
COMMENT ON COLUMN cus_service_location_template.deleted_by IS '删除人';
COMMENT ON COLUMN cus_service_location_template.version IS '乐观锁版本号';
COMMENT ON COLUMN cus_service_location_template.organization_id IS 'organization id';
COMMENT ON COLUMN cus_service_location_template.item_table IS 'item table';
COMMENT ON COLUMN cus_service_location_template.distribution_category_code IS 'distribution category code';
COMMENT ON COLUMN cus_service_location_template.def_location_id IS 'def location id';
COMMENT ON COLUMN cus_service_location_template.item_definition_id IS 'item definition id';
COMMENT ON COLUMN cus_service_location_template.method_code IS 'method code';
COMMENT ON COLUMN cus_service_location_template.start_time IS 'start time';
COMMENT ON COLUMN cus_service_location_template.end_time IS 'end time';
COMMENT ON COLUMN cus_service_location_template.priority IS 'priority';
COMMENT ON COLUMN cus_service_location_template.display_order IS 'display order';
COMMENT ON COLUMN cus_service_location_template.remark IS 'remark';

-- seed: cus_service_location_template
INSERT INTO "cus_service_location_template" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "organization_id", "item_table", "distribution_category_code", "def_location_id", "item_definition_id", "method_code", "start_time", "end_time", "priority", "display_order", "remark")
VALUES
('322976478253289472', '1', '2026-06-10 05:53:13.028+00', '2026-06-10 05:53:13.028+00', '1', '1', false, NULL, NULL, 0, '322663218269917184', 'med_medication_definition', '1', '322954949549363200', NULL, NULL, '00:00:00', '23:59:59', 1, 1, NULL),
('322976478257483776', '1', '2026-06-10 05:53:13.029+00', '2026-06-10 05:53:13.029+00', '1', '1', false, NULL, NULL, 0, '322663218269917184', 'med_medication_definition', '2', '322954949549363200', NULL, NULL, '00:00:00', '23:59:59', 2, 2, NULL),
('322976478257483777', '1', '2026-06-10 05:53:13.029+00', '2026-06-10 05:53:13.029+00', '1', '1', false, NULL, NULL, 0, '322663218269917184', 'adm_device_definition', '11', '322955075642724352', NULL, NULL, '00:00:00', '23:59:59', 3, 3, NULL),
('322976559425654784', '1', '2026-06-10 05:53:32.381+00', '2026-06-10 05:53:32.381+00', '1', '1', false, NULL, NULL, 0, '322663308812357632', 'med_medication_definition', '1', '322955009603407872', NULL, NULL, '00:00:00', '23:59:59', 1, 1, NULL),
('322976559425654785', '1', '2026-06-10 05:53:32.381+00', '2026-06-10 05:53:32.381+00', '1', '1', false, NULL, NULL, 0, '322663308812357632', 'med_medication_definition', '2', '322955009603407872', NULL, NULL, '00:00:00', '23:59:59', 2, 2, NULL),
('322976559425654786', '1', '2026-06-10 05:53:32.381+00', '2026-06-10 05:53:32.381+00', '1', '1', false, NULL, NULL, 0, '322663308812357632', 'adm_device_definition', '11', '322955098803671040', NULL, NULL, '00:00:00', '23:59:59', 3, 3, NULL),
('323999010000000020', '1', '2026-06-17 08:00:00.000+00', '2026-06-17 08:00:00.000+00', '1', '1', false, NULL, NULL, 0, '322663218269917184', 'med_medication_definition', '4', '322954984441778176', NULL, NULL, '00:00:00', '23:59:59', 4, 4, '门诊中草药→门诊中药房'),
('323999010000000021', '1', '2026-06-17 08:00:00.000+00', '2026-06-17 08:00:00.000+00', '1', '1', false, NULL, NULL, 0, '322663308812357632', 'med_medication_definition', '4', '322955039064199168', NULL, NULL, '00:00:00', '23:59:59', 4, 4, '住院中草药→住院中药房')
ON CONFLICT ("id") DO NOTHING;
