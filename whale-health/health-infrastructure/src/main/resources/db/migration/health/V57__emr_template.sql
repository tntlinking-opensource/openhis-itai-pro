-- emr_template（建表）
-- Flyway V57

CREATE TABLE IF NOT EXISTS emr_template (
    id                   BIGINT       NOT NULL PRIMARY KEY,
    name                 VARCHAR(128),
    code                 VARCHAR(64),
    document_type_code   VARCHAR(32),
    organization_id      BIGINT,
    encounter_class_enum INTEGER,
    encounter_type_enum  INTEGER,
    content              TEXT,
    is_enabled           BOOLEAN      NOT NULL DEFAULT TRUE,

    created_at           TIMESTAMP    NOT NULL,
    updated_at           TIMESTAMP,
    created_by           BIGINT,
    updated_by           BIGINT,
    is_deleted           BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at           TIMESTAMP,
    deleted_by           BIGINT,
    tenant_id            BIGINT
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_emr_template_code
    ON emr_template (code);

CREATE INDEX IF NOT EXISTS idx_emr_template_type
    ON emr_template (document_type_code);

CREATE INDEX IF NOT EXISTS idx_emr_template_org
    ON emr_template (organization_id);

CREATE INDEX IF NOT EXISTS idx_emr_template_encounter_class
    ON emr_template (encounter_class_enum);

CREATE INDEX IF NOT EXISTS idx_emr_template_encounter_type
    ON emr_template (encounter_type_enum);

CREATE INDEX IF NOT EXISTS idx_emr_template_enabled
    ON emr_template (is_enabled);

CREATE INDEX IF NOT EXISTS idx_emr_template_tenant
    ON emr_template (tenant_id);

COMMENT ON TABLE emr_template IS '电子病历模板';

-- 模板使用范围：个人 / 科室 / 全院；就诊场景：门诊 AMB / 住院 IMP 等

ALTER TABLE emr_template
    ADD COLUMN IF NOT EXISTS scope_level VARCHAR(16);

ALTER TABLE emr_template
    ADD COLUMN IF NOT EXISTS owner_practitioner_id BIGINT;

ALTER TABLE emr_template
    ADD COLUMN IF NOT EXISTS encounter_class_code VARCHAR(16);

-- 历史数据：有科室 → 科室模板，否则全院
UPDATE emr_template
SET scope_level = 'DEPARTMENT'
WHERE organization_id IS NOT NULL
  AND (scope_level IS NULL OR scope_level = '');

UPDATE emr_template
SET scope_level = 'HOSPITAL'
WHERE scope_level IS NULL OR scope_level = '';

-- 住院类文书类型默认 IMP，其余默认 AMB
UPDATE emr_template
SET encounter_class_code = 'IMP'
WHERE (encounter_class_code IS NULL OR encounter_class_code = '')
  AND document_type_code IN ('ADMISSION_NOTE', 'PROGRESS_NOTE', 'DISCHARGE_SUMMARY');

UPDATE emr_template
SET encounter_class_code = 'AMB'
WHERE encounter_class_code IS NULL OR encounter_class_code = '';

ALTER TABLE emr_template
    ALTER COLUMN scope_level SET DEFAULT 'HOSPITAL';

UPDATE emr_template
SET scope_level = 'HOSPITAL'
WHERE scope_level IS NULL OR scope_level = '';

ALTER TABLE emr_template
    ALTER COLUMN scope_level SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_emr_template_scope
    ON emr_template (scope_level);

CREATE INDEX IF NOT EXISTS idx_emr_template_enc_class_code
    ON emr_template (encounter_class_code);

CREATE INDEX IF NOT EXISTS idx_emr_template_owner_practitioner
    ON emr_template (owner_practitioner_id);

COMMENT ON COLUMN emr_template.scope_level IS '使用范围：PERSONAL/DEPARTMENT/HOSPITAL';
COMMENT ON COLUMN emr_template.owner_practitioner_id IS '个人模板所有者（医务人员 ID）';
COMMENT ON COLUMN emr_template.encounter_class_code IS '就诊类别：AMB/IMP/EMER 等，与 EncounterClass.code 一致';