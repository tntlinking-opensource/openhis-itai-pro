-- emr_metadata_field_def（建表）
-- Flyway V56

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

COMMENT ON TABLE emr_metadata_field_def IS 'EMR元数据字段定义(fieldKey目录)';