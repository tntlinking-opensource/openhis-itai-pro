-- cus_order_template_line（建表）
-- Flyway V35

CREATE TABLE IF NOT EXISTS "public"."cus_order_template_line" (
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
    "order_template_id" int8 NOT NULL,
    "line_kind_enum" varchar(255) NOT NULL,
    "medication_definition_id" int8,
    "activity_definition_id" int8,
    "device_definition_id" int8,
    "group_key" varchar(255),
    "sort_order" int4 NOT NULL,
    "default_quantity" numeric(18,2),
    "default_unit_code" varchar(255),
    "default_days" int4,
    "frequency_id" int8,
    "route_code" varchar(255),
    "dosage_text" varchar(255),
    "default_perform_org_id" int8,
    "line_remark" varchar(255),
    "chinese_herbs_dose_quantity" numeric(18, 2),
    "suffering_flag" bool,
    CONSTRAINT "cus_order_template_line_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE cus_order_template_line IS '自定义 order template line';
COMMENT ON COLUMN cus_order_template_line.id IS '主键';
COMMENT ON COLUMN cus_order_template_line.tenant_id IS '租户标识';
COMMENT ON COLUMN cus_order_template_line.created_at IS '创建时间';
COMMENT ON COLUMN cus_order_template_line.updated_at IS '最后更新时间';
COMMENT ON COLUMN cus_order_template_line.created_by IS '创建人';
COMMENT ON COLUMN cus_order_template_line.updated_by IS '最后更新人';
COMMENT ON COLUMN cus_order_template_line.is_deleted IS '软删标记';
COMMENT ON COLUMN cus_order_template_line.deleted_at IS '删除时间';
COMMENT ON COLUMN cus_order_template_line.deleted_by IS '删除人';
COMMENT ON COLUMN cus_order_template_line.version IS '乐观锁版本号';
COMMENT ON COLUMN cus_order_template_line.order_template_id IS 'order模板标识';
COMMENT ON COLUMN cus_order_template_line.line_kind_enum IS 'linekind枚举值';
COMMENT ON COLUMN cus_order_template_line.medication_definition_id IS '药品定义标识';
COMMENT ON COLUMN cus_order_template_line.activity_definition_id IS '活动定义标识';
COMMENT ON COLUMN cus_order_template_line.device_definition_id IS '设备定义标识';
COMMENT ON COLUMN cus_order_template_line.group_key IS '分组key';
COMMENT ON COLUMN cus_order_template_line.sort_order IS '排序号';
COMMENT ON COLUMN cus_order_template_line.default_quantity IS 'default数量';
COMMENT ON COLUMN cus_order_template_line.default_unit_code IS 'default单元编码';
COMMENT ON COLUMN cus_order_template_line.default_days IS '默认天数';
COMMENT ON COLUMN cus_order_template_line.frequency_id IS '频次标识';
COMMENT ON COLUMN cus_order_template_line.route_code IS 'route编码';
COMMENT ON COLUMN cus_order_template_line.dosage_text IS 'dosage文本';
COMMENT ON COLUMN cus_order_template_line.default_perform_org_id IS 'defaultperformorg标识';
COMMENT ON COLUMN cus_order_template_line.line_remark IS '行备注';
COMMENT ON COLUMN cus_order_template_line.chinese_herbs_dose_quantity IS '组套模板行（聚合内子实体）';
COMMENT ON COLUMN cus_order_template_line.suffering_flag IS 'TCM 行级代煎标志';

-- ----- seed sync from DB (cus_order_template_line, 13 rows) -----

INSERT INTO "cus_order_template_line" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "order_template_id", "line_kind_enum", "medication_definition_id", "activity_definition_id", "device_definition_id", "group_key", "sort_order", "default_quantity", "default_unit_code", "default_days", "frequency_id", "route_code", "dosage_text", "default_perform_org_id", "line_remark", "chinese_herbs_dose_quantity", "suffering_flag") VALUES ('327719620303654912', '1', '2026-06-23 08:00:46.216+00', '2026-06-23 08:00:46.216+00', '1', '1', false, NULL, NULL, 0, '327719620186214400', 'MEDICATION', '322996998399594496', NULL, NULL, '1782201639464', 1, '1.00', '8', 1, '320010001000000003', '404', '250ml', NULL, NULL, NULL, NULL) ON CONFLICT ("id") DO NOTHING;

