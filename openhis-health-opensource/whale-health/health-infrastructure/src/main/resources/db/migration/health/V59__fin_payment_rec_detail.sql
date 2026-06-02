-- fin_payment_rec_detail（建表）
-- Flyway V59

CREATE TABLE IF NOT EXISTS "public"."fin_payment_rec_detail" (
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
    "predecessor_id" int8,
    "target_enum" varchar(255),
    "account_id" int8 NOT NULL,
    "account_code" varchar(255),
    "pay_enum" varchar(255) NOT NULL,
    "pay_level_enum" varchar(255),
    "amount" numeric(19,6) NOT NULL,
    "return_amount" numeric(19,6),
    "charge_amount" numeric(19,6),
    "pay_trans_no" varchar(255),
    "pay_trans_text" varchar(255),
    "pay_trans_date" timestamptz(6),
    "before_balance" numeric(19,6),
    "after_balance" numeric(19,6),
    "result_enum" varchar(255) NOT NULL,
    CONSTRAINT "fin_payment_rec_detail_pkey" PRIMARY KEY ("id")
);