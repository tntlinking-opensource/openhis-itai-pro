-- adm_encounter_diagnosis（建表）
-- Flyway V7

CREATE TABLE IF NOT EXISTS "public"."adm_encounter_diagnosis" (
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
    "condition_id" int8 NOT NULL,
    "primary_flag" bool,
    "rank" int4,
    "description" varchar(255),
    "diag_type_code" varchar(32),
    "diag_code" int4,
    "condition_code" int4,
    "med_type" varchar(255),
    "highest_diag_flag" bool,
    "tcm_syndrome_group" varchar(255),
    "tcm_flag" bool,
    CONSTRAINT "adm_encounter_diagnosis_pkey" PRIMARY KEY ("id")
);