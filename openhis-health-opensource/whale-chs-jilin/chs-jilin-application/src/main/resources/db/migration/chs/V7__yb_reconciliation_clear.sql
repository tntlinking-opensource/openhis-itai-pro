-- Wave 3：吉林对账清算本地记录（3201 / 3203A）

CREATE TABLE IF NOT EXISTS yb_clear_apply (
    id                BIGINT         NOT NULL PRIMARY KEY,
    contract_id       BIGINT         NOT NULL,
    contract_bus_no   VARCHAR(64)    NOT NULL,
    clr_type          VARCHAR(30),
    clr_optins        VARCHAR(6),
    begndate          DATE,
    enddate           DATE,
    medfee_sumamt     NUMERIC(18, 2),
    med_sumfee        NUMERIC(18, 2),
    fund_appy_sum     NUMERIC(18, 2),
    cash_payamt       NUMERIC(18, 2),
    acct_pay          NUMERIC(18, 2),
    clr_appy_evt_id   VARCHAR(30),
    clr_stas          VARCHAR(6),
    status            VARCHAR(16)    NOT NULL,

    created_at        TIMESTAMP      NOT NULL,
    updated_at        TIMESTAMP,
    created_by        BIGINT,
    updated_by        BIGINT,
    is_deleted        BOOLEAN        NOT NULL DEFAULT FALSE,
    deleted_at        TIMESTAMP,
    deleted_by        BIGINT,
    tenant_id         BIGINT         NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_yb_clear_apply_contract
    ON yb_clear_apply (contract_bus_no, tenant_id)
    WHERE is_deleted = FALSE;

CREATE TABLE IF NOT EXISTS yb_recon_daily (
    id                  BIGINT         NOT NULL PRIMARY KEY,
    contract_id         BIGINT         NOT NULL,
    contract_bus_no     VARCHAR(64)    NOT NULL,
    insutype            VARCHAR(6),
    clr_type            VARCHAR(6),
    setl_optins         VARCHAR(6),
    stmt_begndate       DATE,
    stmt_enddate        DATE,
    medfee_sumamt       NUMERIC(18, 2),
    fund_pay_sumamt     NUMERIC(18, 2),
    acct_pay            NUMERIC(18, 2),
    fixmedins_setl_cnt  INTEGER,
    stmt_rslt           VARCHAR(6),
    stmt_rslt_dscr      VARCHAR(200),

    created_at          TIMESTAMP      NOT NULL,
    updated_at          TIMESTAMP,
    created_by          BIGINT,
    updated_by          BIGINT,
    is_deleted          BOOLEAN        NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    deleted_by          BIGINT,
    tenant_id           BIGINT         NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_yb_recon_daily_contract
    ON yb_recon_daily (contract_bus_no, tenant_id)
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
COMMENT ON TABLE yb_clear_apply IS '医保 clear apply';
COMMENT ON COLUMN yb_clear_apply.id IS '主键';
COMMENT ON COLUMN yb_clear_apply.contract_id IS '合同标识';
COMMENT ON COLUMN yb_clear_apply.contract_bus_no IS '合同bus编号';
COMMENT ON COLUMN yb_clear_apply.clr_type IS 'clr类型';
COMMENT ON COLUMN yb_clear_apply.clr_optins IS '清算经办机构';
COMMENT ON COLUMN yb_clear_apply.begndate IS '开始日期';
COMMENT ON COLUMN yb_clear_apply.enddate IS '结束日期';
COMMENT ON COLUMN yb_clear_apply.medfee_sumamt IS '医疗费总额';
COMMENT ON COLUMN yb_clear_apply.med_sumfee IS '医保费用总额';
COMMENT ON COLUMN yb_clear_apply.fund_appy_sum IS '基金申请总额';
COMMENT ON COLUMN yb_clear_apply.cash_payamt IS '现金支付金额';
COMMENT ON COLUMN yb_clear_apply.acct_pay IS 'acct支付';
COMMENT ON COLUMN yb_clear_apply.clr_appy_evt_id IS 'clrappyevt标识';
COMMENT ON COLUMN yb_clear_apply.clr_stas IS 'clr状态';
COMMENT ON COLUMN yb_clear_apply.status IS '状态';
COMMENT ON COLUMN yb_clear_apply.created_at IS '创建时间';
COMMENT ON COLUMN yb_clear_apply.updated_at IS '最后更新时间';
COMMENT ON COLUMN yb_clear_apply.created_by IS '创建人';
COMMENT ON COLUMN yb_clear_apply.updated_by IS '最后更新人';
COMMENT ON COLUMN yb_clear_apply.is_deleted IS '软删标记';
COMMENT ON COLUMN yb_clear_apply.deleted_at IS '删除时间';
COMMENT ON COLUMN yb_clear_apply.deleted_by IS '删除人';
COMMENT ON COLUMN yb_clear_apply.tenant_id IS '租户标识';

COMMENT ON TABLE yb_recon_daily IS '医保 recon daily';
COMMENT ON COLUMN yb_recon_daily.id IS '主键';
COMMENT ON COLUMN yb_recon_daily.contract_id IS '合同标识';
COMMENT ON COLUMN yb_recon_daily.contract_bus_no IS '合同bus编号';
COMMENT ON COLUMN yb_recon_daily.insutype IS '险种类型';
COMMENT ON COLUMN yb_recon_daily.clr_type IS 'clr类型';
COMMENT ON COLUMN yb_recon_daily.setl_optins IS '结算optins';
COMMENT ON COLUMN yb_recon_daily.stmt_begndate IS 'stmt开始日期';
COMMENT ON COLUMN yb_recon_daily.stmt_enddate IS 'stmt结束日期';
COMMENT ON COLUMN yb_recon_daily.medfee_sumamt IS '医疗费总额';
COMMENT ON COLUMN yb_recon_daily.fund_pay_sumamt IS 'fund支付sumamt';
COMMENT ON COLUMN yb_recon_daily.acct_pay IS 'acct支付';
COMMENT ON COLUMN yb_recon_daily.fixmedins_setl_cnt IS 'fixmedins结算数量';
COMMENT ON COLUMN yb_recon_daily.stmt_rslt IS 'stmt结果';
COMMENT ON COLUMN yb_recon_daily.stmt_rslt_dscr IS 'stmt结果dscr';
COMMENT ON COLUMN yb_recon_daily.created_at IS '创建时间';
COMMENT ON COLUMN yb_recon_daily.updated_at IS '最后更新时间';
COMMENT ON COLUMN yb_recon_daily.created_by IS '创建人';
COMMENT ON COLUMN yb_recon_daily.updated_by IS '最后更新人';
COMMENT ON COLUMN yb_recon_daily.is_deleted IS '软删标记';
COMMENT ON COLUMN yb_recon_daily.deleted_at IS '删除时间';
COMMENT ON COLUMN yb_recon_daily.deleted_by IS '删除人';
COMMENT ON COLUMN yb_recon_daily.tenant_id IS '租户标识';

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
