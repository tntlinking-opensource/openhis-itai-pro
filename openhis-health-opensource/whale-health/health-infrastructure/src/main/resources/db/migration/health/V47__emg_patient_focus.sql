-- emg_patient_focus（建表）
-- Flyway V47

CREATE TABLE IF NOT EXISTS "public"."emg_patient_focus" (
    "id" int8 NOT NULL,
    "tenant_id" int8,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,

    "patient_id" int8 NOT NULL,
    "encounter_id" int8 NOT NULL,
    "focus_note" text,
    "status_enum" varchar(32) NOT NULL,
    PRIMARY KEY ("id")
);