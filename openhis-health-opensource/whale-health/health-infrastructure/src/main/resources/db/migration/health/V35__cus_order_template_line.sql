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
COMMENT ON COLUMN cus_order_template_line.order_template_id IS 'order template id';
COMMENT ON COLUMN cus_order_template_line.line_kind_enum IS 'line kind enum';
COMMENT ON COLUMN cus_order_template_line.medication_definition_id IS 'medication definition id';
COMMENT ON COLUMN cus_order_template_line.activity_definition_id IS 'activity definition id';
COMMENT ON COLUMN cus_order_template_line.device_definition_id IS 'device definition id';
COMMENT ON COLUMN cus_order_template_line.group_key IS 'group key';
COMMENT ON COLUMN cus_order_template_line.sort_order IS 'sort order';
COMMENT ON COLUMN cus_order_template_line.default_quantity IS 'default quantity';
COMMENT ON COLUMN cus_order_template_line.default_unit_code IS 'default unit code';
COMMENT ON COLUMN cus_order_template_line.default_days IS 'default days';
COMMENT ON COLUMN cus_order_template_line.frequency_id IS 'frequency id';
COMMENT ON COLUMN cus_order_template_line.route_code IS 'route code';
COMMENT ON COLUMN cus_order_template_line.dosage_text IS 'dosage text';
COMMENT ON COLUMN cus_order_template_line.default_perform_org_id IS 'default perform org id';
COMMENT ON COLUMN cus_order_template_line.line_remark IS 'line remark';
COMMENT ON COLUMN cus_order_template_line.chinese_herbs_dose_quantity IS '组套模板行（聚合内子实体）';
COMMENT ON COLUMN cus_order_template_line.suffering_flag IS 'TCM 行级代煎标志';

