-- mrms_qc_defect（建表）
-- Flyway V93

CREATE TABLE IF NOT EXISTS mrms_qc_defect (
    id                          BIGINT       NOT NULL PRIMARY KEY,
    tenant_id                   BIGINT       NOT NULL,
    case_record_id              BIGINT       NOT NULL REFERENCES mrms_case_record(id),
    rule_code                   VARCHAR(64),
    defect_message              VARCHAR(1000) NOT NULL,
    severity_code               VARCHAR(16)  NOT NULL,
    status_code                 VARCHAR(16)  NOT NULL,
    responsible_practitioner_id BIGINT,
    rectified_at                TIMESTAMP,
    closed_at                   TIMESTAMP,
    created_at                  TIMESTAMP    NOT NULL,
    updated_at                  TIMESTAMP,
    created_by                  BIGINT,
    updated_by                  BIGINT,
    is_deleted                  BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at                  TIMESTAMP,
    deleted_by                  BIGINT
);

CREATE INDEX IF NOT EXISTS idx_mrms_qc_defect_cr_status
    ON mrms_qc_defect (case_record_id, status_code)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_mrms_qc_defect_cr_rule
    ON mrms_qc_defect (case_record_id, rule_code)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE mrms_qc_defect IS '病案质控缺陷';
COMMENT ON COLUMN mrms_qc_defect.id IS '主键';
COMMENT ON COLUMN mrms_qc_defect.tenant_id IS '租户标识';
COMMENT ON COLUMN mrms_qc_defect.case_record_id IS '病案记录标识';
COMMENT ON COLUMN mrms_qc_defect.rule_code IS '关联规则编码';
COMMENT ON COLUMN mrms_qc_defect.defect_message IS '缺陷描述';
COMMENT ON COLUMN mrms_qc_defect.severity_code IS '严重级别编码';
COMMENT ON COLUMN mrms_qc_defect.status_code IS '缺陷状态编码';
COMMENT ON COLUMN mrms_qc_defect.responsible_practitioner_id IS '责任医护人员标识';
COMMENT ON COLUMN mrms_qc_defect.rectified_at IS '整改完成时间';
COMMENT ON COLUMN mrms_qc_defect.closed_at IS '关闭时间';
COMMENT ON COLUMN mrms_qc_defect.created_at IS '创建时间';
COMMENT ON COLUMN mrms_qc_defect.updated_at IS '最后更新时间';
COMMENT ON COLUMN mrms_qc_defect.created_by IS '创建人';
COMMENT ON COLUMN mrms_qc_defect.updated_by IS '最后更新人';
COMMENT ON COLUMN mrms_qc_defect.is_deleted IS '软删标记';
COMMENT ON COLUMN mrms_qc_defect.deleted_at IS '删除时间';
COMMENT ON COLUMN mrms_qc_defect.deleted_by IS '删除人';
