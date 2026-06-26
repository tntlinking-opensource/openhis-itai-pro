-- yb_catalog_upload_record（建表）
-- CHS Flyway V2

CREATE TABLE IF NOT EXISTS yb_catalog_upload_record (
    id                      BIGINT       NOT NULL PRIMARY KEY,
    contract_id             BIGINT       NOT NULL,
    operation               VARCHAR(8)   NOT NULL,
    product_table           VARCHAR(64)  NOT NULL,
    product_id              BIGINT       NOT NULL,
    list_type               VARCHAR(30)  NOT NULL,
    fixmedins_hilist_id     VARCHAR(30)  NOT NULL,
    med_list_codg           VARCHAR(30)  NOT NULL,
    platform_success        BOOLEAN      NOT NULL,
    msgid                   VARCHAR(64),
    request_snapshot        TEXT,
    response_snapshot       TEXT,
    error_msg               VARCHAR(512),
    operator_id             BIGINT,

    created_at              TIMESTAMP    NOT NULL,
    updated_at              TIMESTAMP,
    created_by              BIGINT,
    updated_by              BIGINT,
    is_deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMP,
    deleted_by              BIGINT,
    tenant_id               BIGINT       NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_yb_cat_upload_product
    ON yb_catalog_upload_record (product_table, product_id)
    WHERE is_deleted = FALSE;


CREATE TABLE IF NOT EXISTS yb_settlement_reversal (
    id                      BIGINT       NOT NULL PRIMARY KEY,
    settlement_record_id    BIGINT       NOT NULL,
    revoke_setl_id            VARCHAR(30),
    mdtrt_id                  VARCHAR(30)  NOT NULL,
    psn_no                    VARCHAR(30)  NOT NULL,
    encounter_class           VARCHAR(8)   NOT NULL,
    medfee_sumamt             NUMERIC(18, 2),
    fund_pay_sumamt           NUMERIC(18, 2),
    acct_pay                  NUMERIC(18, 2),
    psn_cash_pay              NUMERIC(18, 2),
    fund_breakdown_json       TEXT,
    reversed_at               TIMESTAMP    NOT NULL,

    created_at              TIMESTAMP    NOT NULL,
    updated_at              TIMESTAMP,
    created_by              BIGINT,
    updated_by              BIGINT,
    is_deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMP,
    deleted_by              BIGINT,
    tenant_id               BIGINT       NOT NULL
);
COMMENT ON TABLE yb_catalog_upload_record IS '医保 catalog upload record';
COMMENT ON COLUMN yb_catalog_upload_record.id IS '主键';
COMMENT ON COLUMN yb_catalog_upload_record.contract_id IS '合同标识';
COMMENT ON COLUMN yb_catalog_upload_record.operation IS '操作类型';
COMMENT ON COLUMN yb_catalog_upload_record.product_table IS '产品表名';
COMMENT ON COLUMN yb_catalog_upload_record.product_id IS 'product标识';
COMMENT ON COLUMN yb_catalog_upload_record.list_type IS 'list类型';
COMMENT ON COLUMN yb_catalog_upload_record.fixmedins_hilist_id IS 'fixmedinshilist标识';
COMMENT ON COLUMN yb_catalog_upload_record.med_list_codg IS '医疗目录编码';
COMMENT ON COLUMN yb_catalog_upload_record.platform_success IS 'platform成功';
COMMENT ON COLUMN yb_catalog_upload_record.msgid IS '报文标识';
COMMENT ON COLUMN yb_catalog_upload_record.request_snapshot IS '申请快照';
COMMENT ON COLUMN yb_catalog_upload_record.response_snapshot IS 'response快照';
COMMENT ON COLUMN yb_catalog_upload_record.error_msg IS '错误msg';
COMMENT ON COLUMN yb_catalog_upload_record.operator_id IS '操作人标识';
COMMENT ON COLUMN yb_catalog_upload_record.created_at IS '创建时间';
COMMENT ON COLUMN yb_catalog_upload_record.updated_at IS '最后更新时间';
COMMENT ON COLUMN yb_catalog_upload_record.created_by IS '创建人';
COMMENT ON COLUMN yb_catalog_upload_record.updated_by IS '最后更新人';
COMMENT ON COLUMN yb_catalog_upload_record.is_deleted IS '软删标记';
COMMENT ON COLUMN yb_catalog_upload_record.deleted_at IS '删除时间';
COMMENT ON COLUMN yb_catalog_upload_record.deleted_by IS '删除人';
COMMENT ON COLUMN yb_catalog_upload_record.tenant_id IS '租户标识';

COMMENT ON TABLE yb_settlement_reversal IS '医保结算reversal';
COMMENT ON COLUMN yb_settlement_reversal.id IS '主键';
COMMENT ON COLUMN yb_settlement_reversal.settlement_record_id IS '结算记录标识';
COMMENT ON COLUMN yb_settlement_reversal.revoke_setl_id IS 'revoke结算标识';
COMMENT ON COLUMN yb_settlement_reversal.mdtrt_id IS '就诊标识';
COMMENT ON COLUMN yb_settlement_reversal.psn_no IS 'psn编号';
COMMENT ON COLUMN yb_settlement_reversal.encounter_class IS '就诊class';
COMMENT ON COLUMN yb_settlement_reversal.medfee_sumamt IS '医疗费总额';
COMMENT ON COLUMN yb_settlement_reversal.fund_pay_sumamt IS 'fund支付sumamt';
COMMENT ON COLUMN yb_settlement_reversal.acct_pay IS 'acct支付';
COMMENT ON COLUMN yb_settlement_reversal.psn_cash_pay IS 'psncash支付';
COMMENT ON COLUMN yb_settlement_reversal.fund_breakdown_json IS '基金分项JSON';
COMMENT ON COLUMN yb_settlement_reversal.reversed_at IS 'reversed时间';
COMMENT ON COLUMN yb_settlement_reversal.created_at IS '创建时间';
COMMENT ON COLUMN yb_settlement_reversal.updated_at IS '最后更新时间';
COMMENT ON COLUMN yb_settlement_reversal.created_by IS '创建人';
COMMENT ON COLUMN yb_settlement_reversal.updated_by IS '最后更新人';
COMMENT ON COLUMN yb_settlement_reversal.is_deleted IS '软删标记';
COMMENT ON COLUMN yb_settlement_reversal.deleted_at IS '删除时间';
COMMENT ON COLUMN yb_settlement_reversal.deleted_by IS '删除人';
COMMENT ON COLUMN yb_settlement_reversal.tenant_id IS '租户标识';
