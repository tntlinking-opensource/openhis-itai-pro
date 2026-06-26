-- dia_vital_sign 主从结构（DM-DIA-002 v2.1 最终态；术语种子见 V51/V52）

CREATE TABLE IF NOT EXISTS "public"."dia_vital_sign" (
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
    "patient_id" int8 NOT NULL,
    "encounter_id" int8 NOT NULL,
    "recorded_at" timestamptz(6) NOT NULL,
    "type_enum" varchar(50) NOT NULL,
    "bus_no" varchar(100) NOT NULL,
    "location_id" int8,
    "baby_no" int4,
    "recorder_id" int8,
    "remark" varchar(500),
    CONSTRAINT "dia_vital_sign_pkey" PRIMARY KEY ("id")
);

CREATE TABLE IF NOT EXISTS "public"."dia_vital_sign_detail" (
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
    "vital_sign_id" int8 NOT NULL,
    "term_code" varchar(100) NOT NULL,
    "item_code" varchar(100) NOT NULL,
    "item_value" varchar(500) NOT NULL,
    "units" varchar(100),
    "remark" varchar(500),
    CONSTRAINT "dia_vital_sign_detail_pkey" PRIMARY KEY ("id")
);

CREATE INDEX IF NOT EXISTS "idx_dia_vital_sign_encounter_recorded"
    ON "public"."dia_vital_sign" ("encounter_id", "recorded_at")
    WHERE "is_deleted" = false;

CREATE INDEX IF NOT EXISTS "idx_dia_vital_sign_encounter_type_recorded"
    ON "public"."dia_vital_sign" ("encounter_id", "type_enum", "recorded_at")
    WHERE "is_deleted" = false;

CREATE INDEX IF NOT EXISTS "idx_dia_vital_sign_patient_recorded"
    ON "public"."dia_vital_sign" ("patient_id", "recorded_at")
    WHERE "is_deleted" = false;

CREATE UNIQUE INDEX IF NOT EXISTS "uk_dia_vital_sign_bus_no"
    ON "public"."dia_vital_sign" ("tenant_id", "bus_no")
    WHERE "is_deleted" = false;

CREATE INDEX IF NOT EXISTS "idx_dia_vital_sign_detail_vital_sign_id"
    ON "public"."dia_vital_sign_detail" ("vital_sign_id")
    WHERE "is_deleted" = false;

CREATE UNIQUE INDEX IF NOT EXISTS "uk_dia_vital_sign_detail_natural_key"
    ON "public"."dia_vital_sign_detail" ("tenant_id", "vital_sign_id", "term_code", "item_code")
    WHERE "is_deleted" = false;
COMMENT ON TABLE dia_vital_sign IS '生命体征/护理测量单据主表（DM-DIA-002 v2.1）';
COMMENT ON COLUMN dia_vital_sign.id IS '主键';
COMMENT ON COLUMN dia_vital_sign.tenant_id IS '租户标识';
COMMENT ON COLUMN dia_vital_sign.created_at IS '创建时间';
COMMENT ON COLUMN dia_vital_sign.updated_at IS '最后更新时间';
COMMENT ON COLUMN dia_vital_sign.created_by IS '创建人';
COMMENT ON COLUMN dia_vital_sign.updated_by IS '最后更新人';
COMMENT ON COLUMN dia_vital_sign.is_deleted IS '软删标记';
COMMENT ON COLUMN dia_vital_sign.deleted_at IS '删除时间';
COMMENT ON COLUMN dia_vital_sign.deleted_by IS '删除人';
COMMENT ON COLUMN dia_vital_sign.version IS '乐观锁版本号';
COMMENT ON COLUMN dia_vital_sign.patient_id IS '患者标识';
COMMENT ON COLUMN dia_vital_sign.encounter_id IS '就诊标识';
COMMENT ON COLUMN dia_vital_sign.recorded_at IS '记录时间';
COMMENT ON COLUMN dia_vital_sign.type_enum IS '单据类型：TEMPERATURE_SHEET / NURSING_RECORD';
COMMENT ON COLUMN dia_vital_sign.bus_no IS '业务单号，租户内唯一';
COMMENT ON COLUMN dia_vital_sign.location_id IS '位置/科室标识';
COMMENT ON COLUMN dia_vital_sign.baby_no IS '婴儿编号';
COMMENT ON COLUMN dia_vital_sign.recorder_id IS '记录人标识';
COMMENT ON COLUMN dia_vital_sign.remark IS '备注';

COMMENT ON TABLE dia_vital_sign_detail IS '生命体征/护理测量明细（通用字典项行）';
COMMENT ON COLUMN dia_vital_sign_detail.id IS '主键';
COMMENT ON COLUMN dia_vital_sign_detail.tenant_id IS '租户标识';
COMMENT ON COLUMN dia_vital_sign_detail.created_at IS '创建时间';
COMMENT ON COLUMN dia_vital_sign_detail.updated_at IS '最后更新时间';
COMMENT ON COLUMN dia_vital_sign_detail.created_by IS '创建人';
COMMENT ON COLUMN dia_vital_sign_detail.updated_by IS '最后更新人';
COMMENT ON COLUMN dia_vital_sign_detail.is_deleted IS '软删标记';
COMMENT ON COLUMN dia_vital_sign_detail.deleted_at IS '删除时间';
COMMENT ON COLUMN dia_vital_sign_detail.deleted_by IS '删除人';
COMMENT ON COLUMN dia_vital_sign_detail.version IS '乐观锁版本号';
COMMENT ON COLUMN dia_vital_sign_detail.vital_sign_id IS '生命体征体征标识';
COMMENT ON COLUMN dia_vital_sign_detail.term_code IS '术语体系 code（如 vital_signs_code）';
COMMENT ON COLUMN dia_vital_sign_detail.item_code IS '字典项 code';
COMMENT ON COLUMN dia_vital_sign_detail.item_value IS '字典项值';
COMMENT ON COLUMN dia_vital_sign_detail.units IS '单位';
COMMENT ON COLUMN dia_vital_sign_detail.remark IS '明细备注';
