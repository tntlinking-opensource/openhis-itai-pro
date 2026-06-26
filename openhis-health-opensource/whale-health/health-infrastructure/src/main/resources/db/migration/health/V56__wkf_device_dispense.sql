-- wkf_device_dispense（建表）
-- Flyway V62

CREATE TABLE IF NOT EXISTS "public"."wkf_device_dispense" (
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
    "device_req_id" int8 NOT NULL,
    "status_enum" varchar(255) NOT NULL,
    "based_on_table" varchar(255),
    "based_on_id" int8,
    "not_performed_reason_enum" varchar(255),
    "dispense_category_enum" varchar(255),
    "quantity" numeric(18,2) NOT NULL,
    "unit_code" varchar(255) NOT NULL,
    "lot_number" varchar(255),
    "device_def_id" int8 NOT NULL,
    "patient_id" int8 NOT NULL,
    "encounter_id" int8 NOT NULL,
    "performer_id" int8,
    "location_id" int8,
    "part_type_code" varchar(255),
    "dispense_quantity" numeric(18,2),
    "preparer_id" int8,
    "prepare_time" timestamptz(6),
    "dispense_time" timestamptz(6),
    "limit_time" timestamptz(6),
    "planned_dispense_time" timestamptz(6),
    "des_location_id" varchar(255),
    "rec_practitioner_id" varchar(255),
    "support_info" varchar(255),
    "usage_instruction" varchar(255),
    "dispense_frequency_code" varchar(255),
    "trace_no" varchar(50000),
    "procedure_id" int8,
    CONSTRAINT "wkf_device_dispense_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE wkf_device_dispense IS '工作流 device dispense';
COMMENT ON COLUMN wkf_device_dispense.id IS '主键';
COMMENT ON COLUMN wkf_device_dispense.tenant_id IS '租户标识';
COMMENT ON COLUMN wkf_device_dispense.created_at IS '创建时间';
COMMENT ON COLUMN wkf_device_dispense.updated_at IS '最后更新时间';
COMMENT ON COLUMN wkf_device_dispense.created_by IS '创建人';
COMMENT ON COLUMN wkf_device_dispense.updated_by IS '最后更新人';
COMMENT ON COLUMN wkf_device_dispense.is_deleted IS '软删标记';
COMMENT ON COLUMN wkf_device_dispense.deleted_at IS '删除时间';
COMMENT ON COLUMN wkf_device_dispense.deleted_by IS '删除人';
COMMENT ON COLUMN wkf_device_dispense.version IS '乐观锁版本号';
COMMENT ON COLUMN wkf_device_dispense.bus_no IS '业务编号';
COMMENT ON COLUMN wkf_device_dispense.device_req_id IS '设备req标识';
COMMENT ON COLUMN wkf_device_dispense.status_enum IS '状态枚举值';
COMMENT ON COLUMN wkf_device_dispense.based_on_table IS '依据表名';
COMMENT ON COLUMN wkf_device_dispense.based_on_id IS 'basedon标识';
COMMENT ON COLUMN wkf_device_dispense.not_performed_reason_enum IS 'notperformed原因枚举值';
COMMENT ON COLUMN wkf_device_dispense.dispense_category_enum IS '发药category枚举值';
COMMENT ON COLUMN wkf_device_dispense.quantity IS '数量';
COMMENT ON COLUMN wkf_device_dispense.unit_code IS '单元编码';
COMMENT ON COLUMN wkf_device_dispense.lot_number IS '批号';
COMMENT ON COLUMN wkf_device_dispense.device_def_id IS '设备def标识';
COMMENT ON COLUMN wkf_device_dispense.patient_id IS '患者标识';
COMMENT ON COLUMN wkf_device_dispense.encounter_id IS '就诊标识';
COMMENT ON COLUMN wkf_device_dispense.performer_id IS 'performer标识';
COMMENT ON COLUMN wkf_device_dispense.location_id IS '位置/科室标识';
COMMENT ON COLUMN wkf_device_dispense.part_type_code IS 'part类型编码';
COMMENT ON COLUMN wkf_device_dispense.dispense_quantity IS '发药数量';
COMMENT ON COLUMN wkf_device_dispense.preparer_id IS 'preparer标识';
COMMENT ON COLUMN wkf_device_dispense.prepare_time IS 'prepare时间';
COMMENT ON COLUMN wkf_device_dispense.dispense_time IS '发药时间';
COMMENT ON COLUMN wkf_device_dispense.limit_time IS 'limit时间';
COMMENT ON COLUMN wkf_device_dispense.planned_dispense_time IS 'planned发药时间';
COMMENT ON COLUMN wkf_device_dispense.des_location_id IS 'des位置标识';
COMMENT ON COLUMN wkf_device_dispense.rec_practitioner_id IS 'rec医护人员标识';
COMMENT ON COLUMN wkf_device_dispense.support_info IS '辅助信息';
COMMENT ON COLUMN wkf_device_dispense.usage_instruction IS '用法说明';
COMMENT ON COLUMN wkf_device_dispense.dispense_frequency_code IS '发药频次编码';
COMMENT ON COLUMN wkf_device_dispense.trace_no IS '追踪编号';
COMMENT ON COLUMN wkf_device_dispense.procedure_id IS '处置标识';
