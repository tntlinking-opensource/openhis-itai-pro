-- fin_payment_reconciliation（建表）
-- Flyway V52

CREATE TABLE IF NOT EXISTS "public"."fin_payment_reconciliation" (
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
    "status_enum" varchar(255) NOT NULL,
    "relation_id" int8,
    "payment_no" varchar(255) NOT NULL,
    "payment_enum" varchar(255) NOT NULL,
    "kind_enum" varchar(255) NOT NULL,
    "enterer_id" int8 NOT NULL,
    "issuer_enum" varchar(255),
    "patient_id" int8 NOT NULL,
    "practitioner_id" int8,
    "outcome_enum" varchar(255),
    "location_id" int8,
    "expiration_date" timestamptz(6),
    "tendered_amount" numeric(19,6) NOT NULL,
    "returned_amount" numeric(19,6),
    "display_amount" numeric(19,6) NOT NULL,
    "print_count" int4 NOT NULL,
    "contract_no" varchar(255),
    "charge_item_ids" text NOT NULL,
    "encounter_id" int8 NOT NULL,
    "bill_date" timestamptz(6),
    "invoice_id" int8,
    "account_id" int8,
    "yb_clear_flag" bool,
    "cashier_settlement_id" int8,
    CONSTRAINT "fin_payment_reconciliation_pkey" PRIMARY KEY ("id")
);

CREATE INDEX IF NOT EXISTS idx_fin_payment_cashier_settlement
    ON fin_payment_reconciliation (enterer_id, bill_date, cashier_settlement_id)
    WHERE is_deleted = FALSE;
COMMENT ON TABLE fin_payment_reconciliation IS '财务 payment reconciliation';
COMMENT ON COLUMN fin_payment_reconciliation.id IS '主键';
COMMENT ON COLUMN fin_payment_reconciliation.tenant_id IS '租户标识';
COMMENT ON COLUMN fin_payment_reconciliation.created_at IS '创建时间';
COMMENT ON COLUMN fin_payment_reconciliation.updated_at IS '最后更新时间';
COMMENT ON COLUMN fin_payment_reconciliation.created_by IS '创建人';
COMMENT ON COLUMN fin_payment_reconciliation.updated_by IS '最后更新人';
COMMENT ON COLUMN fin_payment_reconciliation.is_deleted IS '软删标记';
COMMENT ON COLUMN fin_payment_reconciliation.deleted_at IS '删除时间';
COMMENT ON COLUMN fin_payment_reconciliation.deleted_by IS '删除人';
COMMENT ON COLUMN fin_payment_reconciliation.version IS '乐观锁版本号';
COMMENT ON COLUMN fin_payment_reconciliation.status_enum IS '支付流水聚合根';
COMMENT ON COLUMN fin_payment_reconciliation.relation_id IS 'relation 标识';
COMMENT ON COLUMN fin_payment_reconciliation.payment_no IS 'payment 编号';
COMMENT ON COLUMN fin_payment_reconciliation.payment_enum IS 'payment 枚举值';
COMMENT ON COLUMN fin_payment_reconciliation.kind_enum IS 'kind 枚举值';
COMMENT ON COLUMN fin_payment_reconciliation.enterer_id IS 'enterer 标识';
COMMENT ON COLUMN fin_payment_reconciliation.issuer_enum IS 'issuer 枚举值';
COMMENT ON COLUMN fin_payment_reconciliation.patient_id IS 'patient 标识';
COMMENT ON COLUMN fin_payment_reconciliation.practitioner_id IS 'practitioner 标识';
COMMENT ON COLUMN fin_payment_reconciliation.outcome_enum IS 'outcome 枚举值';
COMMENT ON COLUMN fin_payment_reconciliation.location_id IS 'location 标识';
COMMENT ON COLUMN fin_payment_reconciliation.expiration_date IS 'expiration日期';
COMMENT ON COLUMN fin_payment_reconciliation.tendered_amount IS 'tendered 金额（元，与库 numeric(19,6) 一致）';
COMMENT ON COLUMN fin_payment_reconciliation.returned_amount IS 'returned 金额';
COMMENT ON COLUMN fin_payment_reconciliation.display_amount IS 'display 金额';
COMMENT ON COLUMN fin_payment_reconciliation.print_count IS 'print 数量';
COMMENT ON COLUMN fin_payment_reconciliation.contract_no IS 'contract 编号';
COMMENT ON COLUMN fin_payment_reconciliation.charge_item_ids IS '收费项ids';
COMMENT ON COLUMN fin_payment_reconciliation.encounter_id IS 'encounter 标识';
COMMENT ON COLUMN fin_payment_reconciliation.bill_date IS 'bill日期';
COMMENT ON COLUMN fin_payment_reconciliation.invoice_id IS 'invoice 标识';
COMMENT ON COLUMN fin_payment_reconciliation.account_id IS 'account 标识';
COMMENT ON COLUMN fin_payment_reconciliation.yb_clear_flag IS 'ybClear 标志位';
COMMENT ON COLUMN fin_payment_reconciliation.cashier_settlement_id IS '收费员交账头 ID；非空表示已交账';
