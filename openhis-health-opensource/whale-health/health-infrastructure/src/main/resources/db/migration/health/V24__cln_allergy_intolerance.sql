-- cln_allergy_intolerance（建表）
-- Flyway V24

CREATE TABLE IF NOT EXISTS "public"."cln_allergy_intolerance" (
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
    "request_id" int8 NOT NULL,
    "encounter_id" int8 NOT NULL,
    "patient_id" int8 NOT NULL,
    "medication_id" int8,
    "lot_number" varchar(255),
    "clinical_status_enum" varchar(255) NOT NULL,
    "verification_status_enum" varchar(255) NOT NULL,
    "type_enum" varchar(255),
    "category_code" varchar(255),
    "criticality_enum" varchar(255),
    "code" varchar(255),
    "description" varchar(255),
    "severity_enum" varchar(255),
    "onset_date_time" timestamptz(6),
    "recorded_date" timestamptz(6),
    "last_reaction_occurrence" timestamptz(6),
    "exposure_route" varchar(255),
    "practitioner_id" int8,
    "check_practitioner_id" int8,
    "note" varchar(255),
    CONSTRAINT "cln_allergy_intolerance_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE cln_allergy_intolerance IS '临床 allergy intolerance';

COMMENT ON COLUMN cln_allergy_intolerance.id IS '主键';
COMMENT ON COLUMN cln_allergy_intolerance.tenant_id IS '租户标识';
COMMENT ON COLUMN cln_allergy_intolerance.created_at IS '创建时间';
COMMENT ON COLUMN cln_allergy_intolerance.updated_at IS '最后更新时间';
COMMENT ON COLUMN cln_allergy_intolerance.created_by IS '创建人';
COMMENT ON COLUMN cln_allergy_intolerance.updated_by IS '最后更新人';
COMMENT ON COLUMN cln_allergy_intolerance.is_deleted IS '软删标记';
COMMENT ON COLUMN cln_allergy_intolerance.deleted_at IS '删除时间';
COMMENT ON COLUMN cln_allergy_intolerance.deleted_by IS '删除人';
COMMENT ON COLUMN cln_allergy_intolerance.version IS '乐观锁版本号';
COMMENT ON COLUMN cln_allergy_intolerance.request_id IS 'request id';
COMMENT ON COLUMN cln_allergy_intolerance.encounter_id IS 'encounter id';
COMMENT ON COLUMN cln_allergy_intolerance.patient_id IS 'patient id';
COMMENT ON COLUMN cln_allergy_intolerance.medication_id IS 'medication id';
COMMENT ON COLUMN cln_allergy_intolerance.lot_number IS 'lot number';
COMMENT ON COLUMN cln_allergy_intolerance.clinical_status_enum IS 'clinical status enum';
COMMENT ON COLUMN cln_allergy_intolerance.verification_status_enum IS 'verification status enum';
COMMENT ON COLUMN cln_allergy_intolerance.type_enum IS 'type enum';
COMMENT ON COLUMN cln_allergy_intolerance.category_code IS 'category code';
COMMENT ON COLUMN cln_allergy_intolerance.criticality_enum IS 'criticality enum';
COMMENT ON COLUMN cln_allergy_intolerance.code IS 'code';
COMMENT ON COLUMN cln_allergy_intolerance.description IS 'description';
COMMENT ON COLUMN cln_allergy_intolerance.severity_enum IS 'severity enum';
COMMENT ON COLUMN cln_allergy_intolerance.onset_date_time IS 'onset date time';
COMMENT ON COLUMN cln_allergy_intolerance.recorded_date IS 'recorded date';
COMMENT ON COLUMN cln_allergy_intolerance.last_reaction_occurrence IS 'last reaction occurrence';
COMMENT ON COLUMN cln_allergy_intolerance.exposure_route IS 'exposure route';
COMMENT ON COLUMN cln_allergy_intolerance.practitioner_id IS 'practitioner id';
COMMENT ON COLUMN cln_allergy_intolerance.check_practitioner_id IS 'check practitioner id';
COMMENT ON COLUMN cln_allergy_intolerance.note IS 'note';

