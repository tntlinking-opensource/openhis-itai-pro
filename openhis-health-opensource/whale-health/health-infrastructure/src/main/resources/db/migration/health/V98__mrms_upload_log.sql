-- mrms_upload_log（建表）
-- Flyway V98

CREATE TABLE IF NOT EXISTS mrms_upload_log (
    id                  BIGINT       NOT NULL PRIMARY KEY,
    tenant_id           BIGINT       NOT NULL,
    case_record_id      BIGINT       NOT NULL REFERENCES mrms_case_record(id),
    contract_bus_no     VARCHAR(64),
    contract_id         BIGINT,
    infno               VARCHAR(16)  NOT NULL DEFAULT '4401',
    status_code         VARCHAR(32)  NOT NULL,
    msgid               VARCHAR(64),
    request_digest      VARCHAR(128),
    response_snapshot   TEXT,
    err_msg             VARCHAR(500),
    infcode             VARCHAR(16),
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP,
    created_by          BIGINT,
    updated_by          BIGINT,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    deleted_by          BIGINT
);

CREATE INDEX IF NOT EXISTS idx_mrms_upload_log_cr
    ON mrms_upload_log (case_record_id, created_at DESC)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_mrms_upload_log_tenant_status
    ON mrms_upload_log (tenant_id, status_code, created_at DESC)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE mrms_upload_log IS '4401 病案首页上传日志';
COMMENT ON COLUMN mrms_upload_log.id IS '主键';
COMMENT ON COLUMN mrms_upload_log.tenant_id IS '租户标识';
COMMENT ON COLUMN mrms_upload_log.case_record_id IS '病案记录标识';
COMMENT ON COLUMN mrms_upload_log.contract_bus_no IS '合约业务号';
COMMENT ON COLUMN mrms_upload_log.contract_id IS '合约标识';
COMMENT ON COLUMN mrms_upload_log.infno IS '医保接口号';
COMMENT ON COLUMN mrms_upload_log.status_code IS '上传状态编码';
COMMENT ON COLUMN mrms_upload_log.msgid IS '报文 msgid';
COMMENT ON COLUMN mrms_upload_log.request_digest IS '请求摘要';
COMMENT ON COLUMN mrms_upload_log.response_snapshot IS '响应快照';
COMMENT ON COLUMN mrms_upload_log.err_msg IS '错误信息';
COMMENT ON COLUMN mrms_upload_log.infcode IS '医保 infcode';
COMMENT ON COLUMN mrms_upload_log.created_at IS '创建时间';
COMMENT ON COLUMN mrms_upload_log.updated_at IS '最后更新时间';
COMMENT ON COLUMN mrms_upload_log.created_by IS '创建人';
COMMENT ON COLUMN mrms_upload_log.updated_by IS '最后更新人';
COMMENT ON COLUMN mrms_upload_log.is_deleted IS '软删标记';
COMMENT ON COLUMN mrms_upload_log.deleted_at IS '删除时间';
COMMENT ON COLUMN mrms_upload_log.deleted_by IS '删除人';
