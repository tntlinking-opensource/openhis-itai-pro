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
COMMENT ON TABLE cus_price_adjustment IS '自定义 price adjustment';
COMMENT ON COLUMN cus_price_adjustment.id IS '主键';
COMMENT ON COLUMN cus_price_adjustment.tenant_id IS '租户标识';
COMMENT ON COLUMN cus_price_adjustment.created_at IS '创建时间';
COMMENT ON COLUMN cus_price_adjustment.updated_at IS '最后更新时间';
COMMENT ON COLUMN cus_price_adjustment.created_by IS '创建人';
COMMENT ON COLUMN cus_price_adjustment.updated_by IS '最后更新人';
COMMENT ON COLUMN cus_price_adjustment.is_deleted IS '软删标记';
COMMENT ON COLUMN cus_price_adjustment.deleted_at IS '删除时间';
COMMENT ON COLUMN cus_price_adjustment.deleted_by IS '删除人';
COMMENT ON COLUMN cus_price_adjustment.version IS '乐观锁版本号';
COMMENT ON COLUMN cus_price_adjustment.definition_id IS '定义标识';
COMMENT ON COLUMN cus_price_adjustment.price_type IS '价格类型';
COMMENT ON COLUMN cus_price_adjustment.lot_number IS '批号';
COMMENT ON COLUMN cus_price_adjustment.old_amount IS 'old金额';
COMMENT ON COLUMN cus_price_adjustment.new_amount IS 'new金额';
COMMENT ON COLUMN cus_price_adjustment.adjustment_reason IS '调价原因';
COMMENT ON COLUMN cus_price_adjustment.adjustment_time IS '调价时间';
COMMENT ON COLUMN cus_price_adjustment.status_enum IS '状态枚举值';
COMMENT ON COLUMN cus_price_adjustment.submitter_id IS 'submitter标识';
COMMENT ON COLUMN cus_price_adjustment.submit_time IS 'submit时间';
COMMENT ON COLUMN cus_price_adjustment.approver_id IS 'approver标识';
COMMENT ON COLUMN cus_price_adjustment.approve_time IS 'approve时间';
COMMENT ON COLUMN cus_price_adjustment.approve_comment IS '审批意见';
COMMENT ON COLUMN cus_price_adjustment.operator_id IS '操作人标识';
COMMENT ON COLUMN cus_price_adjustment.org_id IS '机构标识';
COMMENT ON COLUMN cus_price_adjustment.stock_quantity IS 'stock数量';
COMMENT ON COLUMN cus_price_adjustment.adjustment_delta IS '调价delta';
COMMENT ON COLUMN cus_price_adjustment.total_impact IS '合计impact';
