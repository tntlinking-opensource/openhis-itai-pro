-- adm_patient_identifier（建表）
-- Flyway V18

CREATE TABLE IF NOT EXISTS "public"."adm_patient_identifier" (
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
    "type_code" varchar(255) NOT NULL,
    "identifier_no" varchar(255) NOT NULL,
    "state_enum" varchar(255),
    "start_time" timestamptz(6),
    "end_time" timestamptz(6),
    "patient_id" int8 NOT NULL,
    CONSTRAINT "adm_patient_identifier_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE adm_patient_identifier IS '管理域 patient identifier';

COMMENT ON COLUMN adm_patient_identifier.id IS '主键';
COMMENT ON COLUMN adm_patient_identifier.tenant_id IS '租户标识';
COMMENT ON COLUMN adm_patient_identifier.created_at IS '创建时间';
COMMENT ON COLUMN adm_patient_identifier.updated_at IS '最后更新时间';
COMMENT ON COLUMN adm_patient_identifier.created_by IS '创建人';
COMMENT ON COLUMN adm_patient_identifier.updated_by IS '最后更新人';
COMMENT ON COLUMN adm_patient_identifier.is_deleted IS '软删标记';
COMMENT ON COLUMN adm_patient_identifier.deleted_at IS '删除时间';
COMMENT ON COLUMN adm_patient_identifier.deleted_by IS '删除人';
COMMENT ON COLUMN adm_patient_identifier.version IS '乐观锁版本号';
COMMENT ON COLUMN adm_patient_identifier.type_code IS '患者非身份证标识子实体';
COMMENT ON COLUMN adm_patient_identifier.identifier_no IS '标识号码';
COMMENT ON COLUMN adm_patient_identifier.state_enum IS '标识状态编码';
COMMENT ON COLUMN adm_patient_identifier.start_time IS '有效开始时间';
COMMENT ON COLUMN adm_patient_identifier.end_time IS '有效结束时间';
COMMENT ON COLUMN adm_patient_identifier.patient_id IS '所属患者标识';

