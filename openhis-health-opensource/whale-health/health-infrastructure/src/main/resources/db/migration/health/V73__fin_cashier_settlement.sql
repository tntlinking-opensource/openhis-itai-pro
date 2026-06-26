CREATE TABLE IF NOT EXISTS fin_cashier_settlement (
    id              BIGINT          NOT NULL,
    tenant_id       BIGINT,
    created_at      TIMESTAMPTZ(6)  NOT NULL,
    updated_at      TIMESTAMPTZ(6),
    created_by      BIGINT,
    updated_by      BIGINT,
    is_deleted      BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at      TIMESTAMPTZ(6),
    deleted_by      BIGINT,
    version         INT4            NOT NULL DEFAULT 0,
    bus_no          VARCHAR(64)     NOT NULL,
    type_enum       VARCHAR(32)     NOT NULL,
    status_enum     VARCHAR(32)     NOT NULL,
    settled_at      TIMESTAMPTZ(6)  NOT NULL,
    org_id          BIGINT          NOT NULL,
    location_id     BIGINT,
    cashier_id      BIGINT          NOT NULL,
    CONSTRAINT fin_cashier_settlement_pkey PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_fin_cashier_settlement_bus_no
    ON fin_cashier_settlement (tenant_id, bus_no)
    WHERE is_deleted = FALSE;
COMMENT ON TABLE fin_cashier_settlement IS '收费员交账头表（只增不改）';
COMMENT ON COLUMN fin_cashier_settlement.id IS '主键';
COMMENT ON COLUMN fin_cashier_settlement.tenant_id IS '租户标识';
COMMENT ON COLUMN fin_cashier_settlement.created_at IS '创建时间';
COMMENT ON COLUMN fin_cashier_settlement.updated_at IS '最后更新时间';
COMMENT ON COLUMN fin_cashier_settlement.created_by IS '创建人';
COMMENT ON COLUMN fin_cashier_settlement.updated_by IS '最后更新人';
COMMENT ON COLUMN fin_cashier_settlement.is_deleted IS '软删标记';
COMMENT ON COLUMN fin_cashier_settlement.deleted_at IS '删除时间';
COMMENT ON COLUMN fin_cashier_settlement.deleted_by IS '删除人';
COMMENT ON COLUMN fin_cashier_settlement.version IS '乐观锁版本号';
COMMENT ON COLUMN fin_cashier_settlement.bus_no IS '交账单号';
COMMENT ON COLUMN fin_cashier_settlement.type_enum IS '类型枚举值';
COMMENT ON COLUMN fin_cashier_settlement.status_enum IS 'HANDED_OVER（已交账）';
COMMENT ON COLUMN fin_cashier_settlement.settled_at IS '交账提交时刻';
COMMENT ON COLUMN fin_cashier_settlement.org_id IS '交账所属科室';
COMMENT ON COLUMN fin_cashier_settlement.location_id IS '收费窗口（可选）';
COMMENT ON COLUMN fin_cashier_settlement.cashier_id IS '交账经办人';
