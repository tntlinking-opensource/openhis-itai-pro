-- cus_activity_execution_location（建表）
-- Flyway V29

CREATE TABLE IF NOT EXISTS "public"."cus_activity_execution_location" (
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
    "activity_definition_id" int8,
    "charge_item_definition_id" int8,
    "activity_category_code" varchar(255) NOT NULL,
    "execution_org_id" int8 NOT NULL,
    "execution_location_id" int8,
    "start_time" time(6),
    "end_time" time(6),
    "priority" int4 NOT NULL,
    "display_order" int4,
    "remark" varchar(255),
    CONSTRAINT "cus_activity_execution_location_pkey" PRIMARY KEY ("id")
);