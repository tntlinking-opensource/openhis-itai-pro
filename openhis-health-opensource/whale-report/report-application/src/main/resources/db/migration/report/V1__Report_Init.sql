-- Whale Report V1：统计报表表结构（清库重跑真源）
-- 模块：whale-report · classpath:db/migration/report
-- history：flyway_schema_history_report

CREATE TABLE IF NOT EXISTS rpt_definition (
    id                      BIGINT PRIMARY KEY,
    tenant_id               BIGINT       NOT NULL,
    report_code         VARCHAR(64)  NOT NULL,
    name                    VARCHAR(128) NOT NULL,
    loader_bean             VARCHAR(128) NOT NULL,
    default_columns_json    TEXT,
    allow_column_customize  BOOLEAN      NOT NULL DEFAULT TRUE,
    export_pdf_enabled      BOOLEAN      NOT NULL DEFAULT FALSE,
    pdf_print_code          VARCHAR(64),
    status                  VARCHAR(16)  NOT NULL DEFAULT 'draft',
    source_type             VARCHAR(16)  NOT NULL DEFAULT 'loader',
    query_sql               TEXT,
    header_json             TEXT,
    footer_json             TEXT,
    filter_json             TEXT,
    remark                  VARCHAR(512),
    created_at              TIMESTAMP    NOT NULL,
    updated_at              TIMESTAMP,
    created_by              BIGINT,
    updated_by              BIGINT,
    is_deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMP,
    deleted_by              BIGINT
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_rpt_definition_code
    ON rpt_definition (tenant_id, report_code)
    WHERE is_deleted = FALSE;

CREATE TABLE IF NOT EXISTS rpt_column_override (
    id                  BIGINT PRIMARY KEY,
    tenant_id           BIGINT       NOT NULL,
    report_code     VARCHAR(64)  NOT NULL,
    user_id             BIGINT,
    organization_id     BIGINT,
    columns_json        TEXT         NOT NULL,
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP,
    created_by          BIGINT,
    updated_by          BIGINT,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    deleted_by          BIGINT
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_rpt_column_override_scope
    ON rpt_column_override (tenant_id, report_code, COALESCE(user_id, 0), COALESCE(organization_id, 0))
    WHERE is_deleted = FALSE;
COMMENT ON TABLE rpt_definition IS '统计报表定义（Loader / 院点 SQL）';
COMMENT ON COLUMN rpt_definition.id IS '主键';
COMMENT ON COLUMN rpt_definition.tenant_id IS '租户标识';
COMMENT ON COLUMN rpt_definition.report_code IS '全局唯一报表编码 ReportCode';
COMMENT ON COLUMN rpt_definition.name IS '名称';
COMMENT ON COLUMN rpt_definition.loader_bean IS 'Loader Bean 名（source_type=loader）';
COMMENT ON COLUMN rpt_definition.default_columns_json IS '默认列配置JSON';
COMMENT ON COLUMN rpt_definition.allow_column_customize IS 'allow列customize';
COMMENT ON COLUMN rpt_definition.export_pdf_enabled IS '导出PDFenabled';
COMMENT ON COLUMN rpt_definition.pdf_print_code IS '绑定 Print 模块 printCode（export_pdf_enabled 时）';
COMMENT ON COLUMN rpt_definition.status IS '状态';
COMMENT ON COLUMN rpt_definition.source_type IS '来源类型';
COMMENT ON COLUMN rpt_definition.query_sql IS '查询SQL';
COMMENT ON COLUMN rpt_definition.header_json IS '页眉JSON';
COMMENT ON COLUMN rpt_definition.footer_json IS '页脚JSON';
COMMENT ON COLUMN rpt_definition.filter_json IS '筛选JSON';
COMMENT ON COLUMN rpt_definition.remark IS '备注';
COMMENT ON COLUMN rpt_definition.created_at IS '创建时间';
COMMENT ON COLUMN rpt_definition.updated_at IS '最后更新时间';
COMMENT ON COLUMN rpt_definition.created_by IS '创建人';
COMMENT ON COLUMN rpt_definition.updated_by IS '最后更新人';
COMMENT ON COLUMN rpt_definition.is_deleted IS '软删标记';
COMMENT ON COLUMN rpt_definition.deleted_at IS '删除时间';
COMMENT ON COLUMN rpt_definition.deleted_by IS '删除人';

COMMENT ON TABLE rpt_column_override IS '报表列配置覆盖（用户/院点）';
COMMENT ON COLUMN rpt_column_override.id IS '主键';
COMMENT ON COLUMN rpt_column_override.tenant_id IS '租户标识';
COMMENT ON COLUMN rpt_column_override.report_code IS '报表编码';
COMMENT ON COLUMN rpt_column_override.user_id IS '用户标识';
COMMENT ON COLUMN rpt_column_override.organization_id IS '机构标识';
COMMENT ON COLUMN rpt_column_override.columns_json IS '列配置JSON';
COMMENT ON COLUMN rpt_column_override.created_at IS '创建时间';
COMMENT ON COLUMN rpt_column_override.updated_at IS '最后更新时间';
COMMENT ON COLUMN rpt_column_override.created_by IS '创建人';
COMMENT ON COLUMN rpt_column_override.updated_by IS '最后更新人';
COMMENT ON COLUMN rpt_column_override.is_deleted IS '软删标记';
COMMENT ON COLUMN rpt_column_override.deleted_at IS '删除时间';
COMMENT ON COLUMN rpt_column_override.deleted_by IS '删除人';
