-- emr_document（建表）
-- Flyway V1

CREATE TABLE IF NOT EXISTS emr_document (
    id                  BIGINT       NOT NULL PRIMARY KEY,
    patient_id          VARCHAR(64),
    encounter_id        VARCHAR(64),
    document_type_code  VARCHAR(32),
    title               VARCHAR(256),
    content             TEXT,
    status              VARCHAR(32),
    source_template_id  BIGINT,

    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP,
    created_by          BIGINT,
    updated_by          BIGINT,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    deleted_by          BIGINT,
    tenant_id           BIGINT
);

CREATE INDEX IF NOT EXISTS idx_emr_document_patient
    ON emr_document (patient_id);

CREATE INDEX IF NOT EXISTS idx_emr_document_encounter
    ON emr_document (encounter_id);

CREATE INDEX IF NOT EXISTS idx_emr_document_type
    ON emr_document (document_type_code);

CREATE INDEX IF NOT EXISTS idx_emr_document_status
    ON emr_document (status);

CREATE INDEX IF NOT EXISTS idx_emr_document_tenant
    ON emr_document (tenant_id);

CREATE INDEX IF NOT EXISTS idx_emr_document_source_template
    ON emr_document (source_template_id);
COMMENT ON TABLE emr_document IS '电子病历 document';
COMMENT ON COLUMN emr_document.id IS '主键';
COMMENT ON COLUMN emr_document.patient_id IS '电子病历文书实体';
COMMENT ON COLUMN emr_document.encounter_id IS '就诊 ID';
COMMENT ON COLUMN emr_document.document_type_code IS '文书类型编码，如入院记录、病程记录等';
COMMENT ON COLUMN emr_document.title IS '文档标题';
COMMENT ON COLUMN emr_document.content IS '文档正文内容，Tiptap/ProseMirror JSON';
COMMENT ON COLUMN emr_document.status IS '文档状态：DRAFT / SUBMITTED / LOCKED / ARCHIVED 等';
COMMENT ON COLUMN emr_document.source_template_id IS '创建文书时使用的模板 ID';
COMMENT ON COLUMN emr_document.created_at IS '创建时间';
COMMENT ON COLUMN emr_document.updated_at IS '最后更新时间';
COMMENT ON COLUMN emr_document.created_by IS '创建人';
COMMENT ON COLUMN emr_document.updated_by IS '最后更新人';
COMMENT ON COLUMN emr_document.is_deleted IS '软删标记';
COMMENT ON COLUMN emr_document.deleted_at IS '删除时间';
COMMENT ON COLUMN emr_document.deleted_by IS '删除人';
COMMENT ON COLUMN emr_document.tenant_id IS '租户标识';
