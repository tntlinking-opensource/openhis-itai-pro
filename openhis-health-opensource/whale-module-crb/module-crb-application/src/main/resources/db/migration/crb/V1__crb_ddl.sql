-- V1
-- CRB Flyway V1：前置配置 + 推送队列 + 推送日志
CREATE TABLE crb_org_report_config (
    id          BIGINT       NOT NULL PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL,
    base_url    VARCHAR(512),
    license     VARCHAR(128),
    org_code    VARCHAR(64)  NOT NULL,
    org_name    VARCHAR(256) NOT NULL,
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    is_deleted  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP
);
CREATE INDEX idx_crb_org_report_config_tenant ON crb_org_report_config (tenant_id, enabled);

CREATE TABLE crb_emr_push_task (
    id              BIGINT       NOT NULL PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL,
    resource        VARCHAR(64)  NOT NULL,
    business_key    VARCHAR(256) NOT NULL,
    sla_tier        VARCHAR(32)  NOT NULL,
    operation       VARCHAR(16)  NOT NULL,
    due_at          TIMESTAMP    NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    attempt_count   INT          NOT NULL DEFAULT 0,
    operation_time  TIMESTAMP,
    source_id       BIGINT,
    operator_id     VARCHAR(64),
    last_error      VARCHAR(512),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP,
    CONSTRAINT uk_crb_emr_push_task UNIQUE (tenant_id, resource, business_key, operation)
);
CREATE INDEX idx_crb_emr_push_task_status_due ON crb_emr_push_task (tenant_id, status, due_at);

CREATE TABLE crb_emr_push_log (
    id                BIGINT        NOT NULL PRIMARY KEY,
    tenant_id         BIGINT        NOT NULL,
    task_id           BIGINT,
    resource          VARCHAR(64)   NOT NULL,
    business_key      VARCHAR(256)  NOT NULL,
    request_summary   VARCHAR(2000),
    response_summary  VARCHAR(2000),
    success           BOOLEAN       NOT NULL,
    error_code        VARCHAR(64),
    error_name        VARCHAR(128),
    duration_ms       BIGINT,
    is_deleted        BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMP     NOT NULL
);
CREATE INDEX idx_crb_emr_push_log_task ON crb_emr_push_log (tenant_id, task_id);
