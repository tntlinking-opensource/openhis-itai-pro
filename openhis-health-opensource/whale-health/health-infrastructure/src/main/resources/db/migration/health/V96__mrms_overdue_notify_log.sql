-- mrms_overdue_notify_log（建表）
-- Flyway V96

CREATE TABLE IF NOT EXISTS mrms_overdue_notify_log (
    id                  BIGINT PRIMARY KEY,
    case_record_id      BIGINT       NOT NULL,
    notify_type_code    VARCHAR(32)  NOT NULL,
    notify_date         DATE         NOT NULL,
    tenant_id           BIGINT       NOT NULL,
    created_at          TIMESTAMP    NOT NULL,
    created_by          BIGINT,
    updated_at          TIMESTAMP,
    updated_by          BIGINT,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    deleted_by          BIGINT
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_mrms_overdue_notify_day
    ON mrms_overdue_notify_log (case_record_id, notify_type_code, notify_date)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_mrms_overdue_notify_cr
    ON mrms_overdue_notify_log (case_record_id, notify_date DESC);

COMMENT ON TABLE mrms_overdue_notify_log IS '逾期通知幂等日志';
COMMENT ON COLUMN mrms_overdue_notify_log.id IS '主键';
COMMENT ON COLUMN mrms_overdue_notify_log.case_record_id IS '病案记录标识';
COMMENT ON COLUMN mrms_overdue_notify_log.notify_type_code IS '通知类型编码';
COMMENT ON COLUMN mrms_overdue_notify_log.notify_date IS '通知日期';
COMMENT ON COLUMN mrms_overdue_notify_log.tenant_id IS '租户标识';
COMMENT ON COLUMN mrms_overdue_notify_log.created_at IS '创建时间';
COMMENT ON COLUMN mrms_overdue_notify_log.created_by IS '创建人';
COMMENT ON COLUMN mrms_overdue_notify_log.updated_at IS '最后更新时间';
COMMENT ON COLUMN mrms_overdue_notify_log.updated_by IS '最后更新人';
COMMENT ON COLUMN mrms_overdue_notify_log.is_deleted IS '软删标记';
COMMENT ON COLUMN mrms_overdue_notify_log.deleted_at IS '删除时间';
COMMENT ON COLUMN mrms_overdue_notify_log.deleted_by IS '删除人';
