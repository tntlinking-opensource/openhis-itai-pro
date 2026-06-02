-- inventory_lock（建表）
-- Flyway V62

CREATE TABLE IF NOT EXISTS "public"."inventory_lock" (
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
    "inventory_id" int8 NOT NULL,
    "lock_flag" bool NOT NULL,
    "status" varchar(255) NOT NULL,
    "bus_no" varchar(255) NOT NULL,
    "order_flag" int4 NOT NULL,
    CONSTRAINT "inventory_lock_pkey" PRIMARY KEY ("id")
);