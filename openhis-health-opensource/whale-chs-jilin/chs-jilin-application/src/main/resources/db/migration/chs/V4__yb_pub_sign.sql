-- 医保 9001 签到流水持久化（当日有效，避免重复签到）
-- CHS CHS Flyway V4

CREATE TABLE IF NOT EXISTS yb_pub_sign (
    id               BIGINT       NOT NULL PRIMARY KEY,
    contract_bus_no  VARCHAR(64)  NOT NULL,
    adm_vs           VARCHAR(16)  NOT NULL,
    fixmedins_code   VARCHAR(32)  NOT NULL,
    opter_no         VARCHAR(32)  NOT NULL,
    sign_no          VARCHAR(64)  NOT NULL,
    sign_time        TIMESTAMP    NOT NULL,
    sign_date        DATE         NOT NULL,
    yb_username      VARCHAR(64),
    status           VARCHAR(8)   NOT NULL DEFAULT '1',
    sign_out_time    TIMESTAMP,

    created_at       TIMESTAMP    NOT NULL,
    updated_at       TIMESTAMP,
    created_by       BIGINT,
    updated_by       BIGINT,
    is_deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at       TIMESTAMP,
    deleted_by       BIGINT,
    tenant_id        BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_yb_pub_sign_lookup
    ON yb_pub_sign (contract_bus_no, opter_no, sign_date, tenant_id)
    WHERE is_deleted = FALSE AND status = '1';


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
COMMENT ON TABLE yb_pub_sign IS '医保 pub sign';
COMMENT ON COLUMN yb_pub_sign.id IS '主键';
COMMENT ON COLUMN yb_pub_sign.contract_bus_no IS '合同bus编号';
COMMENT ON COLUMN yb_pub_sign.adm_vs IS '行政区划版本';
COMMENT ON COLUMN yb_pub_sign.fixmedins_code IS 'fixmedins编码';
COMMENT ON COLUMN yb_pub_sign.opter_no IS 'opter编号';
COMMENT ON COLUMN yb_pub_sign.sign_no IS '体征编号';
COMMENT ON COLUMN yb_pub_sign.sign_time IS '体征时间';
COMMENT ON COLUMN yb_pub_sign.sign_date IS '体征日期';
COMMENT ON COLUMN yb_pub_sign.yb_username IS '医保username';
COMMENT ON COLUMN yb_pub_sign.status IS '医保 9001 签到流水（对齐 yb_pub_sign）';
COMMENT ON COLUMN yb_pub_sign.sign_out_time IS '体征out时间';
COMMENT ON COLUMN yb_pub_sign.created_at IS '创建时间';
COMMENT ON COLUMN yb_pub_sign.updated_at IS '最后更新时间';
COMMENT ON COLUMN yb_pub_sign.created_by IS '创建人';
COMMENT ON COLUMN yb_pub_sign.updated_by IS '最后更新人';
COMMENT ON COLUMN yb_pub_sign.is_deleted IS '软删标记';
COMMENT ON COLUMN yb_pub_sign.deleted_at IS '删除时间';
COMMENT ON COLUMN yb_pub_sign.deleted_by IS '删除人';
COMMENT ON COLUMN yb_pub_sign.tenant_id IS '租户标识';

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
