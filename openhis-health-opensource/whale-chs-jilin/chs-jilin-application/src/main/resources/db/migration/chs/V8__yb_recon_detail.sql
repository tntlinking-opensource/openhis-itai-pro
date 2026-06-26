-- Wave 3 扩展：3202 明细对账本地记录

CREATE TABLE IF NOT EXISTS yb_recon_detail (
    id                  BIGINT         NOT NULL PRIMARY KEY,
    contract_id         BIGINT         NOT NULL,
    contract_bus_no     VARCHAR(64)    NOT NULL,
    setl_optins         VARCHAR(6),
    clr_type            VARCHAR(6),
    refd_setl_flag      VARCHAR(3),
    stmt_begndate       DATE,
    stmt_enddate        DATE,
    upload_file_qury_no VARCHAR(64),
    upload_filename     VARCHAR(200),
    result_file_qury_no VARCHAR(64),
    result_filename     VARCHAR(200),
    dld_endtime         VARCHAR(30),
    row_count           INTEGER,
    mismatch_count      INTEGER,
    result_json         TEXT,

    created_at          TIMESTAMP      NOT NULL,
    updated_at          TIMESTAMP,
    created_by          BIGINT,
    updated_by          BIGINT,
    is_deleted          BOOLEAN        NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    deleted_by          BIGINT,
    tenant_id           BIGINT         NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_yb_recon_detail_contract
    ON yb_recon_detail (contract_bus_no, tenant_id)
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
COMMENT ON TABLE yb_recon_detail IS '医保 recon detail';
COMMENT ON COLUMN yb_recon_detail.id IS '主键';
COMMENT ON COLUMN yb_recon_detail.contract_id IS '合同标识';
COMMENT ON COLUMN yb_recon_detail.contract_bus_no IS '合同bus编号';
COMMENT ON COLUMN yb_recon_detail.setl_optins IS '结算optins';
COMMENT ON COLUMN yb_recon_detail.clr_type IS 'clr类型';
COMMENT ON COLUMN yb_recon_detail.refd_setl_flag IS 'refd结算标志位';
COMMENT ON COLUMN yb_recon_detail.stmt_begndate IS 'stmt开始日期';
COMMENT ON COLUMN yb_recon_detail.stmt_enddate IS 'stmt结束日期';
COMMENT ON COLUMN yb_recon_detail.upload_file_qury_no IS '上传filequry编号';
COMMENT ON COLUMN yb_recon_detail.upload_filename IS '上传filename';
COMMENT ON COLUMN yb_recon_detail.result_file_qury_no IS 'resultfilequry编号';
COMMENT ON COLUMN yb_recon_detail.result_filename IS '结果filename';
COMMENT ON COLUMN yb_recon_detail.dld_endtime IS '下载结束时间';
COMMENT ON COLUMN yb_recon_detail.row_count IS '行数';
COMMENT ON COLUMN yb_recon_detail.mismatch_count IS '不一致笔数';
COMMENT ON COLUMN yb_recon_detail.result_json IS '结果JSON';
COMMENT ON COLUMN yb_recon_detail.created_at IS '创建时间';
COMMENT ON COLUMN yb_recon_detail.updated_at IS '最后更新时间';
COMMENT ON COLUMN yb_recon_detail.created_by IS '创建人';
COMMENT ON COLUMN yb_recon_detail.updated_by IS '最后更新人';
COMMENT ON COLUMN yb_recon_detail.is_deleted IS '软删标记';
COMMENT ON COLUMN yb_recon_detail.deleted_at IS '删除时间';
COMMENT ON COLUMN yb_recon_detail.deleted_by IS '删除人';
COMMENT ON COLUMN yb_recon_detail.tenant_id IS '租户标识';

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
