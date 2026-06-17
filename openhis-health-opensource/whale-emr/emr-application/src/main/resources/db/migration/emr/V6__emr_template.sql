-- emr_template（建表）
-- Flyway V6

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
    scope_level          VARCHAR(16)  NOT NULL DEFAULT 'HOSPITAL',
    owner_practitioner_id BIGINT,
    encounter_class_code VARCHAR(16),

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

CREATE INDEX IF NOT EXISTS idx_emr_template_scope
    ON emr_template (scope_level);

CREATE INDEX IF NOT EXISTS idx_emr_template_enc_class_code
    ON emr_template (encounter_class_code);

CREATE INDEX IF NOT EXISTS idx_emr_template_owner_practitioner
    ON emr_template (owner_practitioner_id);

COMMENT ON TABLE emr_template IS '电子病历 template';

COMMENT ON COLUMN emr_template.id IS '主键';
COMMENT ON COLUMN emr_template.name IS '电子病历模板实体';
COMMENT ON COLUMN emr_template.code IS '模板编码，唯一';
COMMENT ON COLUMN emr_template.document_type_code IS '关联文书类型编码';
COMMENT ON COLUMN emr_template.organization_id IS '适用科室 ID（organizationId）；scope_level=DEPARTMENT 时必填';
COMMENT ON COLUMN emr_template.encounter_class_enum IS '适用就诊类型（遗留整型字段，新数据请用 encounter_class_code）';
COMMENT ON COLUMN emr_template.encounter_type_enum IS '适用就诊子类型（Encounter.typeEnum 等），如普通门诊/专家门诊等';
COMMENT ON COLUMN emr_template.content IS '模板内容 JSON';
COMMENT ON COLUMN emr_template.is_enabled IS '是否启用';
COMMENT ON COLUMN emr_template.scope_level IS '使用范围：PERSONAL / DEPARTMENT / HOSPITAL';
COMMENT ON COLUMN emr_template.owner_practitioner_id IS '个人模板所有者（医务人员 ID）；scope_level=PERSONAL 时必填';
COMMENT ON COLUMN emr_template.encounter_class_code IS '适用就诊类别（与 health EncounterClass.code 一致）：AMB / IMP / OBSENC 等';
COMMENT ON COLUMN emr_template.created_at IS '创建时间';
COMMENT ON COLUMN emr_template.updated_at IS '最后更新时间';
COMMENT ON COLUMN emr_template.created_by IS '创建人';
COMMENT ON COLUMN emr_template.updated_by IS '最后更新人';
COMMENT ON COLUMN emr_template.is_deleted IS '软删标记';
COMMENT ON COLUMN emr_template.deleted_at IS '删除时间';
COMMENT ON COLUMN emr_template.deleted_by IS '删除人';
COMMENT ON COLUMN emr_template.tenant_id IS '租户标识';

-- seed: emr_template
INSERT INTO emr_template (
    id, name, code, document_type_code, scope_level, encounter_class_code,
    content, is_enabled, created_at, is_deleted, tenant_id, created_by
) VALUES
(
    323100000000000201,
    '门诊病历（标准）',
    'OP_RECORD_STD',
    'OUTPATIENT_RECORD',
    'HOSPITAL',
    'AMB',
    $tpl_op$
{"type":"doc","content":[{"type":"paragraph","attrs":{"textAlign":"center"},"content":[{"type":"text","text":"门诊病历"}]},{"type":"paragraph","content":[{"type":"text","text":"主诉："}]},{"type":"paragraph","content":[{"type":"text","text":"现病史："}]},{"type":"paragraph","content":[{"type":"text","text":"既往史："}]},{"type":"paragraph","content":[{"type":"text","text":"体格检查："}]},{"type":"paragraph","content":[{"type":"text","text":"初步诊断："}]},{"type":"paragraph","content":[{"type":"text","text":"处理意见："}]}]}
$tpl_op$,
    TRUE,
    TIMESTAMP '2026-06-11 00:00:00+00',
    FALSE,
    1,
    1
),
(
    323100000000000202,
    '首次病程记录',
    'PN_FIRST',
    'PROGRESS_NOTE',
    'HOSPITAL',
    'IMP',
    $tpl_pn_first$
{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"首次病程记录"}]},{"type":"paragraph","content":[{"type":"text","text":"病例特点："}]},{"type":"paragraph","content":[{"type":"text","text":"初步诊断："}]},{"type":"paragraph","content":[{"type":"text","text":"诊疗计划："}]}]}
$tpl_pn_first$,
    TRUE,
    TIMESTAMP '2026-06-11 00:00:00+00',
    FALSE,
    1,
    1
),
(
    323100000000000203,
    '日常病程记录',
    'PN_DAILY',
    'PROGRESS_NOTE',
    'HOSPITAL',
    'IMP',
    $tpl_pn_daily$
{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"病程记录"}]},{"type":"paragraph","content":[{"type":"text","text":"病情变化："}]},{"type":"paragraph","content":[{"type":"text","text":"诊疗措施："}]}]}
$tpl_pn_daily$,
    TRUE,
    TIMESTAMP '2026-06-11 00:00:00+00',
    FALSE,
    1,
    1
),
(
    323100000000000204,
    '上级医师查房记录',
    'PN_SENIOR',
    'PROGRESS_NOTE',
    'HOSPITAL',
    'IMP',
    $tpl_pn_senior$
{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"上级医师查房记录"}]},{"type":"paragraph","content":[{"type":"text","text":"查房意见："}]},{"type":"paragraph","content":[{"type":"text","text":"诊疗调整："}]}]}
$tpl_pn_senior$,
    TRUE,
    TIMESTAMP '2026-06-11 00:00:00+00',
    FALSE,
    1,
    1
);
