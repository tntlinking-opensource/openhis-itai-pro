-- emr_document_version（建表）
-- Flyway V55

CREATE TABLE IF NOT EXISTS emr_document_version (
    id             BIGINT      NOT NULL PRIMARY KEY,
    document_id    BIGINT      NOT NULL,
    version_number INT         NOT NULL,
    content        TEXT        NOT NULL,

    created_at     TIMESTAMP   NOT NULL,
    updated_at     TIMESTAMP,
    created_by     BIGINT,
    updated_by     BIGINT,
    is_deleted     BOOLEAN     NOT NULL DEFAULT FALSE,
    deleted_at     TIMESTAMP,
    deleted_by     BIGINT,
    tenant_id      BIGINT
);

CREATE INDEX IF NOT EXISTS idx_emr_document_version_doc
    ON emr_document_version (document_id);

CREATE INDEX IF NOT EXISTS idx_emr_document_version_created_at
    ON emr_document_version (created_at);

CREATE INDEX IF NOT EXISTS idx_emr_document_version_tenant
    ON emr_document_version (tenant_id);

COMMENT ON TABLE emr_document_version IS '文书版本快照';