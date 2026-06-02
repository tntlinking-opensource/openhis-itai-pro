-- cus_order_template_line（建表）
-- Flyway V35

CREATE TABLE IF NOT EXISTS "public"."cus_order_template_line" (
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
    "order_template_id" int8 NOT NULL,
    "line_kind_enum" varchar(255) NOT NULL,
    "medication_definition_id" int8,
    "activity_definition_id" int8,
    "device_definition_id" int8,
    "group_key" varchar(255),
    "sort_order" int4 NOT NULL,
    "default_quantity" numeric(18,2),
    "default_unit_code" varchar(255),
    "default_days" int4,
    "frequency_id" int8,
    "route_code" varchar(255),
    "dosage_text" varchar(255),
    "default_perform_org_id" int8,
    "line_remark" varchar(255),
    CONSTRAINT "cus_order_template_line_pkey" PRIMARY KEY ("id")
);