-- cln_procedure_performer（建表）
-- Flyway V28

CREATE TABLE IF NOT EXISTS "public"."cln_procedure_performer" (
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
    "procedure_id" int8 NOT NULL,
    "function_code" varchar(255),
    "start_time" timestamptz(6) NOT NULL,
    "end_time" timestamptz(6),
    "practitioner_id" int8 NOT NULL,
    "org_from_id" varchar(255),
    CONSTRAINT "cln_procedure_performer_pkey" PRIMARY KEY ("id")
);