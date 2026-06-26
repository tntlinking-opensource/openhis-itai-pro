-- V83：支付/医保结算扩展 + 收费员交账（合并原 V83/V84）

CREATE TABLE IF NOT EXISTS fin_payment_yb_settlement (
    id                      BIGINT       NOT NULL PRIMARY KEY,
    reconciliation_id       BIGINT       NOT NULL,
    setl_id                 VARCHAR(30)  NOT NULL,
    chrg_bchno              VARCHAR(30),
    mdtrt_id                VARCHAR(30),
    settlement_record_id    BIGINT,
    account_id              BIGINT,
    status                  VARCHAR(16)  NOT NULL DEFAULT 'CONFIRMED',

    created_at              TIMESTAMPTZ  NOT NULL,
    updated_at              TIMESTAMPTZ,
    created_by              BIGINT,
    updated_by              BIGINT,
    is_deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMPTZ,
    deleted_by              BIGINT,
    tenant_id               BIGINT
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_fin_payment_yb_settlement_payment_setl
    ON fin_payment_yb_settlement (reconciliation_id, setl_id, tenant_id)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_fin_payment_yb_settlement_payment
    ON fin_payment_yb_settlement (reconciliation_id, status)
    WHERE is_deleted = FALSE;
COMMENT ON TABLE fin_payment_yb_settlement IS '支付流水关联的医保正式结算凭证（一行一个 setl_id）';
COMMENT ON COLUMN fin_payment_yb_settlement.id IS '主键';
COMMENT ON COLUMN fin_payment_yb_settlement.reconciliation_id IS '对账标识';
COMMENT ON COLUMN fin_payment_yb_settlement.setl_id IS '平台 setl_id';
COMMENT ON COLUMN fin_payment_yb_settlement.chrg_bchno IS '收费批次号';
COMMENT ON COLUMN fin_payment_yb_settlement.mdtrt_id IS '就诊标识';
COMMENT ON COLUMN fin_payment_yb_settlement.settlement_record_id IS 'yb_settlement_record.id（CHS 侧）';
COMMENT ON COLUMN fin_payment_yb_settlement.account_id IS '账户标识';
COMMENT ON COLUMN fin_payment_yb_settlement.status IS '状态';
COMMENT ON COLUMN fin_payment_yb_settlement.created_at IS '创建时间';
COMMENT ON COLUMN fin_payment_yb_settlement.updated_at IS '最后更新时间';
COMMENT ON COLUMN fin_payment_yb_settlement.created_by IS '创建人';
COMMENT ON COLUMN fin_payment_yb_settlement.updated_by IS '最后更新人';
COMMENT ON COLUMN fin_payment_yb_settlement.is_deleted IS '软删标记';
COMMENT ON COLUMN fin_payment_yb_settlement.deleted_at IS '删除时间';
COMMENT ON COLUMN fin_payment_yb_settlement.deleted_by IS '删除人';
COMMENT ON COLUMN fin_payment_yb_settlement.tenant_id IS '租户标识';
