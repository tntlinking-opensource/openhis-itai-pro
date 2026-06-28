-- mrms_qc_run（建表）
-- Flyway V94

CREATE TABLE IF NOT EXISTS mrms_qc_run (
    id                  BIGINT       NOT NULL PRIMARY KEY,
    tenant_id           BIGINT       NOT NULL,
    case_record_id      BIGINT       NOT NULL REFERENCES mrms_case_record(id),
    trigger_code        VARCHAR(32)  NOT NULL,
    defect_open_count   INTEGER      NOT NULL DEFAULT 0,
    defect_warn_count   INTEGER      NOT NULL DEFAULT 0,
    passed              BOOLEAN      NOT NULL,
    finished_at         TIMESTAMP    NOT NULL,
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP,
    created_by          BIGINT,
    updated_by          BIGINT,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    deleted_by          BIGINT
);

CREATE INDEX IF NOT EXISTS idx_mrms_qc_run_cr
    ON mrms_qc_run (case_record_id, finished_at DESC)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE mrms_qc_run IS '病案质控执行批次';
COMMENT ON COLUMN mrms_qc_run.id IS '主键';
COMMENT ON COLUMN mrms_qc_run.tenant_id IS '租户标识';
COMMENT ON COLUMN mrms_qc_run.case_record_id IS '病案记录标识';
COMMENT ON COLUMN mrms_qc_run.trigger_code IS '触发方式编码';
COMMENT ON COLUMN mrms_qc_run.defect_open_count IS '未关闭缺陷数';
COMMENT ON COLUMN mrms_qc_run.defect_warn_count IS '警告级缺陷数';
COMMENT ON COLUMN mrms_qc_run.passed IS '是否通过';
COMMENT ON COLUMN mrms_qc_run.finished_at IS '执行完成时间';
COMMENT ON COLUMN mrms_qc_run.created_at IS '创建时间';
COMMENT ON COLUMN mrms_qc_run.updated_at IS '最后更新时间';
COMMENT ON COLUMN mrms_qc_run.created_by IS '创建人';
COMMENT ON COLUMN mrms_qc_run.updated_by IS '最后更新人';
COMMENT ON COLUMN mrms_qc_run.is_deleted IS '软删标记';
COMMENT ON COLUMN mrms_qc_run.deleted_at IS '删除时间';
COMMENT ON COLUMN mrms_qc_run.deleted_by IS '删除人';
