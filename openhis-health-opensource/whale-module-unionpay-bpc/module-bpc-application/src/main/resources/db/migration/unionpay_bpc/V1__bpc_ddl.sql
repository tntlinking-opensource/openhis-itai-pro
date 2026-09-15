-- UnionPay BPC Flyway V1
-- upc_bpc_order
CREATE TABLE upc_bpc_order (
    id                BIGINT       NOT NULL PRIMARY KEY,
    tenant_id         BIGINT       NOT NULL,
    payment_id        BIGINT       NOT NULL,
    detail_index      INT          NOT NULL DEFAULT 0,
    mer_trade_no      VARCHAR(64)  NOT NULL,
    trade_no          VARCHAR(64),
    tran_type         VARCHAR(8)   NOT NULL,
    txn_amt_fen       BIGINT       NOT NULL,
    paid_amt_fen      BIGINT,
    pay_type          VARCHAR(16),
    status            VARCHAR(16)  NOT NULL,
    resp_code         VARCHAR(8),
    resp_msg          VARCHAR(256),
    profile_id        VARCHAR(32)  NOT NULL,
    pos_no            VARCHAR(32),
    scan_code_digest  VARCHAR(64),
    is_deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMP    NOT NULL,
    created_by        BIGINT,
    updated_at        TIMESTAMP,
    updated_by        BIGINT,
    deleted_at        TIMESTAMP,
    deleted_by        BIGINT,
    CONSTRAINT uk_upc_bpc_order_mer UNIQUE (tenant_id, mer_trade_no)
);
CREATE INDEX idx_upc_bpc_order_payment ON upc_bpc_order (tenant_id, payment_id);
CREATE INDEX idx_upc_bpc_order_trade ON upc_bpc_order (tenant_id, trade_no);
COMMENT ON TABLE upc_bpc_order IS 'upc_bpc_order';
COMMENT ON COLUMN upc_bpc_order.id IS '主键';
COMMENT ON COLUMN upc_bpc_order.tenant_id IS '租户标识';
COMMENT ON COLUMN upc_bpc_order.payment_id IS '支付标识';
COMMENT ON COLUMN upc_bpc_order.detail_index IS '明细index';
COMMENT ON COLUMN upc_bpc_order.mer_trade_no IS 'mertrade编号';
COMMENT ON COLUMN upc_bpc_order.trade_no IS 'trade编号';
COMMENT ON COLUMN upc_bpc_order.tran_type IS 'tran类型';
COMMENT ON COLUMN upc_bpc_order.txn_amt_fen IS '院内下单金额（分）；不被 POS 实付覆盖';
COMMENT ON COLUMN upc_bpc_order.paid_amt_fen IS '银联/POS 回传实付金额（分）；可小于下单 txn_amt_fen';
COMMENT ON COLUMN upc_bpc_order.pay_type IS '支付类型';
COMMENT ON COLUMN upc_bpc_order.status IS '状态';
COMMENT ON COLUMN upc_bpc_order.resp_code IS 'resp编码';
COMMENT ON COLUMN upc_bpc_order.resp_msg IS '响应消息';
COMMENT ON COLUMN upc_bpc_order.profile_id IS 'profile标识';
COMMENT ON COLUMN upc_bpc_order.pos_no IS 'pos编号';
COMMENT ON COLUMN upc_bpc_order.scan_code_digest IS 'scan编码digest';
COMMENT ON COLUMN upc_bpc_order.is_deleted IS '软删标记';
COMMENT ON COLUMN upc_bpc_order.created_at IS '创建时间';
COMMENT ON COLUMN upc_bpc_order.created_by IS '创建人';
COMMENT ON COLUMN upc_bpc_order.updated_at IS '最后更新时间';
COMMENT ON COLUMN upc_bpc_order.updated_by IS '最后更新人';
COMMENT ON COLUMN upc_bpc_order.deleted_at IS '软删时间';
COMMENT ON COLUMN upc_bpc_order.deleted_by IS '软删操作人';
