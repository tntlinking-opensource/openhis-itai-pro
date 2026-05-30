-- emg_encounter_companion（建表）
-- Flyway V43

CREATE TABLE IF NOT EXISTS "public"."emg_encounter_companion" (
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
    "patient_id" int8 NOT NULL,
    "name" varchar(128) NOT NULL,
    "id_card" varchar(32),
    "gender_enum" varchar(16),
    "phone" varchar(32),
    "relationship_code" varchar(64),
    "status_enum" varchar(32) NOT NULL,
    "id_card_source_enum" varchar(32) NOT NULL,
    "wristband_printed_at" timestamptz(6),
    "remark" text,
    PRIMARY KEY ("id")
);