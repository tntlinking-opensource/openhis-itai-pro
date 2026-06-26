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
    "charge_item_ids" text,
    "payment_terms" varchar(255),
    "amount" numeric(19,6),
    "status_enum" varchar(255),
    "issued_at" timestamptz(6),
    CONSTRAINT "adm_invoice_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE adm_invoice IS '管理域 invoice';
COMMENT ON COLUMN adm_invoice.id IS '主键';
COMMENT ON COLUMN adm_invoice.tenant_id IS '租户标识';
COMMENT ON COLUMN adm_invoice.created_at IS '创建时间';
COMMENT ON COLUMN adm_invoice.updated_at IS '最后更新时间';
COMMENT ON COLUMN adm_invoice.created_by IS '创建人';
COMMENT ON COLUMN adm_invoice.updated_by IS '最后更新人';
COMMENT ON COLUMN adm_invoice.is_deleted IS '软删标记';
COMMENT ON COLUMN adm_invoice.deleted_at IS '删除时间';
COMMENT ON COLUMN adm_invoice.deleted_by IS '删除人';
COMMENT ON COLUMN adm_invoice.version IS '乐观锁版本号';
COMMENT ON COLUMN adm_invoice.patient_id IS '票据聚合根：草稿开具等写侧由 { com.openhis.health.domain.administration.invoice.service.InvoiceDomainService} 编排';
COMMENT ON COLUMN adm_invoice.reconciliation_id IS 'reconciliation 标识';
COMMENT ON COLUMN adm_invoice.payment_id IS 'payment 标识';
COMMENT ON COLUMN adm_invoice.bus_no IS '业务流水号（新建必填）';
COMMENT ON COLUMN adm_invoice.bill_no IS 'bill 编号';
COMMENT ON COLUMN adm_invoice.type_code IS 'type 代码';
COMMENT ON COLUMN adm_invoice.cancelled_reason IS 'cancelled原因';
COMMENT ON COLUMN adm_invoice.charge_item_ids IS '收费项ids';
COMMENT ON COLUMN adm_invoice.payment_terms IS '支付terms';
COMMENT ON COLUMN adm_invoice.amount IS '金额';
COMMENT ON COLUMN adm_invoice.status_enum IS 'status 枚举值';
COMMENT ON COLUMN adm_invoice.issued_at IS 'issued 时间';
