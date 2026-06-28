-- mrms_required_doc_type（建表 + 种子）
-- Flyway V95

CREATE TABLE IF NOT EXISTS mrms_required_doc_type (
    id                      BIGINT       NOT NULL PRIMARY KEY,
    tenant_id               BIGINT       NOT NULL,
    document_type_code      VARCHAR(64)  NOT NULL,
    encounter_class_code    VARCHAR(16),
    required                BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMP    NOT NULL,
    updated_at              TIMESTAMP,
    created_by              BIGINT,
    updated_by              BIGINT,
    is_deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMP,
    deleted_by              BIGINT
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_mrms_required_doc_type
    ON mrms_required_doc_type (tenant_id, document_type_code, COALESCE(encounter_class_code, ''))
    WHERE is_deleted = FALSE;

COMMENT ON TABLE mrms_required_doc_type IS '必写文书类型配置';
COMMENT ON COLUMN mrms_required_doc_type.id IS '主键';
COMMENT ON COLUMN mrms_required_doc_type.tenant_id IS '租户标识';
COMMENT ON COLUMN mrms_required_doc_type.document_type_code IS '文书类型编码';
COMMENT ON COLUMN mrms_required_doc_type.encounter_class_code IS '就诊类别编码';
COMMENT ON COLUMN mrms_required_doc_type.required IS '是否必写';
COMMENT ON COLUMN mrms_required_doc_type.created_at IS '创建时间';
COMMENT ON COLUMN mrms_required_doc_type.updated_at IS '最后更新时间';
COMMENT ON COLUMN mrms_required_doc_type.created_by IS '创建人';
COMMENT ON COLUMN mrms_required_doc_type.updated_by IS '最后更新人';
COMMENT ON COLUMN mrms_required_doc_type.is_deleted IS '软删标记';
COMMENT ON COLUMN mrms_required_doc_type.deleted_at IS '删除时间';
COMMENT ON COLUMN mrms_required_doc_type.deleted_by IS '删除人';

INSERT INTO mrms_required_doc_type (id, tenant_id, document_type_code, encounter_class_code, required, created_at, is_deleted)
VALUES
    (881001, 0, 'ADMISSION_RECORD', 'IMP', TRUE, NOW(), FALSE),
    (881002, 0, 'DISCHARGE_RECORD', 'IMP', TRUE, NOW(), FALSE),
    (881003, 0, 'FIRST_PROGRESS_NOTE', 'IMP', TRUE, NOW(), FALSE)
ON CONFLICT (id) DO NOTHING;
