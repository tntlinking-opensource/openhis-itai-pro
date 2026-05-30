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