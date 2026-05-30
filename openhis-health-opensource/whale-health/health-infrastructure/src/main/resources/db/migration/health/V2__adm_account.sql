-- adm_account（建表）
-- Flyway V2

CREATE TABLE IF NOT EXISTS "public"."adm_account" (
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
    "patient_id" int8 NOT NULL,
    "encounter_id" int8 NOT NULL,
    "type_code" varchar(255) NOT NULL,
    "bus_no" varchar(255),
    "name" varchar(255),
    "balance" numeric(19,6),
    "billing_status_enum" varchar(255),
    "contract_no" varchar(255),
    "yb_area_no" varchar(255),
    "encounter_flag" bool,
    "insutype" varchar(255),
    "default_flag" bool,
    "status_enum" varchar(255),
    CONSTRAINT "adm_account_pkey" PRIMARY KEY ("id")
);