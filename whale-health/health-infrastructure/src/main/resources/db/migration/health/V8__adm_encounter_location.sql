-- adm_encounter_location（建表）
-- Flyway V8

CREATE TABLE IF NOT EXISTS "public"."adm_encounter_location" (
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
    "location_id" int8 NOT NULL,
    "form_enum" varchar(255),
    "status_enum" varchar(255) NOT NULL,
    "start_time" timestamptz(6),
    "end_time" timestamptz(6),
    CONSTRAINT "adm_encounter_location_pkey" PRIMARY KEY ("id")
);