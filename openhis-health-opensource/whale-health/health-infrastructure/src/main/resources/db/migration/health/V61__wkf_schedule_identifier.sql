-- wkf_schedule_identifier（建表）
-- Flyway V67

CREATE TABLE IF NOT EXISTS "public"."wkf_schedule_identifier" (
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
    "schedule_id" int8 NOT NULL,
    "id_system" varchar(255) NOT NULL,
    "id_value" varchar(255) NOT NULL,
    "use_code" varchar(255),
    CONSTRAINT "wkf_schedule_identifier_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE wkf_schedule_identifier IS '日程外部标识（FHIR Identifier 语义）';
COMMENT ON COLUMN wkf_schedule_identifier.id IS '主键';
COMMENT ON COLUMN wkf_schedule_identifier.tenant_id IS '租户标识';
COMMENT ON COLUMN wkf_schedule_identifier.created_at IS '创建时间';
COMMENT ON COLUMN wkf_schedule_identifier.updated_at IS '最后更新时间';
COMMENT ON COLUMN wkf_schedule_identifier.created_by IS '创建人';
COMMENT ON COLUMN wkf_schedule_identifier.updated_by IS '最后更新人';
COMMENT ON COLUMN wkf_schedule_identifier.is_deleted IS '软删标记';
COMMENT ON COLUMN wkf_schedule_identifier.deleted_at IS '删除时间';
COMMENT ON COLUMN wkf_schedule_identifier.deleted_by IS '删除人';
COMMENT ON COLUMN wkf_schedule_identifier.version IS '乐观锁版本号';
COMMENT ON COLUMN wkf_schedule_identifier.schedule_id IS '所属日程标识';
COMMENT ON COLUMN wkf_schedule_identifier.id_system IS '标识体系 URI';
COMMENT ON COLUMN wkf_schedule_identifier.id_value IS '标识值';
COMMENT ON COLUMN wkf_schedule_identifier.use_code IS '用途编码';
