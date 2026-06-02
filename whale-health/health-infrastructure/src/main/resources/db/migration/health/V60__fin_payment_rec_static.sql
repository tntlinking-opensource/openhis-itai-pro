-- fin_payment_rec_static（建表）
-- Flyway V60

CREATE TABLE IF NOT EXISTS "public"."fin_payment_rec_static" (
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
    "reconciliation_id" int8 NOT NULL,
    "method_code" varchar(255) NOT NULL,
    "type_code" varchar(255) NOT NULL,
    "type_name" varchar(255),
    "amount" numeric(19,6) NOT NULL,
    "charge_item_ids" varchar(255),
    CONSTRAINT "fin_payment_rec_static_pkey" PRIMARY KEY ("id")
);