-- V1
-- 电子处方主表：患者历史索引 + 状态机

CREATE TABLE IF NOT EXISTS yb_elep_prescription (
    id                  BIGINT       NOT NULL PRIMARY KEY,
    tenant_id           BIGINT       NOT NULL,
    patient_id          BIGINT       NOT NULL,
    encounter_id        BIGINT       NOT NULL,
    contract_bus_no     VARCHAR(64)  NOT NULL,
    hosp_rxno           VARCHAR(64)  NOT NULL,
    hi_rxno             VARCHAR(64),
    rx_trace_code       VARCHAR(128),
    init_rxno           VARCHAR(64),
    local_status        VARCHAR(32)  NOT NULL,
    rx_stas_codg        VARCHAR(8),
    rx_used_stas_codg   VARCHAR(8),
    prsc_time           TIMESTAMP,
    prsc_dr_name        VARCHAR(64),
    prsc_dept_name      VARCHAR(128),
    main_diag_code      VARCHAR(32),
    main_diag_name      VARCHAR(256),
    rx_type_code        VARCHAR(8),
    drug_summary        VARCHAR(512),
    line_count          INT,
    prescription_no     VARCHAR(64),
    rept_flag           VARCHAR(2),
    max_rept_cnt        INT,
    reptd_cnt           INT,
    vali_end_time       TIMESTAMP,
    platform_synced_at  TIMESTAMP,

    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP,
    created_by          BIGINT,
    updated_by          BIGINT,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    deleted_by          BIGINT
);

CREATE INDEX IF NOT EXISTS idx_yb_elep_rx_patient_time
    ON yb_elep_prescription (tenant_id, patient_id, prsc_time DESC)
    WHERE is_deleted = FALSE;

CREATE UNIQUE INDEX IF NOT EXISTS uk_yb_elep_rx_hosp
    ON yb_elep_prescription (tenant_id, contract_bus_no, hosp_rxno)
    WHERE is_deleted = FALSE;
COMMENT ON TABLE yb_elep_prescription IS '医保电子处方处方';
COMMENT ON COLUMN yb_elep_prescription.id IS '主键';
COMMENT ON COLUMN yb_elep_prescription.tenant_id IS '租户标识';
COMMENT ON COLUMN yb_elep_prescription.patient_id IS '患者标识';
COMMENT ON COLUMN yb_elep_prescription.encounter_id IS '就诊标识';
COMMENT ON COLUMN yb_elep_prescription.contract_bus_no IS '合同bus编号';
COMMENT ON COLUMN yb_elep_prescription.hosp_rxno IS '院内处方号';
COMMENT ON COLUMN yb_elep_prescription.hi_rxno IS '医保处方号';
COMMENT ON COLUMN yb_elep_prescription.rx_trace_code IS 'rx追踪编码';
COMMENT ON COLUMN yb_elep_prescription.init_rxno IS '初始处方号';
COMMENT ON COLUMN yb_elep_prescription.local_status IS 'local状态';
COMMENT ON COLUMN yb_elep_prescription.rx_stas_codg IS '处方状态编码';
COMMENT ON COLUMN yb_elep_prescription.rx_used_stas_codg IS '处方使用状态编码';
COMMENT ON COLUMN yb_elep_prescription.prsc_time IS 'prsc时间';
COMMENT ON COLUMN yb_elep_prescription.prsc_dr_name IS 'prscdr名称';
COMMENT ON COLUMN yb_elep_prescription.prsc_dept_name IS 'prscdept名称';
COMMENT ON COLUMN yb_elep_prescription.main_diag_code IS 'maindiag编码';
COMMENT ON COLUMN yb_elep_prescription.main_diag_name IS 'maindiag名称';
COMMENT ON COLUMN yb_elep_prescription.rx_type_code IS 'rx类型编码';
COMMENT ON COLUMN yb_elep_prescription.drug_summary IS '药品摘要';
COMMENT ON COLUMN yb_elep_prescription.line_count IS '行数';
COMMENT ON COLUMN yb_elep_prescription.prescription_no IS '处方编号';
COMMENT ON COLUMN yb_elep_prescription.rept_flag IS 'rept标志位';
COMMENT ON COLUMN yb_elep_prescription.max_rept_cnt IS 'maxrept数量';
COMMENT ON COLUMN yb_elep_prescription.reptd_cnt IS 'reptd数量';
COMMENT ON COLUMN yb_elep_prescription.vali_end_time IS 'vali结束时间';
COMMENT ON COLUMN yb_elep_prescription.platform_synced_at IS 'platformsynced时间';
COMMENT ON COLUMN yb_elep_prescription.created_at IS '创建时间';
COMMENT ON COLUMN yb_elep_prescription.updated_at IS '最后更新时间';
COMMENT ON COLUMN yb_elep_prescription.created_by IS '创建人';
COMMENT ON COLUMN yb_elep_prescription.updated_by IS '最后更新人';
COMMENT ON COLUMN yb_elep_prescription.is_deleted IS '软删标记';
COMMENT ON COLUMN yb_elep_prescription.deleted_at IS '删除时间';
COMMENT ON COLUMN yb_elep_prescription.deleted_by IS '删除人';

