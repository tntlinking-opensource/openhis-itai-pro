-- wkf_supply_request（建表）
-- Flyway V82

CREATE TABLE IF NOT EXISTS "public"."wkf_supply_request" (
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
    "bus_no" varchar(255) NOT NULL,
    "original_bus_no" varchar(255),
    "type_enum" varchar(255) NOT NULL,
    "status_enum" varchar(255) NOT NULL,
    "intent_enum" varchar(255),
    "category_enum" varchar(255) NOT NULL,
    "priority_enum" varchar(255),
    "summary_id" int8,
    "deliver_id" int8,
    "patient_id" int8,
    "item_table" varchar(255) NOT NULL,
    "item_quantity" numeric(18,2) NOT NULL,
    "total_quantity" numeric(18,2),
    "item_id" int8 NOT NULL,
    "unit_code" varchar(255) NOT NULL,
    "occurrence_time" timestamptz(6),
    "practitioner_id" int8,
    "supplier_id" int8,
    "reason" varchar(255),
    "source_type_enum" varchar(255) NOT NULL,
    "source_location_id" int8 NOT NULL,
    "purpose_type_enum" varchar(255) NOT NULL,
    "purpose_location_id" int8 NOT NULL,
    "approver_id" int8,
    "approval_time" timestamptz(6),
    "applicant_id" int8 NOT NULL,
    "apply_time" timestamptz(6),
    "start_time" timestamptz(6),
    "end_time" timestamptz(6),
    "lot_number" varchar(255),
    "trace_no" varchar(50000),
    "price" numeric(19,6),
    "total_price" numeric(19,6),
    "remake" varchar(255),
    CONSTRAINT "wkf_supply_request_pkey" PRIMARY KEY ("id")
);

-- 盘点单：盈亏金额持久化列（与 AS 约定 profit_loss_price 对齐）
ALTER TABLE "public"."wkf_supply_request"
    ADD COLUMN IF NOT EXISTS "profit_loss_price" numeric(19, 6);

COMMENT ON COLUMN "public"."wkf_supply_request"."profit_loss_price" IS '盘点盈亏金额；盘盈为正、盘亏为负；非盘点类型可空';