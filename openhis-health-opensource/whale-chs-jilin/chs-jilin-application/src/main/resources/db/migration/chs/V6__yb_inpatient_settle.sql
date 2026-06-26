-- 住院医保结算 Wave 1：预结算快照、费用上传跟踪

CREATE TABLE IF NOT EXISTS yb_inpatient_fee_upload (
    id                      BIGINT       NOT NULL PRIMARY KEY,
    encounter_id            BIGINT       NOT NULL,
    charge_item_id          BIGINT       NOT NULL,
    feedetl_sn              VARCHAR(30)  NOT NULL,
    mdtrt_id                VARCHAR(30)  NOT NULL,
    upload_status           VARCHAR(16)  NOT NULL,
    platform_result_json    TEXT,
    uploaded_at             TIMESTAMP,

    created_at              TIMESTAMP    NOT NULL,
    updated_at              TIMESTAMP,
    created_by              BIGINT,
    updated_by              BIGINT,
    is_deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMP,
    deleted_by              BIGINT,
    tenant_id               BIGINT       NOT NULL,
    det_item_fee_sumamt     NUMERIC(18, 4)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_yb_ip_fee_upload_charge_item
    ON yb_inpatient_fee_upload (charge_item_id, tenant_id)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_yb_ip_fee_upload_encounter
    ON yb_inpatient_fee_upload (encounter_id, upload_status)
    WHERE is_deleted = FALSE;

CREATE TABLE IF NOT EXISTS yb_inpatient_pre_settle (
    id                      BIGINT       NOT NULL PRIMARY KEY,
    payment_draft_id        BIGINT       NOT NULL,
    encounter_id            BIGINT       NOT NULL,
    contract_id             BIGINT       NOT NULL,
    contract_bus_no         VARCHAR(64)  NOT NULL,
    mdtrt_id                VARCHAR(30)  NOT NULL,
    psn_no                  VARCHAR(30)  NOT NULL,
    med_type                VARCHAR(6),
    mid_setl_flag           VARCHAR(3),
    settle_mode             VARCHAR(20),
    medfee_sumamt           NUMERIC(18, 2),
    fund_pay_sumamt         NUMERIC(18, 2),
    acct_pay                NUMERIC(18, 2),
    psn_cash_pay            NUMERIC(18, 2),
    charge_item_ids         TEXT,
    setl_id                 VARCHAR(30),
    status                  VARCHAR(16)  NOT NULL,

    created_at              TIMESTAMP    NOT NULL,
    updated_at              TIMESTAMP,
    created_by              BIGINT,
    updated_by              BIGINT,
    is_deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMP,
    deleted_by              BIGINT,
    tenant_id               BIGINT       NOT NULL,
    account_id BIGINT
);

CREATE INDEX IF NOT EXISTS idx_yb_ip_pre_settle_draft_status
    ON yb_inpatient_pre_settle (payment_draft_id, status)
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
COMMENT ON TABLE yb_inpatient_fee_upload IS '医保 inpatient fee upload';
COMMENT ON COLUMN yb_inpatient_fee_upload.id IS '主键';
COMMENT ON COLUMN yb_inpatient_fee_upload.encounter_id IS '就诊标识';
COMMENT ON COLUMN yb_inpatient_fee_upload.charge_item_id IS '收费项标识';
COMMENT ON COLUMN yb_inpatient_fee_upload.feedetl_sn IS '费用明细流水号';
COMMENT ON COLUMN yb_inpatient_fee_upload.mdtrt_id IS '就诊标识';
COMMENT ON COLUMN yb_inpatient_fee_upload.upload_status IS '上传状态';
COMMENT ON COLUMN yb_inpatient_fee_upload.platform_result_json IS 'platform结果JSON';
COMMENT ON COLUMN yb_inpatient_fee_upload.uploaded_at IS 'uploaded时间';
COMMENT ON COLUMN yb_inpatient_fee_upload.created_at IS '创建时间';
COMMENT ON COLUMN yb_inpatient_fee_upload.updated_at IS '最后更新时间';
COMMENT ON COLUMN yb_inpatient_fee_upload.created_by IS '创建人';
COMMENT ON COLUMN yb_inpatient_fee_upload.updated_by IS '最后更新人';
COMMENT ON COLUMN yb_inpatient_fee_upload.is_deleted IS '软删标记';
COMMENT ON COLUMN yb_inpatient_fee_upload.deleted_at IS '删除时间';
COMMENT ON COLUMN yb_inpatient_fee_upload.deleted_by IS '删除人';
COMMENT ON COLUMN yb_inpatient_fee_upload.tenant_id IS '租户标识';
COMMENT ON COLUMN yb_inpatient_fee_upload.det_item_fee_sumamt IS 'det项feesumamt';

COMMENT ON TABLE yb_inpatient_pre_settle IS '医保 inpatient pre settle';
COMMENT ON COLUMN yb_inpatient_pre_settle.id IS '主键';
COMMENT ON COLUMN yb_inpatient_pre_settle.payment_draft_id IS '支付draft标识';
COMMENT ON COLUMN yb_inpatient_pre_settle.encounter_id IS '就诊标识';
COMMENT ON COLUMN yb_inpatient_pre_settle.contract_id IS '合同标识';
COMMENT ON COLUMN yb_inpatient_pre_settle.contract_bus_no IS '合同bus编号';
COMMENT ON COLUMN yb_inpatient_pre_settle.mdtrt_id IS '就诊标识';
COMMENT ON COLUMN yb_inpatient_pre_settle.psn_no IS 'psn编号';
COMMENT ON COLUMN yb_inpatient_pre_settle.med_type IS 'med类型';
COMMENT ON COLUMN yb_inpatient_pre_settle.mid_setl_flag IS 'mid结算标志位';
COMMENT ON COLUMN yb_inpatient_pre_settle.settle_mode IS '结算模式';
COMMENT ON COLUMN yb_inpatient_pre_settle.medfee_sumamt IS '医疗费总额';
COMMENT ON COLUMN yb_inpatient_pre_settle.fund_pay_sumamt IS 'fund支付sumamt';
COMMENT ON COLUMN yb_inpatient_pre_settle.acct_pay IS 'acct支付';
COMMENT ON COLUMN yb_inpatient_pre_settle.psn_cash_pay IS 'psncash支付';
COMMENT ON COLUMN yb_inpatient_pre_settle.charge_item_ids IS '收费项ids';
COMMENT ON COLUMN yb_inpatient_pre_settle.setl_id IS '结算标识';
COMMENT ON COLUMN yb_inpatient_pre_settle.status IS '状态';
COMMENT ON COLUMN yb_inpatient_pre_settle.created_at IS '创建时间';
COMMENT ON COLUMN yb_inpatient_pre_settle.updated_at IS '最后更新时间';
COMMENT ON COLUMN yb_inpatient_pre_settle.created_by IS '创建人';
COMMENT ON COLUMN yb_inpatient_pre_settle.updated_by IS '最后更新人';
COMMENT ON COLUMN yb_inpatient_pre_settle.is_deleted IS '软删标记';
COMMENT ON COLUMN yb_inpatient_pre_settle.deleted_at IS '删除时间';
COMMENT ON COLUMN yb_inpatient_pre_settle.deleted_by IS '删除人';
COMMENT ON COLUMN yb_inpatient_pre_settle.tenant_id IS '租户标识';
COMMENT ON COLUMN yb_inpatient_pre_settle.account_id IS '账户标识';

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
