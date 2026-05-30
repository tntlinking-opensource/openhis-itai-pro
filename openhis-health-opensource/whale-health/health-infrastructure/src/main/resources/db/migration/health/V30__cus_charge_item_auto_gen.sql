-- cus_charge_item_auto_gen（建表）
-- Flyway V30

CREATE TABLE IF NOT EXISTS "public"."cus_charge_item_auto_gen" (
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
    "rule_name" varchar(255) NOT NULL,
    "status_enum" varchar(255) NOT NULL,
    "trigger_source_table" varchar(255) NOT NULL,
    "trigger_source_id" int8 NOT NULL,
    "target_definition_id" int8 NOT NULL,
    "performing_org_id" int8,
    "quantity_value" numeric(18,2) NOT NULL,
    "priority" int4 NOT NULL,
    "remark" varchar(255),
    CONSTRAINT "cus_charge_item_auto_gen_pkey" PRIMARY KEY ("id")
);