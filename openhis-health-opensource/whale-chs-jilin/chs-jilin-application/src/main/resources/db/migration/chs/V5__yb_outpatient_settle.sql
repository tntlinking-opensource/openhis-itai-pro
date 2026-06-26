-- 门诊医保结算 Wave 1：人员缓存、就诊映射、预结算快照、结算记录

CREATE TABLE IF NOT EXISTS yb_person_cache (
    id                  BIGINT       NOT NULL PRIMARY KEY,
    contract_id         BIGINT       NOT NULL,
    mdtrt_cert_type     VARCHAR(3)   NOT NULL,
    mdtrt_cert_no       VARCHAR(64)  NOT NULL,
    psn_no              VARCHAR(30)  NOT NULL,
    insuplc_admdvs      VARCHAR(6),
    insutype            VARCHAR(6),
    balc                NUMERIC(18, 2),
    snapshot_json       TEXT         NOT NULL,
    cached_at           TIMESTAMP    NOT NULL,
    expire_at           TIMESTAMP    NOT NULL,

    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP,
    created_by          BIGINT,
    updated_by          BIGINT,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    deleted_by          BIGINT,
    tenant_id           BIGINT       NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_yb_person_cache_cert
    ON yb_person_cache (contract_id, mdtrt_cert_type, mdtrt_cert_no, tenant_id)
    WHERE is_deleted = FALSE;

CREATE TABLE IF NOT EXISTS yb_encounter_platform_map (
    id                  BIGINT       NOT NULL PRIMARY KEY,
    encounter_id        BIGINT       NOT NULL,
    contract_id         BIGINT       NOT NULL,
    mdtrt_id            VARCHAR(30)  NOT NULL,
    psn_no              VARCHAR(30),
    ipt_otp_no          VARCHAR(30),

    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP,
    created_by          BIGINT,
    updated_by          BIGINT,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    deleted_by          BIGINT,
    tenant_id           BIGINT       NOT NULL,
    med_type VARCHAR(6) NOT NULL DEFAULT '11'
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_yb_encounter_platform_map
    ON yb_encounter_platform_map (encounter_id, contract_id, tenant_id)
    WHERE is_deleted = FALSE;

CREATE TABLE IF NOT EXISTS yb_clinic_pre_settle (
    id                      BIGINT       NOT NULL PRIMARY KEY,
    contract_id             BIGINT       NOT NULL,
    encounter_id            BIGINT       NOT NULL,
    payment_draft_id        BIGINT,
    chrg_bchno              VARCHAR(30)  NOT NULL,
    contract_bus_no         VARCHAR(64)  NOT NULL,
    mdtrt_id                VARCHAR(30)  NOT NULL,
    psn_no                  VARCHAR(30)  NOT NULL,
    insuplc_admdvs          VARCHAR(6),
    medfee_sumamt           NUMERIC(18, 2),
    fund_pay_sumamt         NUMERIC(18, 2),
    acct_pay                NUMERIC(18, 2),
    psn_cash_pay            NUMERIC(18, 2),
    charge_item_ids         TEXT,
    status                  VARCHAR(16)  NOT NULL,
    med_type                VARCHAR(6),
    dise_codg               VARCHAR(30),

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

CREATE UNIQUE INDEX IF NOT EXISTS uk_yb_clinic_pre_settle_bchno
    ON yb_clinic_pre_settle (chrg_bchno, tenant_id)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_yb_clinic_pre_settle_draft
    ON yb_clinic_pre_settle (payment_draft_id)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_yb_clinic_pre_settle_draft_status
    ON yb_clinic_pre_settle (payment_draft_id, status)
    WHERE is_deleted = FALSE;

CREATE TABLE IF NOT EXISTS yb_settlement_record (
    id                      BIGINT       NOT NULL PRIMARY KEY,
    contract_id             BIGINT       NOT NULL,
    encounter_id            BIGINT       NOT NULL,
    payment_id              BIGINT,
    pre_settle_id           BIGINT,
    chrg_bchno              VARCHAR(30)  NOT NULL,
    setl_id                 VARCHAR(30)  NOT NULL,
    mdtrt_id                VARCHAR(30)  NOT NULL,
    psn_no                  VARCHAR(30)  NOT NULL,
    medfee_sumamt           NUMERIC(18, 2),
    fund_pay_sumamt         NUMERIC(18, 2),
    acct_pay                NUMERIC(18, 2),
    psn_cash_pay            NUMERIC(18, 2),

    created_at              TIMESTAMP    NOT NULL,
    updated_at              TIMESTAMP,
    created_by              BIGINT,
    updated_by          BIGINT,
    is_deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMP,
    deleted_by              BIGINT,
    tenant_id               BIGINT       NOT NULL,
    account_id BIGINT,
    encounter_class VARCHAR(8) NOT NULL DEFAULT 'AMB',
    status VARCHAR(16) NOT NULL DEFAULT 'CONFIRMED'
);

CREATE INDEX IF NOT EXISTS idx_yb_settlement_record_payment
    ON yb_settlement_record (payment_id)
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
COMMENT ON TABLE yb_person_cache IS '医保 person cache';
COMMENT ON COLUMN yb_person_cache.id IS '主键';
COMMENT ON COLUMN yb_person_cache.contract_id IS '合同标识';
COMMENT ON COLUMN yb_person_cache.mdtrt_cert_type IS '就诊cert类型';
COMMENT ON COLUMN yb_person_cache.mdtrt_cert_no IS '就诊cert编号';
COMMENT ON COLUMN yb_person_cache.psn_no IS 'psn编号';
COMMENT ON COLUMN yb_person_cache.insuplc_admdvs IS '参保地医保区划';
COMMENT ON COLUMN yb_person_cache.insutype IS '险种类型';
COMMENT ON COLUMN yb_person_cache.balc IS '账户余额';
COMMENT ON COLUMN yb_person_cache.snapshot_json IS '快照JSON';
COMMENT ON COLUMN yb_person_cache.cached_at IS 'cached时间';
COMMENT ON COLUMN yb_person_cache.expire_at IS 'expire时间';
COMMENT ON COLUMN yb_person_cache.created_at IS '创建时间';
COMMENT ON COLUMN yb_person_cache.updated_at IS '最后更新时间';
COMMENT ON COLUMN yb_person_cache.created_by IS '创建人';
COMMENT ON COLUMN yb_person_cache.updated_by IS '最后更新人';
COMMENT ON COLUMN yb_person_cache.is_deleted IS '软删标记';
COMMENT ON COLUMN yb_person_cache.deleted_at IS '删除时间';
COMMENT ON COLUMN yb_person_cache.deleted_by IS '删除人';
COMMENT ON COLUMN yb_person_cache.tenant_id IS '租户标识';

COMMENT ON TABLE yb_encounter_platform_map IS '医保 encounter platform map';
COMMENT ON COLUMN yb_encounter_platform_map.id IS '主键';
COMMENT ON COLUMN yb_encounter_platform_map.encounter_id IS '就诊标识';
COMMENT ON COLUMN yb_encounter_platform_map.contract_id IS '合同标识';
COMMENT ON COLUMN yb_encounter_platform_map.mdtrt_id IS '就诊标识';
COMMENT ON COLUMN yb_encounter_platform_map.psn_no IS 'psn编号';
COMMENT ON COLUMN yb_encounter_platform_map.ipt_otp_no IS 'iptotp编号';
COMMENT ON COLUMN yb_encounter_platform_map.created_at IS '创建时间';
COMMENT ON COLUMN yb_encounter_platform_map.updated_at IS '最后更新时间';
COMMENT ON COLUMN yb_encounter_platform_map.created_by IS '创建人';
COMMENT ON COLUMN yb_encounter_platform_map.updated_by IS '最后更新人';
COMMENT ON COLUMN yb_encounter_platform_map.is_deleted IS '软删标记';
COMMENT ON COLUMN yb_encounter_platform_map.deleted_at IS '删除时间';
COMMENT ON COLUMN yb_encounter_platform_map.deleted_by IS '删除人';
COMMENT ON COLUMN yb_encounter_platform_map.tenant_id IS '租户标识';
COMMENT ON COLUMN yb_encounter_platform_map.med_type IS 'med类型';

COMMENT ON TABLE yb_clinic_pre_settle IS '2206 预结算快照（按 chrg_bchno）';
COMMENT ON COLUMN yb_clinic_pre_settle.id IS '主键';
COMMENT ON COLUMN yb_clinic_pre_settle.contract_id IS '合同标识';
COMMENT ON COLUMN yb_clinic_pre_settle.encounter_id IS '就诊标识';
COMMENT ON COLUMN yb_clinic_pre_settle.payment_draft_id IS '支付draft标识';
COMMENT ON COLUMN yb_clinic_pre_settle.chrg_bchno IS '收费批次号';
COMMENT ON COLUMN yb_clinic_pre_settle.contract_bus_no IS '合同bus编号';
COMMENT ON COLUMN yb_clinic_pre_settle.mdtrt_id IS '就诊标识';
COMMENT ON COLUMN yb_clinic_pre_settle.psn_no IS 'psn编号';
COMMENT ON COLUMN yb_clinic_pre_settle.insuplc_admdvs IS '参保地医保区划';
COMMENT ON COLUMN yb_clinic_pre_settle.medfee_sumamt IS '医疗费总额';
COMMENT ON COLUMN yb_clinic_pre_settle.fund_pay_sumamt IS 'fund支付sumamt';
COMMENT ON COLUMN yb_clinic_pre_settle.acct_pay IS 'acct支付';
COMMENT ON COLUMN yb_clinic_pre_settle.psn_cash_pay IS 'psncash支付';
COMMENT ON COLUMN yb_clinic_pre_settle.charge_item_ids IS '收费项ids';
COMMENT ON COLUMN yb_clinic_pre_settle.status IS '状态';
COMMENT ON COLUMN yb_clinic_pre_settle.med_type IS '医疗类别';
COMMENT ON COLUMN yb_clinic_pre_settle.dise_codg IS '慢病诊断码';
COMMENT ON COLUMN yb_clinic_pre_settle.created_at IS '创建时间';
COMMENT ON COLUMN yb_clinic_pre_settle.updated_at IS '最后更新时间';
COMMENT ON COLUMN yb_clinic_pre_settle.created_by IS '创建人';
COMMENT ON COLUMN yb_clinic_pre_settle.updated_by IS '最后更新人';
COMMENT ON COLUMN yb_clinic_pre_settle.is_deleted IS '软删标记';
COMMENT ON COLUMN yb_clinic_pre_settle.deleted_at IS '删除时间';
COMMENT ON COLUMN yb_clinic_pre_settle.deleted_by IS '删除人';
COMMENT ON COLUMN yb_clinic_pre_settle.tenant_id IS '租户标识';
COMMENT ON COLUMN yb_clinic_pre_settle.account_id IS '账户标识';

COMMENT ON TABLE yb_settlement_record IS '医保 settlement record';
COMMENT ON COLUMN yb_settlement_record.id IS '主键';
COMMENT ON COLUMN yb_settlement_record.contract_id IS '合同标识';
COMMENT ON COLUMN yb_settlement_record.encounter_id IS '就诊标识';
COMMENT ON COLUMN yb_settlement_record.payment_id IS '支付标识';
COMMENT ON COLUMN yb_settlement_record.pre_settle_id IS 'presettle标识';
COMMENT ON COLUMN yb_settlement_record.chrg_bchno IS '收费批次号';
COMMENT ON COLUMN yb_settlement_record.setl_id IS '结算标识';
COMMENT ON COLUMN yb_settlement_record.mdtrt_id IS '就诊标识';
COMMENT ON COLUMN yb_settlement_record.psn_no IS 'psn编号';
COMMENT ON COLUMN yb_settlement_record.medfee_sumamt IS '医疗费总额';
COMMENT ON COLUMN yb_settlement_record.fund_pay_sumamt IS 'fund支付sumamt';
COMMENT ON COLUMN yb_settlement_record.acct_pay IS 'acct支付';
COMMENT ON COLUMN yb_settlement_record.psn_cash_pay IS 'psncash支付';
COMMENT ON COLUMN yb_settlement_record.created_at IS '创建时间';
COMMENT ON COLUMN yb_settlement_record.updated_at IS '最后更新时间';
COMMENT ON COLUMN yb_settlement_record.created_by IS '创建人';
COMMENT ON COLUMN yb_settlement_record.updated_by IS '最后更新人';
COMMENT ON COLUMN yb_settlement_record.is_deleted IS '软删标记';
COMMENT ON COLUMN yb_settlement_record.deleted_at IS '删除时间';
COMMENT ON COLUMN yb_settlement_record.deleted_by IS '删除人';
COMMENT ON COLUMN yb_settlement_record.tenant_id IS '租户标识';
COMMENT ON COLUMN yb_settlement_record.account_id IS '账户标识';
COMMENT ON COLUMN yb_settlement_record.encounter_class IS '就诊class';
COMMENT ON COLUMN yb_settlement_record.status IS '状态';

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
