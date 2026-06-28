-- cus_price_adjustment（建表 · 单据型调价项目行最终态）
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
    "bus_no" varchar(255) NOT NULL,
    "item_table" varchar(255) NOT NULL,
    "item_id" int8 NOT NULL,
    "new_purchase_amount" numeric(19, 6),
    "new_selling_amount" numeric(19, 6),
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
    CONSTRAINT "cus_price_adjustment_pkey" PRIMARY KEY ("id")
);

CREATE INDEX IF NOT EXISTS idx_cus_price_adjustment_bus_no ON cus_price_adjustment (bus_no);
CREATE INDEX IF NOT EXISTS idx_cus_price_adjustment_item_pending ON cus_price_adjustment (item_table, item_id, status_enum);

COMMENT ON TABLE cus_price_adjustment IS '调价单项目行（同 bus_no 多行）';
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
COMMENT ON COLUMN cus_price_adjustment.bus_no IS '调价单号';
COMMENT ON COLUMN cus_price_adjustment.item_table IS '项目物理表（药品/耗材定义表）';
COMMENT ON COLUMN cus_price_adjustment.item_id IS '项目定义主键';
COMMENT ON COLUMN cus_price_adjustment.new_purchase_amount IS '目标进价（可空表示不调进价）';
COMMENT ON COLUMN cus_price_adjustment.new_selling_amount IS '目标售价（可空表示不调售价）';
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
