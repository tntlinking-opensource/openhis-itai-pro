-- CHS 结算域 schema 对齐：补全实体与 V5/V6 迁移之间的缺口

-- ---------------------------------------------------------------------------
-- 1. 新建表
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS yb_invoke_log (
    id                      BIGINT       NOT NULL PRIMARY KEY,
    contract_id             BIGINT,
    contract_bus_no         VARCHAR(64),
    infno                   VARCHAR(16)  NOT NULL,
    msgid                   VARCHAR(64),
    encounter_id            BIGINT,
    payment_draft_id        BIGINT,
    chrg_bchno              VARCHAR(30),
    pre_settle_id           BIGINT,
    settlement_record_id    BIGINT,
    infcode                 VARCHAR(8),
    duration_ms             INT,
    request_json            TEXT,
    response_json           TEXT,

    created_at              TIMESTAMP    NOT NULL,
    updated_at              TIMESTAMP,
    created_by              BIGINT,
    updated_by              BIGINT,
    is_deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMP,
    deleted_by              BIGINT,
    tenant_id               BIGINT       NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_yb_invoke_log_encounter
    ON yb_invoke_log (encounter_id, infno)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_yb_invoke_log_settlement
    ON yb_invoke_log (settlement_record_id)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_yb_invoke_log_msgid
    ON yb_invoke_log (msgid, tenant_id)
    WHERE is_deleted = FALSE;

CREATE TABLE IF NOT EXISTS yb_settlement_fund_detail (
    id                      BIGINT       NOT NULL PRIMARY KEY,
    settlement_record_id    BIGINT       NOT NULL,
    fund_pay_type           VARCHAR(6),
    fund_pay_type_name      VARCHAR(128),
    inscp_scp_amt           NUMERIC(18, 2),
    crt_payb_lmt_amt        NUMERIC(18, 2),
    fund_payamt             NUMERIC(18, 2),
    setl_proc_info          TEXT,

    created_at              TIMESTAMP    NOT NULL,
    updated_at              TIMESTAMP,
    created_by              BIGINT,
    updated_by              BIGINT,
    is_deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMP,
    deleted_by              BIGINT,
    tenant_id               BIGINT       NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_yb_settlement_fund_detail_record
    ON yb_settlement_fund_detail (settlement_record_id)
    WHERE is_deleted = FALSE;

CREATE TABLE IF NOT EXISTS yb_settlement_correction (
    id                      BIGINT       NOT NULL PRIMARY KEY,
    settlement_record_id    BIGINT,
    psn_no                  VARCHAR(30)  NOT NULL,
    omsgid                  VARCHAR(30)  NOT NULL,
    oinfno                  VARCHAR(4)   NOT NULL,
    encounter_class         VARCHAR(8),
    invoke_log_id           BIGINT,
    corrected_at            TIMESTAMP    NOT NULL,

    created_at              TIMESTAMP    NOT NULL,
    updated_at              TIMESTAMP,
    created_by              BIGINT,
    updated_by              BIGINT,
    is_deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMP,
    deleted_by              BIGINT,
    tenant_id               BIGINT       NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_yb_settlement_correction_record
    ON yb_settlement_correction (settlement_record_id)
    WHERE is_deleted = FALSE;

-- ---------------------------------------------------------------------------
-- 2. 门诊预结算：补 2206 扩展字段
-- ---------------------------------------------------------------------------

ALTER TABLE yb_clinic_pre_settle ADD COLUMN IF NOT EXISTS mdtrt_cert_type VARCHAR(3);
ALTER TABLE yb_clinic_pre_settle ADD COLUMN IF NOT EXISTS mdtrt_cert_no VARCHAR(64);
ALTER TABLE yb_clinic_pre_settle ADD COLUMN IF NOT EXISTS insutype VARCHAR(6);
ALTER TABLE yb_clinic_pre_settle ADD COLUMN IF NOT EXISTS acct_used_flag VARCHAR(1);
ALTER TABLE yb_clinic_pre_settle ADD COLUMN IF NOT EXISTS psn_setlway VARCHAR(6);
ALTER TABLE yb_clinic_pre_settle ADD COLUMN IF NOT EXISTS fulamt_ownpay_amt NUMERIC(18, 2);
ALTER TABLE yb_clinic_pre_settle ADD COLUMN IF NOT EXISTS overlmt_selfpay NUMERIC(18, 2);
ALTER TABLE yb_clinic_pre_settle ADD COLUMN IF NOT EXISTS preselfpay_amt NUMERIC(18, 2);
ALTER TABLE yb_clinic_pre_settle ADD COLUMN IF NOT EXISTS inscp_scp_amt NUMERIC(18, 2);

-- ---------------------------------------------------------------------------
-- 3. 住院预结算：补 2303 扩展字段
-- ---------------------------------------------------------------------------

ALTER TABLE yb_inpatient_pre_settle ADD COLUMN IF NOT EXISTS insuplc_admdvs VARCHAR(6);
ALTER TABLE yb_inpatient_pre_settle ADD COLUMN IF NOT EXISTS mdtrt_cert_type VARCHAR(3);
ALTER TABLE yb_inpatient_pre_settle ADD COLUMN IF NOT EXISTS mdtrt_cert_no VARCHAR(64);
ALTER TABLE yb_inpatient_pre_settle ADD COLUMN IF NOT EXISTS insutype VARCHAR(6);
ALTER TABLE yb_inpatient_pre_settle ADD COLUMN IF NOT EXISTS acct_used_flag VARCHAR(1);
ALTER TABLE yb_inpatient_pre_settle ADD COLUMN IF NOT EXISTS psn_setlway VARCHAR(6);
ALTER TABLE yb_inpatient_pre_settle ADD COLUMN IF NOT EXISTS fulamt_ownpay_amt NUMERIC(18, 2);
ALTER TABLE yb_inpatient_pre_settle ADD COLUMN IF NOT EXISTS overlmt_selfpay NUMERIC(18, 2);
ALTER TABLE yb_inpatient_pre_settle ADD COLUMN IF NOT EXISTS preselfpay_amt NUMERIC(18, 2);
ALTER TABLE yb_inpatient_pre_settle ADD COLUMN IF NOT EXISTS inscp_scp_amt NUMERIC(18, 2);

-- ---------------------------------------------------------------------------
-- 4. 住院费用上传：补 2301 分割结果字段（对齐 yb_clinic_fee_upload）
-- ---------------------------------------------------------------------------

ALTER TABLE yb_inpatient_fee_upload ADD COLUMN IF NOT EXISTS chrgitm_lv VARCHAR(3);
ALTER TABLE yb_inpatient_fee_upload ADD COLUMN IF NOT EXISTS med_chrgitm_type VARCHAR(6);
ALTER TABLE yb_inpatient_fee_upload ADD COLUMN IF NOT EXISTS selfpay_prop NUMERIC(5, 4);
ALTER TABLE yb_inpatient_fee_upload ADD COLUMN IF NOT EXISTS fulamt_ownpay_amt NUMERIC(18, 2);
ALTER TABLE yb_inpatient_fee_upload ADD COLUMN IF NOT EXISTS overlmt_amt NUMERIC(18, 2);
ALTER TABLE yb_inpatient_fee_upload ADD COLUMN IF NOT EXISTS preselfpay_amt NUMERIC(18, 2);
ALTER TABLE yb_inpatient_fee_upload ADD COLUMN IF NOT EXISTS inscp_scp_amt NUMERIC(18, 2);
ALTER TABLE yb_inpatient_fee_upload ADD COLUMN IF NOT EXISTS lmt_used_flag VARCHAR(3);
ALTER TABLE yb_inpatient_fee_upload ADD COLUMN IF NOT EXISTS platform_memo VARCHAR(500);

-- ---------------------------------------------------------------------------
-- 5. 结算凭证：补 2207/2304 setlinfo 完整字段
-- ---------------------------------------------------------------------------

ALTER TABLE yb_settlement_record ADD COLUMN IF NOT EXISTS settle_mode VARCHAR(20);
ALTER TABLE yb_settlement_record ADD COLUMN IF NOT EXISTS psn_name VARCHAR(50);
ALTER TABLE yb_settlement_record ADD COLUMN IF NOT EXISTS insutype VARCHAR(6);
ALTER TABLE yb_settlement_record ADD COLUMN IF NOT EXISTS med_type VARCHAR(6);
ALTER TABLE yb_settlement_record ADD COLUMN IF NOT EXISTS setl_time TIMESTAMP;
ALTER TABLE yb_settlement_record ADD COLUMN IF NOT EXISTS fulamt_ownpay_amt NUMERIC(18, 2);
ALTER TABLE yb_settlement_record ADD COLUMN IF NOT EXISTS overlmt_selfpay NUMERIC(18, 2);
ALTER TABLE yb_settlement_record ADD COLUMN IF NOT EXISTS preselfpay_amt NUMERIC(18, 2);
ALTER TABLE yb_settlement_record ADD COLUMN IF NOT EXISTS inscp_scp_amt NUMERIC(18, 2);
ALTER TABLE yb_settlement_record ADD COLUMN IF NOT EXISTS hifp_pay NUMERIC(18, 2);
ALTER TABLE yb_settlement_record ADD COLUMN IF NOT EXISTS cvlserv_pay NUMERIC(18, 2);
ALTER TABLE yb_settlement_record ADD COLUMN IF NOT EXISTS hifmi_pay NUMERIC(18, 2);
ALTER TABLE yb_settlement_record ADD COLUMN IF NOT EXISTS hifob_pay NUMERIC(18, 2);
ALTER TABLE yb_settlement_record ADD COLUMN IF NOT EXISTS hifes_pay NUMERIC(18, 2);
ALTER TABLE yb_settlement_record ADD COLUMN IF NOT EXISTS cvlserv_flag VARCHAR(3);
ALTER TABLE yb_settlement_record ADD COLUMN IF NOT EXISTS pool_prop_selfpay NUMERIC(18, 2);
ALTER TABLE yb_settlement_record ADD COLUMN IF NOT EXISTS maf_pay NUMERIC(18, 2);
ALTER TABLE yb_settlement_record ADD COLUMN IF NOT EXISTS oth_pay NUMERIC(18, 2);
ALTER TABLE yb_settlement_record ADD COLUMN IF NOT EXISTS psn_part_amt NUMERIC(18, 2);
ALTER TABLE yb_settlement_record ADD COLUMN IF NOT EXISTS hosp_part_amt NUMERIC(18, 2);
ALTER TABLE yb_settlement_record ADD COLUMN IF NOT EXISTS balc NUMERIC(18, 2);
ALTER TABLE yb_settlement_record ADD COLUMN IF NOT EXISTS acct_mulaid_pay NUMERIC(18, 2);
ALTER TABLE yb_settlement_record ADD COLUMN IF NOT EXISTS psn_type VARCHAR(6);
ALTER TABLE yb_settlement_record ADD COLUMN IF NOT EXISTS act_pay_dedc NUMERIC(18, 2);
ALTER TABLE yb_settlement_record ADD COLUMN IF NOT EXISTS hifdm_pay NUMERIC(18, 2);
ALTER TABLE yb_settlement_record ADD COLUMN IF NOT EXISTS exp_content TEXT;
ALTER TABLE yb_settlement_record ADD COLUMN IF NOT EXISTS medins_setl_id VARCHAR(30);
ALTER TABLE yb_settlement_record ADD COLUMN IF NOT EXISTS msgid_2207 VARCHAR(64);
ALTER TABLE yb_settlement_record ADD COLUMN IF NOT EXISTS clr_optins VARCHAR(6);
ALTER TABLE yb_settlement_record ADD COLUMN IF NOT EXISTS clr_way VARCHAR(6);
ALTER TABLE yb_settlement_record ADD COLUMN IF NOT EXISTS clr_type VARCHAR(6);
ALTER TABLE yb_settlement_record ADD COLUMN IF NOT EXISTS fund_breakdown_json TEXT;

-- ---------------------------------------------------------------------------
-- 6. 结算撤销：补 invoke 关联
-- ---------------------------------------------------------------------------

ALTER TABLE yb_settlement_reversal ADD COLUMN IF NOT EXISTS invoke_log_id BIGINT;

-- ---------------------------------------------------------------------------
-- 注释
-- ---------------------------------------------------------------------------

COMMENT ON TABLE yb_invoke_log IS '医保平台网关调用日志';
COMMENT ON COLUMN yb_invoke_log.id IS '主键';
COMMENT ON COLUMN yb_invoke_log.contract_id IS '合同标识';
COMMENT ON COLUMN yb_invoke_log.contract_bus_no IS '合同bus编号';
COMMENT ON COLUMN yb_invoke_log.infno IS '交易编号';
COMMENT ON COLUMN yb_invoke_log.msgid IS '发送方报文ID';
COMMENT ON COLUMN yb_invoke_log.encounter_id IS '就诊标识';
COMMENT ON COLUMN yb_invoke_log.payment_draft_id IS '支付draft标识';
COMMENT ON COLUMN yb_invoke_log.chrg_bchno IS '收费批次号';
COMMENT ON COLUMN yb_invoke_log.pre_settle_id IS '预结算标识';
COMMENT ON COLUMN yb_invoke_log.settlement_record_id IS '结算记录标识';
COMMENT ON COLUMN yb_invoke_log.infcode IS '平台返回码';
COMMENT ON COLUMN yb_invoke_log.duration_ms IS '调用耗时毫秒';
COMMENT ON COLUMN yb_invoke_log.request_json IS '请求JSON';
COMMENT ON COLUMN yb_invoke_log.response_json IS '响应JSON';
COMMENT ON COLUMN yb_invoke_log.created_at IS '创建时间';
COMMENT ON COLUMN yb_invoke_log.updated_at IS '最后更新时间';
COMMENT ON COLUMN yb_invoke_log.created_by IS '创建人';
COMMENT ON COLUMN yb_invoke_log.updated_by IS '最后更新人';
COMMENT ON COLUMN yb_invoke_log.is_deleted IS '软删标记';
COMMENT ON COLUMN yb_invoke_log.deleted_at IS '删除时间';
COMMENT ON COLUMN yb_invoke_log.deleted_by IS '删除人';
COMMENT ON COLUMN yb_invoke_log.tenant_id IS '租户标识';

COMMENT ON TABLE yb_settlement_fund_detail IS '2207/2304 setldetail 基金分项明细';
COMMENT ON COLUMN yb_settlement_fund_detail.id IS '主键';
COMMENT ON COLUMN yb_settlement_fund_detail.settlement_record_id IS '结算记录标识';
COMMENT ON COLUMN yb_settlement_fund_detail.fund_pay_type IS '基金支付类型';
COMMENT ON COLUMN yb_settlement_fund_detail.fund_pay_type_name IS '基金支付类型名称';
COMMENT ON COLUMN yb_settlement_fund_detail.inscp_scp_amt IS '符合政策范围金额';
COMMENT ON COLUMN yb_settlement_fund_detail.crt_payb_lmt_amt IS '本次可支付限额金额';
COMMENT ON COLUMN yb_settlement_fund_detail.fund_payamt IS '基金支付金额';
COMMENT ON COLUMN yb_settlement_fund_detail.setl_proc_info IS '结算过程信息';
COMMENT ON COLUMN yb_settlement_fund_detail.created_at IS '创建时间';
COMMENT ON COLUMN yb_settlement_fund_detail.updated_at IS '最后更新时间';
COMMENT ON COLUMN yb_settlement_fund_detail.created_by IS '创建人';
COMMENT ON COLUMN yb_settlement_fund_detail.updated_by IS '最后更新人';
COMMENT ON COLUMN yb_settlement_fund_detail.is_deleted IS '软删标记';
COMMENT ON COLUMN yb_settlement_fund_detail.deleted_at IS '删除时间';
COMMENT ON COLUMN yb_settlement_fund_detail.deleted_by IS '删除人';
COMMENT ON COLUMN yb_settlement_fund_detail.tenant_id IS '租户标识';

COMMENT ON TABLE yb_settlement_correction IS '2601 冲正交易审计凭证';
COMMENT ON COLUMN yb_settlement_correction.id IS '主键';
COMMENT ON COLUMN yb_settlement_correction.settlement_record_id IS '结算记录标识';
COMMENT ON COLUMN yb_settlement_correction.psn_no IS 'psn编号';
COMMENT ON COLUMN yb_settlement_correction.omsgid IS '原发送方报文ID';
COMMENT ON COLUMN yb_settlement_correction.oinfno IS '原交易编号';
COMMENT ON COLUMN yb_settlement_correction.encounter_class IS '就诊class';
COMMENT ON COLUMN yb_settlement_correction.invoke_log_id IS '调用日志标识';
COMMENT ON COLUMN yb_settlement_correction.corrected_at IS '冲正时间';
COMMENT ON COLUMN yb_settlement_correction.created_at IS '创建时间';
COMMENT ON COLUMN yb_settlement_correction.updated_at IS '最后更新时间';
COMMENT ON COLUMN yb_settlement_correction.created_by IS '创建人';
COMMENT ON COLUMN yb_settlement_correction.updated_by IS '最后更新人';
COMMENT ON COLUMN yb_settlement_correction.is_deleted IS '软删标记';
COMMENT ON COLUMN yb_settlement_correction.deleted_at IS '删除时间';
COMMENT ON COLUMN yb_settlement_correction.deleted_by IS '删除人';
COMMENT ON COLUMN yb_settlement_correction.tenant_id IS '租户标识';

COMMENT ON COLUMN yb_clinic_pre_settle.mdtrt_cert_type IS '就诊cert类型';
COMMENT ON COLUMN yb_clinic_pre_settle.mdtrt_cert_no IS '就诊cert编号';
COMMENT ON COLUMN yb_clinic_pre_settle.insutype IS '险种类型';
COMMENT ON COLUMN yb_clinic_pre_settle.acct_used_flag IS '个人账户使用标志';
COMMENT ON COLUMN yb_clinic_pre_settle.psn_setlway IS '个人结算方式';
COMMENT ON COLUMN yb_clinic_pre_settle.fulamt_ownpay_amt IS '全自费金额';
COMMENT ON COLUMN yb_clinic_pre_settle.overlmt_selfpay IS '超限价自费费用';
COMMENT ON COLUMN yb_clinic_pre_settle.preselfpay_amt IS '先行自付金额';
COMMENT ON COLUMN yb_clinic_pre_settle.inscp_scp_amt IS '符合政策范围金额';

COMMENT ON COLUMN yb_inpatient_pre_settle.insuplc_admdvs IS '参保地医保区划';
COMMENT ON COLUMN yb_inpatient_pre_settle.mdtrt_cert_type IS '就诊cert类型';
COMMENT ON COLUMN yb_inpatient_pre_settle.mdtrt_cert_no IS '就诊cert编号';
COMMENT ON COLUMN yb_inpatient_pre_settle.insutype IS '险种类型';
COMMENT ON COLUMN yb_inpatient_pre_settle.acct_used_flag IS '个人账户使用标志';
COMMENT ON COLUMN yb_inpatient_pre_settle.psn_setlway IS '个人结算方式';
COMMENT ON COLUMN yb_inpatient_pre_settle.fulamt_ownpay_amt IS '全自费金额';
COMMENT ON COLUMN yb_inpatient_pre_settle.overlmt_selfpay IS '超限价自费费用';
COMMENT ON COLUMN yb_inpatient_pre_settle.preselfpay_amt IS '先行自付金额';
COMMENT ON COLUMN yb_inpatient_pre_settle.inscp_scp_amt IS '符合政策范围金额';

COMMENT ON COLUMN yb_inpatient_fee_upload.chrgitm_lv IS '收费项目等级';
COMMENT ON COLUMN yb_inpatient_fee_upload.med_chrgitm_type IS '医疗收费项目类别';
COMMENT ON COLUMN yb_inpatient_fee_upload.selfpay_prop IS '自付比例';
COMMENT ON COLUMN yb_inpatient_fee_upload.fulamt_ownpay_amt IS '全自费金额';
COMMENT ON COLUMN yb_inpatient_fee_upload.overlmt_amt IS '超限价金额';
COMMENT ON COLUMN yb_inpatient_fee_upload.preselfpay_amt IS '先行自付金额';
COMMENT ON COLUMN yb_inpatient_fee_upload.inscp_scp_amt IS '符合政策范围金额';
COMMENT ON COLUMN yb_inpatient_fee_upload.lmt_used_flag IS '限制使用标志';
COMMENT ON COLUMN yb_inpatient_fee_upload.platform_memo IS '平台备注';

COMMENT ON COLUMN yb_settlement_record.settle_mode IS '结算模式';
COMMENT ON COLUMN yb_settlement_record.psn_name IS '人员姓名';
COMMENT ON COLUMN yb_settlement_record.insutype IS '险种类型';
COMMENT ON COLUMN yb_settlement_record.med_type IS '医疗类别';
COMMENT ON COLUMN yb_settlement_record.setl_time IS '结算时间';
COMMENT ON COLUMN yb_settlement_record.fulamt_ownpay_amt IS '全自费金额';
COMMENT ON COLUMN yb_settlement_record.overlmt_selfpay IS '超限价自费费用';
COMMENT ON COLUMN yb_settlement_record.preselfpay_amt IS '先行自付金额';
COMMENT ON COLUMN yb_settlement_record.inscp_scp_amt IS '符合政策范围金额';
COMMENT ON COLUMN yb_settlement_record.hifp_pay IS '基本医疗保险统筹基金支出';
COMMENT ON COLUMN yb_settlement_record.cvlserv_pay IS '公务员医疗补助资金支出';
COMMENT ON COLUMN yb_settlement_record.hifmi_pay IS '居民大病保险资金支出';
COMMENT ON COLUMN yb_settlement_record.hifob_pay IS '职工大额医疗费用补助基金支出';
COMMENT ON COLUMN yb_settlement_record.hifes_pay IS '企业补充医疗保险基金支出';
COMMENT ON COLUMN yb_settlement_record.cvlserv_flag IS '公务员标志';
COMMENT ON COLUMN yb_settlement_record.pool_prop_selfpay IS '基本医疗保险统筹基金支付比例';
COMMENT ON COLUMN yb_settlement_record.maf_pay IS '医疗救助基金支出';
COMMENT ON COLUMN yb_settlement_record.oth_pay IS '其他支出';
COMMENT ON COLUMN yb_settlement_record.psn_part_amt IS '个人负担总金额';
COMMENT ON COLUMN yb_settlement_record.hosp_part_amt IS '医院负担金额';
COMMENT ON COLUMN yb_settlement_record.balc IS '余额';
COMMENT ON COLUMN yb_settlement_record.acct_mulaid_pay IS '个人账户共济支付金额';
COMMENT ON COLUMN yb_settlement_record.psn_type IS '人员类别';
COMMENT ON COLUMN yb_settlement_record.act_pay_dedc IS '实际支付起付线';
COMMENT ON COLUMN yb_settlement_record.hifdm_pay IS '伤残人员医疗保障基金支出';
COMMENT ON COLUMN yb_settlement_record.exp_content IS '扩展内容JSON';
COMMENT ON COLUMN yb_settlement_record.medins_setl_id IS '医药机构结算ID';
COMMENT ON COLUMN yb_settlement_record.msgid_2207 IS '2207发送方报文ID';
COMMENT ON COLUMN yb_settlement_record.clr_optins IS '清算经办机构';
COMMENT ON COLUMN yb_settlement_record.clr_way IS '清算方式';
COMMENT ON COLUMN yb_settlement_record.clr_type IS '清算类别';
COMMENT ON COLUMN yb_settlement_record.fund_breakdown_json IS '基金分项JSON';

COMMENT ON COLUMN yb_settlement_reversal.invoke_log_id IS '调用日志标识';
