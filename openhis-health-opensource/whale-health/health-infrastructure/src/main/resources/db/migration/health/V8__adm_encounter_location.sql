-- adm_encounter_location（建表）
-- Flyway V8

CREATE TABLE IF NOT EXISTS "public"."adm_encounter_location" (
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
    "encounter_id" int8 NOT NULL,
    "location_id" int8 NOT NULL,
    "form_enum" varchar(255),
    "status_enum" varchar(255) NOT NULL,
    "start_time" timestamptz(6),
    "end_time" timestamptz(6),
    CONSTRAINT "adm_encounter_location_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE adm_encounter_location IS '管理域 encounter location';
COMMENT ON COLUMN adm_encounter_location.id IS '主键';
COMMENT ON COLUMN adm_encounter_location.tenant_id IS '租户标识';
COMMENT ON COLUMN adm_encounter_location.created_at IS '创建时间';
COMMENT ON COLUMN adm_encounter_location.updated_at IS '最后更新时间';
COMMENT ON COLUMN adm_encounter_location.created_by IS '创建人';
COMMENT ON COLUMN adm_encounter_location.updated_by IS '最后更新人';
COMMENT ON COLUMN adm_encounter_location.is_deleted IS '软删标记';
COMMENT ON COLUMN adm_encounter_location.deleted_at IS '删除时间';
COMMENT ON COLUMN adm_encounter_location.deleted_by IS '删除人';
COMMENT ON COLUMN adm_encounter_location.version IS '乐观锁版本号';
COMMENT ON COLUMN adm_encounter_location.encounter_id IS '就诊标识';
COMMENT ON COLUMN adm_encounter_location.location_id IS '位置/科室标识';
COMMENT ON COLUMN adm_encounter_location.form_enum IS 'form枚举值';
COMMENT ON COLUMN adm_encounter_location.status_enum IS '状态枚举值';
COMMENT ON COLUMN adm_encounter_location.start_time IS '开始时间';
COMMENT ON COLUMN adm_encounter_location.end_time IS '结束时间';
