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
COMMENT ON COLUMN emr_metadata_field_def.field_key IS 'field key';
COMMENT ON COLUMN emr_metadata_field_def.label IS 'label';
COMMENT ON COLUMN emr_metadata_field_def.data_type IS 'data type';
COMMENT ON COLUMN emr_metadata_field_def.editable_default IS 'editable default';
COMMENT ON COLUMN emr_metadata_field_def.value_source IS 'value source';
COMMENT ON COLUMN emr_metadata_field_def.source_path IS 'source path';
COMMENT ON COLUMN emr_metadata_field_def.source_config IS 'source config';
COMMENT ON COLUMN emr_metadata_field_def.default_value IS 'default value';
COMMENT ON COLUMN emr_metadata_field_def.mask_rule IS 'mask rule';
COMMENT ON COLUMN emr_metadata_field_def.format_pattern IS 'format pattern';
COMMENT ON COLUMN emr_metadata_field_def.sort_order IS 'sort order';
COMMENT ON COLUMN emr_metadata_field_def.is_enabled IS 'is enabled';
COMMENT ON COLUMN emr_metadata_field_def.remark IS 'remark';
COMMENT ON COLUMN emr_metadata_field_def.created_at IS '创建时间';
COMMENT ON COLUMN emr_metadata_field_def.updated_at IS '最后更新时间';
COMMENT ON COLUMN emr_metadata_field_def.created_by IS '创建人';
COMMENT ON COLUMN emr_metadata_field_def.updated_by IS '最后更新人';
COMMENT ON COLUMN emr_metadata_field_def.is_deleted IS '软删标记';
COMMENT ON COLUMN emr_metadata_field_def.deleted_at IS '删除时间';
COMMENT ON COLUMN emr_metadata_field_def.deleted_by IS '删除人';
COMMENT ON COLUMN emr_metadata_field_def.tenant_id IS '租户标识';

-- seed: emr_metadata_field_def
INSERT INTO emr_metadata_field_def (
    id, field_key, label, data_type, editable_default, value_source, source_path,
    mask_rule, sort_order, is_enabled, created_at, is_deleted, tenant_id, created_by
) VALUES
(323100000000000001, 'patientName',      '患者姓名', 'string', TRUE,  'PATIENT',   'patient.name',             'NONE',    1,  TRUE, TIMESTAMP '2026-06-11 00:00:00+00', FALSE, 1, 1),
(323100000000000002, 'gender',           '性别',     'string', FALSE, 'PATIENT',   'patient.gender',           'NONE',    2,  TRUE, TIMESTAMP '2026-06-11 00:00:00+00', FALSE, 1, 1),
(323100000000000003, 'age',              '年龄',     'string', FALSE, 'PATIENT',   'patient.age',              'NONE',    3,  TRUE, TIMESTAMP '2026-06-11 00:00:00+00', FALSE, 1, 1),
(323100000000000004, 'idCard',           '证件号',   'string', FALSE, 'PATIENT',   'patient.idCard',           'ID_CARD', 4,  TRUE, TIMESTAMP '2026-06-11 00:00:00+00', FALSE, 1, 1),
(323100000000000005, 'medicalRecordNo',  '病历号',   'string', FALSE, 'PATIENT',   'patient.medicalRecordNo',  'NONE',    5,  TRUE, TIMESTAMP '2026-06-11 00:00:00+00', FALSE, 1, 1),
(323100000000000006, 'phone',            '电话',     'string', TRUE,  'PATIENT',   'patient.phone',            'PHONE',   6,  TRUE, TIMESTAMP '2026-06-11 00:00:00+00', FALSE, 1, 1),
(323100000000000007, 'deptName',         '科室',     'string', FALSE, 'ENCOUNTER', 'encounter.deptName',       'NONE',    10, TRUE, TIMESTAMP '2026-06-11 00:00:00+00', FALSE, 1, 1),
(323100000000000008, 'bedNo',            '床号',     'string', FALSE, 'ENCOUNTER', 'encounter.bedNo',          'NONE',    11, TRUE, TIMESTAMP '2026-06-11 00:00:00+00', FALSE, 1, 1),
(323100000000000009, 'orgName',          '机构名称', 'string', FALSE, 'ORG',       'org.name',                 'NONE',    12, TRUE, TIMESTAMP '2026-06-11 00:00:00+00', FALSE, 1, 1),
(323100000000000010, 'recorderName',     '记录人',   'string', FALSE, 'USER',      'user.displayName',         'NONE',    13, TRUE, TIMESTAMP '2026-06-11 00:00:00+00', FALSE, 1, 1),
(323100000000000011, 'printTime',        '打印时间', 'string', FALSE, 'SYSTEM',    'system.printedAt',         'NONE',    14, TRUE, TIMESTAMP '2026-06-11 00:00:00+00', FALSE, 1, 1),
(323100000000000012, 'primaryDiagnosis', '主诊断',   'string', FALSE, 'DIAGNOSIS', 'diagnosis.primaryDisplay', 'NONE',    15, TRUE, TIMESTAMP '2026-06-11 00:00:00+00', FALSE, 1, 1);
