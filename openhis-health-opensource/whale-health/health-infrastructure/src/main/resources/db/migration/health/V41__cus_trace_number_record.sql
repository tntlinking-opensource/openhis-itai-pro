-- cus_trace_number_record（建表）
-- Flyway V41

CREATE TABLE IF NOT EXISTS "public"."cus_trace_number_record" (
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
    "item_table" varchar(255) NOT NULL,
    "item_id" int8 NOT NULL,
    "location_type_enum" varchar(255) NOT NULL,
    "location_id" int8 NOT NULL,
    "location_store_id" int8,
    "lot_number" varchar(255) NOT NULL,
    "trace_no" varchar(50000) NOT NULL,
    "status_enum" varchar(255) NOT NULL,
    "unit_code" varchar(255),
    "operation_type" varchar(64) NOT NULL,
    "bus_no" varchar(255),
    CONSTRAINT "cus_trace_number_record_pkey" PRIMARY KEY ("id")
);