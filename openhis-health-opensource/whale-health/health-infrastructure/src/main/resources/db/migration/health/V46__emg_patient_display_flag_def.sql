-- emg_patient_display_flag_def（建表）
-- Flyway V46

CREATE TABLE IF NOT EXISTS "public"."emg_patient_display_flag_def" (
    "id" int8 NOT NULL,
    "tenant_id" int8,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,

    "flag_code" varchar(64) NOT NULL,
    "display_label" varchar(128) NOT NULL,
    "severity" varchar(16) NOT NULL DEFAULT 'INFO',
    "sort_order" int4 NOT NULL DEFAULT 0,
    "enabled" bool NOT NULL DEFAULT true,
    PRIMARY KEY ("id")
);