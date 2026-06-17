-- adm_encounter_participant（建表）
-- Flyway V9

CREATE TABLE IF NOT EXISTS "public"."adm_encounter_participant" (
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
    "role_code" varchar(255) NOT NULL,
    "practitioner_id" int8 NOT NULL,
    "status_enum" varchar(255) NOT NULL,
    "start_time" timestamptz(6),
    "end_time" timestamptz(6),
    CONSTRAINT "adm_encounter_participant_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE adm_encounter_participant IS '管理域 encounter participant';

COMMENT ON COLUMN adm_encounter_participant.id IS '主键';
COMMENT ON COLUMN adm_encounter_participant.tenant_id IS '租户标识';
COMMENT ON COLUMN adm_encounter_participant.created_at IS '创建时间';
COMMENT ON COLUMN adm_encounter_participant.updated_at IS '最后更新时间';
COMMENT ON COLUMN adm_encounter_participant.created_by IS '创建人';
COMMENT ON COLUMN adm_encounter_participant.updated_by IS '最后更新人';
COMMENT ON COLUMN adm_encounter_participant.is_deleted IS '软删标记';
COMMENT ON COLUMN adm_encounter_participant.deleted_at IS '删除时间';
COMMENT ON COLUMN adm_encounter_participant.deleted_by IS '删除人';
COMMENT ON COLUMN adm_encounter_participant.version IS '乐观锁版本号';
COMMENT ON COLUMN adm_encounter_participant.encounter_id IS 'encounter id';
COMMENT ON COLUMN adm_encounter_participant.role_code IS 'role code';
COMMENT ON COLUMN adm_encounter_participant.practitioner_id IS 'practitioner id';
COMMENT ON COLUMN adm_encounter_participant.status_enum IS 'status enum';
COMMENT ON COLUMN adm_encounter_participant.start_time IS 'start time';
COMMENT ON COLUMN adm_encounter_participant.end_time IS 'end time';

