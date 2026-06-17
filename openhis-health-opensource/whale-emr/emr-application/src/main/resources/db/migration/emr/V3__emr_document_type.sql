-- emr_document_type（建表）
-- Flyway V3

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

COMMENT ON TABLE emr_document_type IS '电子病历 document type';

COMMENT ON COLUMN emr_document_type.id IS '主键';
COMMENT ON COLUMN emr_document_type.code IS '文书类型配置';
COMMENT ON COLUMN emr_document_type.name IS '文书类型名称';
COMMENT ON COLUMN emr_document_type.default_template_id IS '默认模板 ID';
COMMENT ON COLUMN emr_document_type.is_enabled IS '是否启用';
COMMENT ON COLUMN emr_document_type.sort_order IS '排序';
COMMENT ON COLUMN emr_document_type.created_at IS '创建时间';
COMMENT ON COLUMN emr_document_type.updated_at IS '最后更新时间';
COMMENT ON COLUMN emr_document_type.created_by IS '创建人';
COMMENT ON COLUMN emr_document_type.updated_by IS '最后更新人';
COMMENT ON COLUMN emr_document_type.is_deleted IS '软删标记';
COMMENT ON COLUMN emr_document_type.deleted_at IS '删除时间';
COMMENT ON COLUMN emr_document_type.deleted_by IS '删除人';
COMMENT ON COLUMN emr_document_type.tenant_id IS '租户标识';

-- seed: emr_document_type（default_template_id 与 V6 模板种子 id 对齐）
INSERT INTO emr_document_type (
    id, code, name, default_template_id, is_enabled, sort_order,
    created_at, is_deleted, tenant_id, created_by
) VALUES
(323100000000000101, 'OUTPATIENT_RECORD', '门诊病历', 323100000000000201, TRUE, 1, TIMESTAMP '2026-06-11 00:00:00+00', FALSE, 1, 1),
(323100000000000102, 'PROGRESS_NOTE',     '病程记录', 323100000000000203, TRUE, 2, TIMESTAMP '2026-06-11 00:00:00+00', FALSE, 1, 1),
(323100000000000103, 'ADMISSION_RECORD',  '入院记录', NULL,                 TRUE, 3, TIMESTAMP '2026-06-11 00:00:00+00', FALSE, 1, 1),
(323100000000000104, 'DISCHARGE_SUMMARY', '出院小结', NULL,                 TRUE, 4, TIMESTAMP '2026-06-11 00:00:00+00', FALSE, 1, 1);
