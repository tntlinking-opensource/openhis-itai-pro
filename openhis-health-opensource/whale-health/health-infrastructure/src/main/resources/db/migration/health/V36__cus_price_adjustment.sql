-- cus_price_adjustment（建表）
-- Flyway V36

CREATE TABLE IF NOT EXISTS "public"."cus_price_adjustment" (
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
    "definition_id" int8 NOT NULL,
    "price_type" varchar(255) NOT NULL,
    "lot_number" varchar(255),
    "old_amount" numeric(19,6) NOT NULL,
    "new_amount" numeric(19,6) NOT NULL,
    "adjustment_reason" varchar(255) NOT NULL,
    "adjustment_time" timestamp(6) NOT NULL,
    "status_enum" varchar(255) NOT NULL,
    "submitter_id" int8,
    "submit_time" timestamp(6),
    "approver_id" int8,
    "approve_time" timestamp(6),
    "approve_comment" varchar(255),
    "operator_id" int8 NOT NULL,
    "org_id" int8 NOT NULL,
    "stock_quantity" int4,
    "adjustment_delta" numeric(19,6),
    "total_impact" numeric(19,6),
    CONSTRAINT "cus_price_adjustment_pkey" PRIMARY KEY ("id")
);