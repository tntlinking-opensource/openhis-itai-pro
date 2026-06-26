-- fin_payment_rec_detail（建表）
-- Flyway V50

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
COMMENT ON TABLE fin_payment_rec_detail IS '财务 payment rec detail';
COMMENT ON COLUMN fin_payment_rec_detail.id IS '主键';
COMMENT ON COLUMN fin_payment_rec_detail.tenant_id IS '租户标识';
COMMENT ON COLUMN fin_payment_rec_detail.created_at IS '创建时间';
COMMENT ON COLUMN fin_payment_rec_detail.updated_at IS '最后更新时间';
COMMENT ON COLUMN fin_payment_rec_detail.created_by IS '创建人';
COMMENT ON COLUMN fin_payment_rec_detail.updated_by IS '最后更新人';
COMMENT ON COLUMN fin_payment_rec_detail.is_deleted IS '软删标记';
COMMENT ON COLUMN fin_payment_rec_detail.deleted_at IS '删除时间';
COMMENT ON COLUMN fin_payment_rec_detail.deleted_by IS '删除人';
COMMENT ON COLUMN fin_payment_rec_detail.version IS '乐观锁版本号';
COMMENT ON COLUMN fin_payment_rec_detail.reconciliation_id IS '对账标识';
COMMENT ON COLUMN fin_payment_rec_detail.predecessor_id IS 'predecessor标识';
COMMENT ON COLUMN fin_payment_rec_detail.target_enum IS 'target枚举值';
COMMENT ON COLUMN fin_payment_rec_detail.account_id IS '账户标识';
COMMENT ON COLUMN fin_payment_rec_detail.account_code IS '账户编码';
COMMENT ON COLUMN fin_payment_rec_detail.pay_enum IS '支付枚举值';
COMMENT ON COLUMN fin_payment_rec_detail.pay_level_enum IS '支付level枚举值';
COMMENT ON COLUMN fin_payment_rec_detail.amount IS '金额';
COMMENT ON COLUMN fin_payment_rec_detail.return_amount IS 'return金额';
COMMENT ON COLUMN fin_payment_rec_detail.charge_amount IS '收费金额';
COMMENT ON COLUMN fin_payment_rec_detail.pay_trans_no IS '支付trans编号';
COMMENT ON COLUMN fin_payment_rec_detail.pay_trans_text IS '支付trans文本';
COMMENT ON COLUMN fin_payment_rec_detail.pay_trans_date IS '支付trans日期';
COMMENT ON COLUMN fin_payment_rec_detail.before_balance IS 'before余额';
COMMENT ON COLUMN fin_payment_rec_detail.after_balance IS 'after余额';
COMMENT ON COLUMN fin_payment_rec_detail.result_enum IS 'result枚举值';
