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
