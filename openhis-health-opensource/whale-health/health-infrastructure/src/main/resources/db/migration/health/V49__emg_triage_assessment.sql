-- emg_triage_assessment（建表）
-- Flyway V49

CREATE TABLE IF NOT EXISTS "public"."emg_triage_assessment" (
    "id" int8 NOT NULL,
    "tenant_id" int8,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,

    "emg_triage_id" int8 NOT NULL,
    "assessment_type" varchar(128) NOT NULL,
    "assessment_data" text NOT NULL,
    "assessment_score" numeric(16,4),
    "assessed_at" timestamptz(6) NOT NULL,
    "assessed_by" int8,

    CONSTRAINT "emg_triage_assessment_pkey" PRIMARY KEY ("id")
);