INSERT INTO "cus_order_template_line" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "order_template_id", "line_kind_enum", "medication_definition_id", "activity_definition_id", "device_definition_id", "group_key", "sort_order", "default_quantity", "default_unit_code", "default_days", "frequency_id", "route_code", "dosage_text", "default_perform_org_id", "line_remark", "chinese_herbs_dose_quantity", "suffering_flag") VALUES ('327719620303654913', '1', '2026-06-23 08:00:46.216+00', '2026-06-23 08:00:46.216+00', '1', '1', false, NULL, NULL, 0, '327719620186214400', 'MEDICATION', '322997883812974592', NULL, NULL, '1782201639464', 2, '1.00', '8', 1, '320010001000000003', '404', '0.75g', NULL, NULL, NULL, NULL) ON CONFLICT ("id") DO NOTHING;

INSERT INTO "cus_order_template_line" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "order_template_id", "line_kind_enum", "medication_definition_id", "activity_definition_id", "device_definition_id", "group_key", "sort_order", "default_quantity", "default_unit_code", "default_days", "frequency_id", "route_code", "dosage_text", "default_perform_org_id", "line_remark", "chinese_herbs_dose_quantity", "suffering_flag") VALUES ('327719980917329920', '1', '2026-06-23 08:02:12.193+00', '2026-06-23 08:02:12.193+00', '1', '1', false, NULL, NULL, 0, '327719980850221056', 'MEDICATION', '323999010000000001', NULL, NULL, NULL, 1, NULL, NULL, 10, '320010001000000003', '1', '10g', NULL, NULL, NULL, NULL) ON CONFLICT ("id") DO NOTHING;

INSERT INTO "cus_order_template_line" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "order_template_id", "line_kind_enum", "medication_definition_id", "activity_definition_id", "device_definition_id", "group_key", "sort_order", "default_quantity", "default_unit_code", "default_days", "frequency_id", "route_code", "dosage_text", "default_perform_org_id", "line_remark", "chinese_herbs_dose_quantity", "suffering_flag") VALUES ('327719980917329921', '1', '2026-06-23 08:02:12.193+00', '2026-06-23 08:02:12.193+00', '1', '1', false, NULL, NULL, 0, '327719980850221056', 'MEDICATION', '323999010000000013', NULL, NULL, NULL, 2, NULL, NULL, 10, '320010001000000003', '1', '10g', NULL, NULL, NULL, NULL) ON CONFLICT ("id") DO NOTHING;

INSERT INTO "cus_order_template_line" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "order_template_id", "line_kind_enum", "medication_definition_id", "activity_definition_id", "device_definition_id", "group_key", "sort_order", "default_quantity", "default_unit_code", "default_days", "frequency_id", "route_code", "dosage_text", "default_perform_org_id", "line_remark", "chinese_herbs_dose_quantity", "suffering_flag") VALUES ('327719980917329922', '1', '2026-06-23 08:02:12.193+00', '2026-06-23 08:02:12.193+00', '1', '1', false, NULL, NULL, 0, '327719980850221056', 'MEDICATION', '323999010000000012', NULL, NULL, NULL, 3, NULL, NULL, 10, '320010001000000003', '1', '10g', NULL, NULL, NULL, NULL) ON CONFLICT ("id") DO NOTHING;

INSERT INTO "cus_order_template_line" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "order_template_id", "line_kind_enum", "medication_definition_id", "activity_definition_id", "device_definition_id", "group_key", "sort_order", "default_quantity", "default_unit_code", "default_days", "frequency_id", "route_code", "dosage_text", "default_perform_org_id", "line_remark", "chinese_herbs_dose_quantity", "suffering_flag") VALUES ('327719980917329923', '1', '2026-06-23 08:02:12.193+00', '2026-06-23 08:02:12.193+00', '1', '1', false, NULL, NULL, 0, '327719980850221056', 'MEDICATION', '323999010000000015', NULL, NULL, NULL, 4, NULL, NULL, 10, '320010001000000003', '1', '10g', NULL, NULL, NULL, NULL) ON CONFLICT ("id") DO NOTHING;

INSERT INTO "cus_order_template_line" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "order_template_id", "line_kind_enum", "medication_definition_id", "activity_definition_id", "device_definition_id", "group_key", "sort_order", "default_quantity", "default_unit_code", "default_days", "frequency_id", "route_code", "dosage_text", "default_perform_org_id", "line_remark", "chinese_herbs_dose_quantity", "suffering_flag") VALUES ('327720163583463424', '1', '2026-06-23 08:02:55.744+00', '2026-06-23 08:02:55.744+00', '1', '1', false, NULL, NULL, 0, '327720163520548864', 'MEDICATION', '323999010000000012', NULL, NULL, NULL, 1, NULL, NULL, 10, '320010001000000003', 'T03', '10g', NULL, NULL, NULL, NULL) ON CONFLICT ("id") DO NOTHING;

