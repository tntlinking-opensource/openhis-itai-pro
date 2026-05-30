-- emg_visit_restriction（建表）
-- Flyway V50

CREATE TABLE IF NOT EXISTS "public"."emg_visit_restriction" (
    "id" int8 NOT NULL,
    "tenant_id" int8,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,

    "encounter_id" int8 NOT NULL,
    "restriction_type_enum" varchar(32) NOT NULL,
    "reason_text" text,
    "status_enum" varchar(32) NOT NULL,
    "lifted_at" timestamptz(6),
    "operated_by" int8,
    PRIMARY KEY ("id")
);