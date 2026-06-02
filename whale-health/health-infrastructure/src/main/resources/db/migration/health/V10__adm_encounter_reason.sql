-- adm_encounter_reason（建表）
-- Flyway V10

CREATE TABLE IF NOT EXISTS "public"."adm_encounter_reason" (
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
    "use_code" varchar(255) NOT NULL,
    "reason_table" varchar(255),
    "reason_id" int8,
    "description" varchar(255),
    CONSTRAINT "adm_encounter_reason_pkey" PRIMARY KEY ("id")
);