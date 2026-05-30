-- emr_document_type（建表）
-- Flyway V54

CREATE TABLE IF NOT EXISTS emr_document_type (
    id                  BIGINT       NOT NULL PRIMARY KEY,
    code                VARCHAR(32)  NOT NULL,
    name                VARCHAR(64)  NOT NULL,
    default_template_id BIGINT,
    is_enabled          BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order          INT,

    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP,
    created_by          BIGINT,
    updated_by          BIGINT,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    deleted_by          BIGINT,
    tenant_id           BIGINT
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_emr_document_type_code
    ON emr_document_type (code);

CREATE INDEX IF NOT EXISTS idx_emr_document_type_enabled
    ON emr_document_type (is_enabled);

CREATE INDEX IF NOT EXISTS idx_emr_document_type_tenant
    ON emr_document_type (tenant_id);

COMMENT ON TABLE emr_document_type IS '文书类型配置';