INSERT INTO "cus_order_template_line" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "order_template_id", "line_kind_enum", "medication_definition_id", "activity_definition_id", "device_definition_id", "group_key", "sort_order", "default_quantity", "default_unit_code", "default_days", "frequency_id", "route_code", "dosage_text", "default_perform_org_id", "line_remark", "chinese_herbs_dose_quantity", "suffering_flag") VALUES ('327720163583463425', '1', '2026-06-23 08:02:55.744+00', '2026-06-23 08:02:55.744+00', '1', '1', false, NULL, NULL, 0, '327720163520548864', 'MEDICATION', '323999010000000015', NULL, NULL, NULL, 2, NULL, NULL, 10, '320010001000000003', 'T03', '10g', NULL, NULL, NULL, NULL) ON CONFLICT ("id") DO NOTHING;

INSERT INTO "cus_order_template_line" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "order_template_id", "line_kind_enum", "medication_definition_id", "activity_definition_id", "device_definition_id", "group_key", "sort_order", "default_quantity", "default_unit_code", "default_days", "frequency_id", "route_code", "dosage_text", "default_perform_org_id", "line_remark", "chinese_herbs_dose_quantity", "suffering_flag") VALUES ('327720163583463426', '1', '2026-06-23 08:02:55.744+00', '2026-06-23 08:02:55.744+00', '1', '1', false, NULL, NULL, 0, '327720163520548864', 'MEDICATION', '323999010000000016', NULL, NULL, NULL, 3, NULL, NULL, 10, '320010001000000003', 'T03', '10g', NULL, NULL, NULL, NULL) ON CONFLICT ("id") DO NOTHING;

INSERT INTO "cus_order_template_line" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "order_template_id", "line_kind_enum", "medication_definition_id", "activity_definition_id", "device_definition_id", "group_key", "sort_order", "default_quantity", "default_unit_code", "default_days", "frequency_id", "route_code", "dosage_text", "default_perform_org_id", "line_remark", "chinese_herbs_dose_quantity", "suffering_flag") VALUES ('328101749030653952', '1', '2026-06-24 09:19:12.804+00', '2026-06-28 09:23:40.165+00', '322963602154721280', '1', false, NULL, NULL, 0, '328101749022265344', 'MEDICATION', '322997883812974592', NULL, NULL, '1782201639464', 1, '1.00', '8', 1, '320010001000000003', '404', '0.75g', '322663218269917184', NULL, NULL, NULL) ON CONFLICT ("id") DO NOTHING;

INSERT INTO "cus_order_template_line" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "order_template_id", "line_kind_enum", "medication_definition_id", "activity_definition_id", "device_definition_id", "group_key", "sort_order", "default_quantity", "default_unit_code", "default_days", "frequency_id", "route_code", "dosage_text", "default_perform_org_id", "line_remark", "chinese_herbs_dose_quantity", "suffering_flag") VALUES ('328101749034848256', '1', '2026-06-24 09:19:12.805+00', '2026-06-28 09:23:53.393+00', '322963602154721280', '1', false, NULL, NULL, 0, '328101749022265344', 'MEDICATION', '322996998399594496', NULL, NULL, '1782201639464', 2, '1.00', '8', 1, '320010001000000003', '404', '250ml', '322663218269917184', NULL, NULL, NULL) ON CONFLICT ("id") DO NOTHING;

INSERT INTO "cus_order_template_line" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "order_template_id", "line_kind_enum", "medication_definition_id", "activity_definition_id", "device_definition_id", "group_key", "sort_order", "default_quantity", "default_unit_code", "default_days", "frequency_id", "route_code", "dosage_text", "default_perform_org_id", "line_remark", "chinese_herbs_dose_quantity", "suffering_flag") VALUES ('328101749034848257', '1', '2026-06-24 09:19:12.805+00', '2026-06-24 09:19:12.805+00', '322963602154721280', '322963602154721280', false, NULL, NULL, 0, '328101749022265344', 'DEVICE', NULL, NULL, '323000109906268160', NULL, 3, '1.00', '9', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL) ON CONFLICT ("id") DO NOTHING;

INSERT INTO "cus_order_template_line" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "order_template_id", "line_kind_enum", "medication_definition_id", "activity_definition_id", "device_definition_id", "group_key", "sort_order", "default_quantity", "default_unit_code", "default_days", "frequency_id", "route_code", "dosage_text", "default_perform_org_id", "line_remark", "chinese_herbs_dose_quantity", "suffering_flag") VALUES ('328101749039042560', '1', '2026-06-24 09:19:12.806+00', '2026-06-24 09:19:12.806+00', '322963602154721280', '322963602154721280', false, NULL, NULL, 0, '328101749022265344', 'SERVICE', NULL, '323001764504670208', NULL, NULL, 4, '1.00', '6', 1, NULL, NULL, NULL, NULL, NULL, NULL, NULL) ON CONFLICT ("id") DO NOTHING;
