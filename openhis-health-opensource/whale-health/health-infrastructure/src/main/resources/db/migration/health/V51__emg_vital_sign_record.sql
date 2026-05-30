-- emg_vital_sign_record（建表）
-- Flyway V51

CREATE TABLE IF NOT EXISTS "public"."emg_vital_sign_record" (
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

    "triage_id" int8 NOT NULL,
    "item_code" varchar(255) NOT NULL,
    "item_name" varchar(255) NOT NULL,
    "value" varchar(255) NOT NULL,
    "unit" varchar(255) NOT NULL,
    "record_time" timestamptz(6) NOT NULL,

    CONSTRAINT "emg_vital_sign_record_pkey" PRIMARY KEY ("id")
);
