-- dia_body_structure（建表）
-- Flyway V42

CREATE TABLE IF NOT EXISTS "public"."dia_body_structure" (
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
    "bus_no" varchar(255) NOT NULL,
    "name" varchar(255) NOT NULL,
    "status_enum" varchar(255) NOT NULL,
    "py_str" varchar(255),
    "wb_str" varchar(255),
    "parent_id" int8,
    CONSTRAINT "dia_body_structure_pkey" PRIMARY KEY ("id")
);