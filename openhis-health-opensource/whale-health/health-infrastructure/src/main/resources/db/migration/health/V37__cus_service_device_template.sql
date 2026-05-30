-- cus_service_device_template（建表）
-- Flyway V37

CREATE TABLE IF NOT EXISTS "public"."cus_service_device_template" (
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
    "range_code" varchar(255) NOT NULL,
    "type_code" varchar(255) NOT NULL,
    "item_table" varchar(255) NOT NULL,
    "item_id" varchar(255) NOT NULL,
    "dev_act_table" varchar(255) NOT NULL,
    "dev_act_id" int8 NOT NULL,
    "quantity" numeric(18,2) NOT NULL,
    "unit_code" varchar(255) NOT NULL,
    "status_enum" varchar(255) NOT NULL,
    CONSTRAINT "cus_service_device_template_pkey" PRIMARY KEY ("id")
);