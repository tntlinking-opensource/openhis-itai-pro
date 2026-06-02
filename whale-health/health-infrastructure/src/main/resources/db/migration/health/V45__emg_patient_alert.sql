-- emg_patient_alert（建表）
-- Flyway V45

CREATE TABLE IF NOT EXISTS "public"."emg_patient_alert" (
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
    "alert_type_enum" varchar(32) NOT NULL,
    "mark_type_code" varchar(64),
    "reason_text" text NOT NULL,
    "status_enum" varchar(32) NOT NULL,
    "effective_from" timestamptz(6),
    "effective_to" timestamptz(6),
    "revoked_at" timestamptz(6),
    "revoked_by" int8,
    PRIMARY KEY ("id")
);