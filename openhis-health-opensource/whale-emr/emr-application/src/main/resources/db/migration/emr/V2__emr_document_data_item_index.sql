-- emr_document_data_item_index（建表）
-- Flyway V2

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

COMMENT ON TABLE emr_document_data_item_index IS '电子病历 document data item index';

COMMENT ON COLUMN emr_document_data_item_index.id IS '主键';
COMMENT ON COLUMN emr_document_data_item_index.document_id IS '文书数据项名索引（可选）';
COMMENT ON COLUMN emr_document_data_item_index.node_type IS '节点类型：region / section / dataElement';
COMMENT ON COLUMN emr_document_data_item_index.data_item_name IS '数据项名，对应节点 attrs.name';
COMMENT ON COLUMN emr_document_data_item_index.created_at IS '创建时间';
COMMENT ON COLUMN emr_document_data_item_index.updated_at IS '最后更新时间';
COMMENT ON COLUMN emr_document_data_item_index.created_by IS '创建人';
COMMENT ON COLUMN emr_document_data_item_index.updated_by IS '最后更新人';
COMMENT ON COLUMN emr_document_data_item_index.is_deleted IS '软删标记';
COMMENT ON COLUMN emr_document_data_item_index.deleted_at IS '删除时间';
COMMENT ON COLUMN emr_document_data_item_index.deleted_by IS '删除人';
COMMENT ON COLUMN emr_document_data_item_index.tenant_id IS '租户标识';

