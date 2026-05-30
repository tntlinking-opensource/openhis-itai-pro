-- emr_document_data_item_index（建表）
-- Flyway V53

CREATE TABLE IF NOT EXISTS emr_document_data_item_index (
    id             BIGINT       NOT NULL PRIMARY KEY,
    document_id    BIGINT       NOT NULL,
    node_type      VARCHAR(16)  NOT NULL,
    data_item_name VARCHAR(128) NOT NULL,

    created_at     TIMESTAMP    NOT NULL,
    updated_at     TIMESTAMP,
    created_by     BIGINT,
    updated_by     BIGINT,
    is_deleted     BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at     TIMESTAMP,
    deleted_by     BIGINT,
    tenant_id      BIGINT
);

CREATE INDEX IF NOT EXISTS idx_emr_doc_data_item_document
    ON emr_document_data_item_index (document_id);

CREATE INDEX IF NOT EXISTS idx_emr_doc_data_item_name
    ON emr_document_data_item_index (data_item_name, document_id);

CREATE INDEX IF NOT EXISTS idx_emr_doc_data_item_tenant
    ON emr_document_data_item_index (tenant_id);

COMMENT ON TABLE emr_document_data_item_index IS '文书数据项名索引(可选)';