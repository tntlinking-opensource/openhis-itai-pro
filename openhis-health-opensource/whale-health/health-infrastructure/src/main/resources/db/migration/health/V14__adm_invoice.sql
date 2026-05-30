-- adm_invoice（建表）
-- Flyway V14

CREATE TABLE IF NOT EXISTS "public"."adm_invoice" (
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
    "patient_id" int8,
    "reconciliation_id" int8,
    "payment_id" int8,
    "bus_no" varchar(255),
    "bill_no" varchar(255),
    "type_code" varchar(255),
    "cancelled_reason" varchar(255),
    "charge_item_ids" varchar(255),
    "payment_terms" varchar(255),
    "amount" numeric(19,6),
    "status_enum" varchar(255),
    "issued_at" timestamptz(6),
    CONSTRAINT "adm_invoice_pkey" PRIMARY KEY ("id")
);