-- 门诊 2204 费用上传及平台分割结果（对齐 YbClinicFeeUpload）

CREATE TABLE IF NOT EXISTS yb_clinic_fee_upload (
    id                      BIGINT       NOT NULL PRIMARY KEY,
    encounter_id            BIGINT       NOT NULL,
    charge_item_id          BIGINT       NOT NULL,
    feedetl_sn              VARCHAR(30)  NOT NULL,
    mdtrt_id                VARCHAR(30)  NOT NULL,
    chrg_bchno              VARCHAR(30)  NOT NULL,
    pre_settle_id           BIGINT,
    upload_status           VARCHAR(16)  NOT NULL,
    det_item_fee_sumamt     NUMERIC(18, 2),
    chrgitm_lv              VARCHAR(3),
    med_chrgitm_type        VARCHAR(6),
    selfpay_prop            NUMERIC(5, 4),
    fulamt_ownpay_amt       NUMERIC(18, 2),
    overlmt_amt             NUMERIC(18, 2),
    preselfpay_amt          NUMERIC(18, 2),
    inscp_scp_amt           NUMERIC(18, 2),
    lmt_used_flag           VARCHAR(3),
    platform_memo           VARCHAR(500),
    platform_result_json    TEXT,
    uploaded_at             TIMESTAMP,

    created_at              TIMESTAMP    NOT NULL,
    updated_at              TIMESTAMP,
    created_by              BIGINT,
    updated_by              BIGINT,
    is_deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMP,
    deleted_by              BIGINT,
    tenant_id               BIGINT       NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_yb_clinic_fee_upload_item
    ON yb_clinic_fee_upload (charge_item_id, chrg_bchno, tenant_id)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_yb_clinic_fee_upload_bchno
    ON yb_clinic_fee_upload (chrg_bchno, upload_status, tenant_id)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE yb_clinic_fee_upload IS '2204 门诊费用上传及平台分割结果';
COMMENT ON COLUMN yb_clinic_fee_upload.id IS '主键';
COMMENT ON COLUMN yb_clinic_fee_upload.encounter_id IS '就诊标识';
COMMENT ON COLUMN yb_clinic_fee_upload.charge_item_id IS '收费项标识';
COMMENT ON COLUMN yb_clinic_fee_upload.feedetl_sn IS '费用明细流水号';
COMMENT ON COLUMN yb_clinic_fee_upload.mdtrt_id IS '就诊标识';
COMMENT ON COLUMN yb_clinic_fee_upload.chrg_bchno IS '收费批次号';
COMMENT ON COLUMN yb_clinic_fee_upload.pre_settle_id IS '预结算标识';
COMMENT ON COLUMN yb_clinic_fee_upload.upload_status IS '上传状态';
COMMENT ON COLUMN yb_clinic_fee_upload.det_item_fee_sumamt IS '明细项目费用总额';
COMMENT ON COLUMN yb_clinic_fee_upload.chrgitm_lv IS '收费项目等级';
COMMENT ON COLUMN yb_clinic_fee_upload.med_chrgitm_type IS '医疗收费项目类别';
COMMENT ON COLUMN yb_clinic_fee_upload.selfpay_prop IS '自付比例';
COMMENT ON COLUMN yb_clinic_fee_upload.fulamt_ownpay_amt IS '全自费金额';
COMMENT ON COLUMN yb_clinic_fee_upload.overlmt_amt IS '超限价金额';
COMMENT ON COLUMN yb_clinic_fee_upload.preselfpay_amt IS '先行自付金额';
COMMENT ON COLUMN yb_clinic_fee_upload.inscp_scp_amt IS '符合政策范围金额';
COMMENT ON COLUMN yb_clinic_fee_upload.lmt_used_flag IS '限制使用标志';
COMMENT ON COLUMN yb_clinic_fee_upload.platform_memo IS '平台备注';
COMMENT ON COLUMN yb_clinic_fee_upload.platform_result_json IS '平台分割结果JSON';
COMMENT ON COLUMN yb_clinic_fee_upload.uploaded_at IS '上传时间';
COMMENT ON COLUMN yb_clinic_fee_upload.created_at IS '创建时间';
COMMENT ON COLUMN yb_clinic_fee_upload.updated_at IS '最后更新时间';
COMMENT ON COLUMN yb_clinic_fee_upload.created_by IS '创建人';
COMMENT ON COLUMN yb_clinic_fee_upload.updated_by IS '最后更新人';
COMMENT ON COLUMN yb_clinic_fee_upload.is_deleted IS '软删标记';
COMMENT ON COLUMN yb_clinic_fee_upload.deleted_at IS '删除时间';
COMMENT ON COLUMN yb_clinic_fee_upload.deleted_by IS '删除人';
COMMENT ON COLUMN yb_clinic_fee_upload.tenant_id IS '租户标识';
