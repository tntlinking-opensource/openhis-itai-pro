-- emr_document（建表）
-- Flyway V52

CREATE TABLE IF NOT EXISTS emr_document (
    id                  BIGINT       NOT NULL PRIMARY KEY,
    patient_id          VARCHAR(64),
    encounter_id        VARCHAR(64),
    document_type_code  VARCHAR(32),
    title               VARCHAR(256),
    content             TEXT,
    status              VARCHAR(32),

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

COMMENT ON TABLE emr_document IS '电子病历文书';

-- 文书创建时记录来源模板（用于首次病程唯一性等）

ALTER TABLE emr_document
    ADD COLUMN IF NOT EXISTS source_template_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_emr_document_source_template
    ON emr_document (source_template_id);

COMMENT ON COLUMN emr_document.source_template_id IS '创建文书时使用的模板 ID';