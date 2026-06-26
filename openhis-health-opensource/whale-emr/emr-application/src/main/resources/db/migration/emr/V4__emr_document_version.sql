-- emr_document_version（建表）
-- Flyway V4

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
COMMENT ON TABLE emr_document_version IS '电子病历 document version';
COMMENT ON COLUMN emr_document_version.id IS '主键';
COMMENT ON COLUMN emr_document_version.document_id IS '文书版本快照';
COMMENT ON COLUMN emr_document_version.version_number IS '版本号，同一文书内递增';
COMMENT ON COLUMN emr_document_version.content IS '版本内容 JSON';
COMMENT ON COLUMN emr_document_version.created_at IS '创建时间';
COMMENT ON COLUMN emr_document_version.updated_at IS '最后更新时间';
COMMENT ON COLUMN emr_document_version.created_by IS '创建人';
COMMENT ON COLUMN emr_document_version.updated_by IS '最后更新人';
COMMENT ON COLUMN emr_document_version.is_deleted IS '软删标记';
COMMENT ON COLUMN emr_document_version.deleted_at IS '删除时间';
COMMENT ON COLUMN emr_document_version.deleted_by IS '删除人';
COMMENT ON COLUMN emr_document_version.tenant_id IS '租户标识';
