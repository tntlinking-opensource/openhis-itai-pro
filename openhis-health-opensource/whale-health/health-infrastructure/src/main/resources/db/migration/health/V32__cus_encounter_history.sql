-- cus_encounter_history（建表）
-- Flyway V32

CREATE TABLE IF NOT EXISTS "public"."cus_encounter_history" (
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
    "name" varchar(255) NOT NULL,
    "encounter_id" int8 NOT NULL,
    "request_id" int8,
    "request_table_name" varchar(255),
    "original_organization_id" int8,
    "target_organization_id" int8,
    "original_location_id" int8,
    "target_location_id" int8,
    "out_way_code" varchar(255),
    CONSTRAINT "cus_encounter_history_pkey" PRIMARY KEY ("id")
);