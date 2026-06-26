-- adm_encounter_reason（建表）
-- Flyway V10

CREATE TABLE IF NOT EXISTS "public"."adm_encounter_reason" (
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
    "use_code" varchar(255) NOT NULL,
    "reason_table" varchar(255),
    "reason_id" int8,
    "description" varchar(255),
    CONSTRAINT "adm_encounter_reason_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE adm_encounter_reason IS '管理域 encounter reason';
COMMENT ON COLUMN adm_encounter_reason.id IS '主键';
COMMENT ON COLUMN adm_encounter_reason.tenant_id IS '租户标识';
COMMENT ON COLUMN adm_encounter_reason.created_at IS '创建时间';
COMMENT ON COLUMN adm_encounter_reason.updated_at IS '最后更新时间';
COMMENT ON COLUMN adm_encounter_reason.created_by IS '创建人';
COMMENT ON COLUMN adm_encounter_reason.updated_by IS '最后更新人';
COMMENT ON COLUMN adm_encounter_reason.is_deleted IS '软删标记';
COMMENT ON COLUMN adm_encounter_reason.deleted_at IS '删除时间';
COMMENT ON COLUMN adm_encounter_reason.deleted_by IS '删除人';
COMMENT ON COLUMN adm_encounter_reason.version IS '乐观锁版本号';
COMMENT ON COLUMN adm_encounter_reason.encounter_id IS '就诊原因子实体：记录就诊事由的来源表与业务主键及用途编码';
COMMENT ON COLUMN adm_encounter_reason.use_code IS '用途编码（如挂号原因类型）';
COMMENT ON COLUMN adm_encounter_reason.reason_table IS '原因实体所在逻辑表名';
COMMENT ON COLUMN adm_encounter_reason.reason_id IS '原因业务主键';
COMMENT ON COLUMN adm_encounter_reason.description IS '原因描述';
