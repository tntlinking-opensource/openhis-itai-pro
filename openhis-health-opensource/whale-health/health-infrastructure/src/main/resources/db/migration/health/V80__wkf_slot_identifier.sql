-- wkf_slot_identifier（建表）
-- Flyway V80

CREATE TABLE IF NOT EXISTS "public"."wkf_slot_identifier" (
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
    "slot_id" int8 NOT NULL,
    "id_system" varchar(255) NOT NULL,
    "id_value" varchar(255) NOT NULL,
    "use_code" varchar(255),
    CONSTRAINT "wkf_slot_identifier_pkey" PRIMARY KEY ("id")
);