-- V2
-- 电子处方快照：再开真源

CREATE TABLE IF NOT EXISTS yb_elep_prescription_snapshot (
    prescription_id         BIGINT       NOT NULL PRIMARY KEY,
    upload_request_json     TEXT,
    upload_response_json    TEXT,
    sign_response_json      TEXT,
    upload_confirm_json     TEXT,
    pdf_file_path           VARCHAR(512),
    pdf_file_hash           VARCHAR(128),

    created_at              TIMESTAMP    NOT NULL,
    updated_at              TIMESTAMP,
    created_by              BIGINT,
    updated_by              BIGINT,
    is_deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMP,
    deleted_by              BIGINT,
    tenant_id               BIGINT       NOT NULL
);
COMMENT ON TABLE yb_elep_prescription_snapshot IS '医保电子处方处方快照';
COMMENT ON COLUMN yb_elep_prescription_snapshot.prescription_id IS '处方标识';
COMMENT ON COLUMN yb_elep_prescription_snapshot.upload_request_json IS '上传申请JSON';
COMMENT ON COLUMN yb_elep_prescription_snapshot.upload_response_json IS '上传responseJSON';
COMMENT ON COLUMN yb_elep_prescription_snapshot.sign_response_json IS '体征responseJSON';
COMMENT ON COLUMN yb_elep_prescription_snapshot.upload_confirm_json IS '上传confirmJSON';
COMMENT ON COLUMN yb_elep_prescription_snapshot.pdf_file_path IS 'PDF文件路径';
COMMENT ON COLUMN yb_elep_prescription_snapshot.pdf_file_hash IS 'PDFfile哈希';
COMMENT ON COLUMN yb_elep_prescription_snapshot.created_at IS '创建时间';
COMMENT ON COLUMN yb_elep_prescription_snapshot.updated_at IS '最后更新时间';
COMMENT ON COLUMN yb_elep_prescription_snapshot.created_by IS '创建人';
COMMENT ON COLUMN yb_elep_prescription_snapshot.updated_by IS '最后更新人';
COMMENT ON COLUMN yb_elep_prescription_snapshot.is_deleted IS '软删标记';
COMMENT ON COLUMN yb_elep_prescription_snapshot.deleted_at IS '删除时间';
COMMENT ON COLUMN yb_elep_prescription_snapshot.deleted_by IS '删除人';
COMMENT ON COLUMN yb_elep_prescription_snapshot.tenant_id IS '租户标识';

-- V3
-- A000x 运维日志

CREATE TABLE IF NOT EXISTS yb_elep_upload_log (
    id                  BIGINT       NOT NULL PRIMARY KEY,
    prescription_id     BIGINT       NOT NULL,
    op_code             VARCHAR(16)  NOT NULL,
    request_json        TEXT,
    response_json       TEXT,
    success             BOOLEAN      NOT NULL DEFAULT FALSE,
    error_message       VARCHAR(1024),

    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP,
    created_by          BIGINT,
    updated_by          BIGINT,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    deleted_by          BIGINT,
    tenant_id           BIGINT       NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_yb_elep_upload_log_rx
    ON yb_elep_upload_log (prescription_id, created_at DESC)
    WHERE is_deleted = FALSE;
COMMENT ON TABLE yb_elep_upload_log IS '医保电子处方上传日志';
COMMENT ON COLUMN yb_elep_upload_log.id IS '主键';
COMMENT ON COLUMN yb_elep_upload_log.prescription_id IS '处方标识';
COMMENT ON COLUMN yb_elep_upload_log.op_code IS 'op编码';
COMMENT ON COLUMN yb_elep_upload_log.request_json IS '申请JSON';
COMMENT ON COLUMN yb_elep_upload_log.response_json IS '响应JSON';
COMMENT ON COLUMN yb_elep_upload_log.success IS '成功';
COMMENT ON COLUMN yb_elep_upload_log.error_message IS '错误消息';
COMMENT ON COLUMN yb_elep_upload_log.created_at IS '创建时间';
COMMENT ON COLUMN yb_elep_upload_log.updated_at IS '最后更新时间';
COMMENT ON COLUMN yb_elep_upload_log.created_by IS '创建人';
COMMENT ON COLUMN yb_elep_upload_log.updated_by IS '最后更新人';
COMMENT ON COLUMN yb_elep_upload_log.is_deleted IS '软删标记';
COMMENT ON COLUMN yb_elep_upload_log.deleted_at IS '删除时间';
COMMENT ON COLUMN yb_elep_upload_log.deleted_by IS '删除人';
COMMENT ON COLUMN yb_elep_upload_log.tenant_id IS '租户标识';
