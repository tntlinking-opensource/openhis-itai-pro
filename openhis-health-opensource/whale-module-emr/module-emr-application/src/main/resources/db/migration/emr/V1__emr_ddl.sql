-- EMR DDL（清库压扁）
-- 表：document / data_item_index / document_type / document_version / metadata_field_def / template
-- category_code 五类：OUTPATIENT / INPATIENT / NOTICE / SURGERY / OTHER

-- from V1__emr_document.sql
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

-- from V2__emr_document_data_item_index.sql
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

-- from V3__emr_document_type.sql
-- emr_document_type（建表）
-- Flyway V3

CREATE TABLE IF NOT EXISTS emr_document_type (
    id                  BIGINT       NOT NULL PRIMARY KEY,
    code                VARCHAR(32)  NOT NULL,
    name                VARCHAR(64)  NOT NULL,
    default_template_id BIGINT,
    is_enabled          BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order          INT,
    category_code       VARCHAR(32),

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

CREATE INDEX IF NOT EXISTS idx_emr_document_type_category
    ON emr_document_type (category_code);
COMMENT ON TABLE emr_document_type IS '电子病历 document type';
COMMENT ON COLUMN emr_document_type.id IS '主键';
COMMENT ON COLUMN emr_document_type.code IS '文书类型配置';
COMMENT ON COLUMN emr_document_type.name IS '文书类型名称';
COMMENT ON COLUMN emr_document_type.default_template_id IS '默认模板 ID';
COMMENT ON COLUMN emr_document_type.is_enabled IS '是否启用';
COMMENT ON COLUMN emr_document_type.sort_order IS '排序';
COMMENT ON COLUMN emr_document_type.category_code IS '文书分类：OUTPATIENT=门诊病历 / INPATIENT=住院病历 / NOTICE=告知书 / SURGERY=手术病历 / OTHER=其他';
COMMENT ON COLUMN emr_document_type.created_at IS '创建时间';
COMMENT ON COLUMN emr_document_type.updated_at IS '最后更新时间';
COMMENT ON COLUMN emr_document_type.created_by IS '创建人';
COMMENT ON COLUMN emr_document_type.updated_by IS '最后更新人';
COMMENT ON COLUMN emr_document_type.is_deleted IS '软删标记';
COMMENT ON COLUMN emr_document_type.deleted_at IS '删除时间';
COMMENT ON COLUMN emr_document_type.deleted_by IS '删除人';
COMMENT ON COLUMN emr_document_type.tenant_id IS '租户标识';

-- from V4__emr_document_version.sql
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

-- from V5__emr_metadata_field_def.sql
-- emr_metadata_field_def（建表）
-- Flyway V5

CREATE TABLE IF NOT EXISTS emr_metadata_field_def (
    id                  BIGINT       NOT NULL PRIMARY KEY,
    field_key           VARCHAR(64)  NOT NULL,
    label               VARCHAR(128) NOT NULL,
    data_type           VARCHAR(16)  NOT NULL DEFAULT 'string',
    editable_default    BOOLEAN      NOT NULL DEFAULT TRUE,
    value_source        VARCHAR(32)  NOT NULL,
    source_path         VARCHAR(256),
    source_config       TEXT,
    default_value       VARCHAR(512),
    mask_rule           VARCHAR(32)  NOT NULL DEFAULT 'NONE',
    format_pattern      VARCHAR(64),
    sort_order          INT          NOT NULL DEFAULT 0,
    is_enabled          BOOLEAN      NOT NULL DEFAULT TRUE,
    remark              VARCHAR(512),

    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP,
    created_by          BIGINT,
    updated_by          BIGINT,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    deleted_by          BIGINT,
    tenant_id           BIGINT
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_emr_metadata_field_key_tenant
    ON emr_metadata_field_def (field_key, tenant_id)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_emr_metadata_field_enabled
    ON emr_metadata_field_def (is_enabled);

CREATE INDEX IF NOT EXISTS idx_emr_metadata_field_source
    ON emr_metadata_field_def (value_source);

CREATE INDEX IF NOT EXISTS idx_emr_metadata_field_tenant
    ON emr_metadata_field_def (tenant_id);
COMMENT ON TABLE emr_metadata_field_def IS '电子病历 metadata field def';
COMMENT ON COLUMN emr_metadata_field_def.id IS '主键';
COMMENT ON COLUMN emr_metadata_field_def.field_key IS '字段key';
COMMENT ON COLUMN emr_metadata_field_def.label IS '显示标签';
COMMENT ON COLUMN emr_metadata_field_def.data_type IS 'data类型';
COMMENT ON COLUMN emr_metadata_field_def.editable_default IS '默认可编辑';
COMMENT ON COLUMN emr_metadata_field_def.value_source IS 'value来源';
COMMENT ON COLUMN emr_metadata_field_def.source_path IS '来源path';
COMMENT ON COLUMN emr_metadata_field_def.source_config IS '来源config';
COMMENT ON COLUMN emr_metadata_field_def.default_value IS '默认值';
COMMENT ON COLUMN emr_metadata_field_def.mask_rule IS '脱敏规则';
COMMENT ON COLUMN emr_metadata_field_def.format_pattern IS '格式模式';
COMMENT ON COLUMN emr_metadata_field_def.sort_order IS '排序号';
COMMENT ON COLUMN emr_metadata_field_def.is_enabled IS '是否启用';
COMMENT ON COLUMN emr_metadata_field_def.remark IS '备注';
COMMENT ON COLUMN emr_metadata_field_def.created_at IS '创建时间';
COMMENT ON COLUMN emr_metadata_field_def.updated_at IS '最后更新时间';
COMMENT ON COLUMN emr_metadata_field_def.created_by IS '创建人';
COMMENT ON COLUMN emr_metadata_field_def.updated_by IS '最后更新人';
COMMENT ON COLUMN emr_metadata_field_def.is_deleted IS '软删标记';
COMMENT ON COLUMN emr_metadata_field_def.deleted_at IS '删除时间';
COMMENT ON COLUMN emr_metadata_field_def.deleted_by IS '删除人';
COMMENT ON COLUMN emr_metadata_field_def.tenant_id IS '租户标识';

-- seed: emr_metadata_field_def

-- from V6__emr_template.sql
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
