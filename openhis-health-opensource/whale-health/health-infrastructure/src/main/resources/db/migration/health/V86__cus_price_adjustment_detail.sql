-- cus_price_adjustment_detail（建表）
-- Flyway V86

CREATE TABLE IF NOT EXISTS cus_price_adjustment_detail (
    id int8 NOT NULL,
    tenant_id int8,
    created_at timestamptz(6) NOT NULL,
    updated_at timestamptz(6),
    created_by int8,
    updated_by int8,
    is_deleted bool NOT NULL DEFAULT false,
    deleted_at timestamptz(6),
    deleted_by int8,
    version int4 NOT NULL DEFAULT 0,
    adjustment_id int8 NOT NULL,
    lot_number varchar(255) NOT NULL,
    quantity numeric(18, 2) NOT NULL,
    old_purchase_amount numeric(19, 6),
    old_selling_amount numeric(19, 6),
    purchase_impact numeric(19, 6),
    selling_impact numeric(19, 6),
    CONSTRAINT cus_price_adjustment_detail_pkey PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_cus_price_adjustment_detail_adjustment_id ON cus_price_adjustment_detail (adjustment_id);

COMMENT ON TABLE cus_price_adjustment_detail IS '调价批号明细（提交时快照盈亏）';
COMMENT ON COLUMN cus_price_adjustment_detail.id IS '主键';
COMMENT ON COLUMN cus_price_adjustment_detail.tenant_id IS '租户标识';
COMMENT ON COLUMN cus_price_adjustment_detail.created_at IS '创建时间';
COMMENT ON COLUMN cus_price_adjustment_detail.updated_at IS '最后更新时间';
COMMENT ON COLUMN cus_price_adjustment_detail.created_by IS '创建人';
COMMENT ON COLUMN cus_price_adjustment_detail.updated_by IS '最后更新人';
COMMENT ON COLUMN cus_price_adjustment_detail.is_deleted IS '软删标记';
COMMENT ON COLUMN cus_price_adjustment_detail.deleted_at IS '删除时间';
COMMENT ON COLUMN cus_price_adjustment_detail.deleted_by IS '删除人';
COMMENT ON COLUMN cus_price_adjustment_detail.version IS '乐观锁版本号';
COMMENT ON COLUMN cus_price_adjustment_detail.adjustment_id IS '调价项目行主键';
COMMENT ON COLUMN cus_price_adjustment_detail.lot_number IS '批号';
COMMENT ON COLUMN cus_price_adjustment_detail.quantity IS '提交时刻影响数量（拆零）';
COMMENT ON COLUMN cus_price_adjustment_detail.old_purchase_amount IS '调前进价';
COMMENT ON COLUMN cus_price_adjustment_detail.old_selling_amount IS '调前售价';
COMMENT ON COLUMN cus_price_adjustment_detail.purchase_impact IS '进价盈亏';
COMMENT ON COLUMN cus_price_adjustment_detail.selling_impact IS '售价盈亏';
