-- fin_payment_rec_static（建表）
-- Flyway V51

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
    "charge_item_ids" text,
    CONSTRAINT "fin_payment_rec_static_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE fin_payment_rec_static IS '财务 payment rec static';
COMMENT ON COLUMN fin_payment_rec_static.id IS '主键';
COMMENT ON COLUMN fin_payment_rec_static.tenant_id IS '租户标识';
COMMENT ON COLUMN fin_payment_rec_static.created_at IS '创建时间';
COMMENT ON COLUMN fin_payment_rec_static.updated_at IS '最后更新时间';
COMMENT ON COLUMN fin_payment_rec_static.created_by IS '创建人';
COMMENT ON COLUMN fin_payment_rec_static.updated_by IS '最后更新人';
COMMENT ON COLUMN fin_payment_rec_static.is_deleted IS '软删标记';
COMMENT ON COLUMN fin_payment_rec_static.deleted_at IS '删除时间';
COMMENT ON COLUMN fin_payment_rec_static.deleted_by IS '删除人';
COMMENT ON COLUMN fin_payment_rec_static.version IS '乐观锁版本号';
COMMENT ON COLUMN fin_payment_rec_static.reconciliation_id IS '对账标识';
COMMENT ON COLUMN fin_payment_rec_static.method_code IS '支付分类统计快照';
COMMENT ON COLUMN fin_payment_rec_static.type_code IS '类型编码';
COMMENT ON COLUMN fin_payment_rec_static.type_name IS '类型名称';
COMMENT ON COLUMN fin_payment_rec_static.amount IS '金额';
COMMENT ON COLUMN fin_payment_rec_static.charge_item_ids IS '收费项ids';
