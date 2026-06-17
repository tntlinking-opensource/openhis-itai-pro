-- Whale Report V1：表结构（模板、元素目录、C 类报表、打印日志、打印绑定）
-- history: flyway_schema_history_report

CREATE TABLE IF NOT EXISTS rpt_template (
    id                  BIGINT PRIMARY KEY,
    tenant_id           BIGINT       NOT NULL,
    code                VARCHAR(64)  NOT NULL,
    name                VARCHAR(128) NOT NULL,
    pipeline            VARCHAR(16)  NOT NULL,
    layout_json         TEXT,
    dataset_code        VARCHAR(64),
    status              VARCHAR(16)  NOT NULL DEFAULT 'draft',
    version             INTEGER      NOT NULL DEFAULT 1,
    organization_id     BIGINT,
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP,
    created_by          BIGINT,
    updated_by          BIGINT,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    deleted_by          BIGINT
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_rpt_template_pub
    ON rpt_template (tenant_id, code, version)
    WHERE is_deleted = FALSE AND status = 'published';

CREATE INDEX IF NOT EXISTS uk_rpt_template_draft
    ON rpt_template (tenant_id, code, status)
    WHERE is_deleted = FALSE AND status = 'draft';

CREATE TABLE IF NOT EXISTS rpt_print_element_def (
    id                  BIGINT PRIMARY KEY,
    tenant_id           BIGINT       NOT NULL,
    pack_code           VARCHAR(64)  NOT NULL,
    element_code        VARCHAR(64)  NOT NULL,
    label               VARCHAR(128) NOT NULL,
    render_type         VARCHAR(32)  NOT NULL,
    bind_path           VARCHAR(256),
    kv_items_json       TEXT,
    column_defs_json    TEXT,
    default_style_json  TEXT,
    default_label       VARCHAR(256),
    box_json            TEXT,
    layout_mode         VARCHAR(16)  NOT NULL DEFAULT 'flow',
    sort_order          INTEGER      NOT NULL DEFAULT 0,
    required            BOOLEAN      NOT NULL DEFAULT FALSE,
    enabled             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_rpt_print_element
    ON rpt_print_element_def (tenant_id, pack_code, element_code)
    WHERE is_deleted = FALSE;

CREATE TABLE IF NOT EXISTS rpt_definition (
    id                      BIGINT PRIMARY KEY,
    tenant_id               BIGINT       NOT NULL,
    report_code             VARCHAR(64)  NOT NULL,
    name                    VARCHAR(128) NOT NULL,
    loader_bean             VARCHAR(128) NOT NULL,
    default_columns_json    TEXT,
    allow_column_customize  BOOLEAN      NOT NULL DEFAULT TRUE,
    export_pdf_enabled      BOOLEAN      NOT NULL DEFAULT FALSE,
    pdf_template_code       VARCHAR(64),
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
    report_code         VARCHAR(64)  NOT NULL,
    user_id             BIGINT,
    organization_id     BIGINT,
    columns_json        TEXT         NOT NULL,
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS rpt_print_event_log (
    id                      BIGINT PRIMARY KEY,
    tenant_id               BIGINT       NOT NULL,
    event_type              VARCHAR(32)  NOT NULL,
    target_type             VARCHAR(32)  NOT NULL,
    target_code             VARCHAR(64)  NOT NULL,
    template_version        INTEGER,
    dataset_code            VARCHAR(64),
    biz_ref_json            TEXT,
    operator_id             BIGINT,
    operator_name           VARCHAR(64),
    organization_id         BIGINT,
    copies                  INTEGER      NOT NULL DEFAULT 0,
    status                  VARCHAR(16)  NOT NULL,
    error_code              VARCHAR(64),
    error_message           VARCHAR(512),
    content_hash            VARCHAR(64),
    content_type            VARCHAR(64),
    printer_name            VARCHAR(128),
    client_job_id           VARCHAR(64),
    win_duration_ms         INTEGER,
    render_duration_ms      INTEGER,
    client_ip               VARCHAR(64),
    user_agent              VARCHAR(256),
    source                  VARCHAR(32)  NOT NULL DEFAULT 'WEB',
    parent_event_id         BIGINT,
    created_at              TIMESTAMP    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_rpt_print_evt_tenant_time
    ON rpt_print_event_log (tenant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_rpt_print_evt_operator
    ON rpt_print_event_log (tenant_id, operator_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_rpt_print_evt_target
    ON rpt_print_event_log (tenant_id, target_code, event_type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_rpt_print_evt_job
    ON rpt_print_event_log (client_job_id)
    WHERE client_job_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS rpt_print_daily_summary (
    id                      BIGINT PRIMARY KEY,
    tenant_id               BIGINT       NOT NULL,
    stat_date               DATE         NOT NULL,
    operator_id             BIGINT       NOT NULL,
    target_type             VARCHAR(32)  NOT NULL,
    target_code             VARCHAR(64)  NOT NULL,
    event_type              VARCHAR(32)  NOT NULL,
    success_count           INTEGER      NOT NULL DEFAULT 0,
    fail_count              INTEGER      NOT NULL DEFAULT 0,
    total_copies            INTEGER      NOT NULL DEFAULT 0,
    last_event_at           TIMESTAMP,
    updated_at              TIMESTAMP    NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_rpt_print_daily_sum
    ON rpt_print_daily_summary (tenant_id, stat_date, operator_id, target_type, target_code, event_type);

-- 院点打印模板绑定：业务场景 binding_code → template_code
CREATE TABLE IF NOT EXISTS rpt_print_binding (
    id              BIGINT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL,
    binding_code    VARCHAR(64)  NOT NULL,
    template_code   VARCHAR(64)  NOT NULL,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP,
    created_by      BIGINT,
    updated_by      BIGINT,
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at      TIMESTAMP,
    deleted_by      BIGINT
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_rpt_print_binding
    ON rpt_print_binding (tenant_id, binding_code)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE rpt_template IS '报表 template';

COMMENT ON COLUMN rpt_template.id IS '主键';
COMMENT ON COLUMN rpt_template.tenant_id IS '租户标识';
COMMENT ON COLUMN rpt_template.code IS 'code';
COMMENT ON COLUMN rpt_template.name IS 'name';
COMMENT ON COLUMN rpt_template.pipeline IS 'pipeline';
COMMENT ON COLUMN rpt_template.layout_json IS 'layout json';
COMMENT ON COLUMN rpt_template.dataset_code IS 'dataset code';
COMMENT ON COLUMN rpt_template.status IS 'status';
COMMENT ON COLUMN rpt_template.version IS '乐观锁版本号';
COMMENT ON COLUMN rpt_template.organization_id IS 'organization id';
COMMENT ON COLUMN rpt_template.created_at IS '创建时间';
COMMENT ON COLUMN rpt_template.updated_at IS '最后更新时间';
COMMENT ON COLUMN rpt_template.created_by IS '创建人';
COMMENT ON COLUMN rpt_template.updated_by IS '最后更新人';
COMMENT ON COLUMN rpt_template.is_deleted IS '软删标记';
COMMENT ON COLUMN rpt_template.deleted_at IS '删除时间';
COMMENT ON COLUMN rpt_template.deleted_by IS '删除人';

COMMENT ON TABLE rpt_print_element_def IS '报表 print element def';

COMMENT ON COLUMN rpt_print_element_def.id IS '主键';
COMMENT ON COLUMN rpt_print_element_def.tenant_id IS '租户标识';
COMMENT ON COLUMN rpt_print_element_def.pack_code IS 'pack code';
COMMENT ON COLUMN rpt_print_element_def.element_code IS 'element code';
COMMENT ON COLUMN rpt_print_element_def.label IS 'label';
COMMENT ON COLUMN rpt_print_element_def.render_type IS 'render type';
COMMENT ON COLUMN rpt_print_element_def.bind_path IS 'bind path';
COMMENT ON COLUMN rpt_print_element_def.kv_items_json IS 'kv items json';
COMMENT ON COLUMN rpt_print_element_def.column_defs_json IS 'column defs json';
COMMENT ON COLUMN rpt_print_element_def.default_style_json IS 'default style json';
COMMENT ON COLUMN rpt_print_element_def.default_label IS 'default label';
COMMENT ON COLUMN rpt_print_element_def.box_json IS 'box json';
COMMENT ON COLUMN rpt_print_element_def.layout_mode IS 'layout mode';
COMMENT ON COLUMN rpt_print_element_def.sort_order IS 'sort order';
COMMENT ON COLUMN rpt_print_element_def.required IS 'required';
COMMENT ON COLUMN rpt_print_element_def.enabled IS 'enabled';
COMMENT ON COLUMN rpt_print_element_def.created_at IS '创建时间';
COMMENT ON COLUMN rpt_print_element_def.updated_at IS '最后更新时间';
COMMENT ON COLUMN rpt_print_element_def.is_deleted IS '软删标记';

COMMENT ON TABLE rpt_definition IS 'C 类报表定义';

COMMENT ON COLUMN rpt_definition.id IS '主键';
COMMENT ON COLUMN rpt_definition.tenant_id IS '租户标识';
COMMENT ON COLUMN rpt_definition.report_code IS 'report code';
COMMENT ON COLUMN rpt_definition.name IS 'name';
COMMENT ON COLUMN rpt_definition.loader_bean IS 'loader bean';
COMMENT ON COLUMN rpt_definition.default_columns_json IS 'default columns json';
COMMENT ON COLUMN rpt_definition.allow_column_customize IS 'allow column customize';
COMMENT ON COLUMN rpt_definition.export_pdf_enabled IS 'export pdf enabled';
COMMENT ON COLUMN rpt_definition.pdf_template_code IS 'pdf template code';
COMMENT ON COLUMN rpt_definition.status IS '状态（draft/published）';
COMMENT ON COLUMN rpt_definition.source_type IS '数据源类型（loader/sql）';
COMMENT ON COLUMN rpt_definition.query_sql IS '查询 SQL';
COMMENT ON COLUMN rpt_definition.header_json IS '表头 JSON';
COMMENT ON COLUMN rpt_definition.footer_json IS '表底 JSON';
COMMENT ON COLUMN rpt_definition.filter_json IS '筛选器 JSON';
COMMENT ON COLUMN rpt_definition.remark IS '备注';
COMMENT ON COLUMN rpt_definition.created_at IS '创建时间';
COMMENT ON COLUMN rpt_definition.updated_at IS '最后更新时间';
COMMENT ON COLUMN rpt_definition.created_by IS '创建人';
COMMENT ON COLUMN rpt_definition.updated_by IS '最后更新人';
COMMENT ON COLUMN rpt_definition.is_deleted IS '软删标记';
COMMENT ON COLUMN rpt_definition.deleted_at IS '删除时间';
COMMENT ON COLUMN rpt_definition.deleted_by IS '删除人';

COMMENT ON TABLE rpt_column_override IS '报表列配置覆盖';

COMMENT ON COLUMN rpt_column_override.id IS '主键';
COMMENT ON COLUMN rpt_column_override.tenant_id IS '租户标识';
COMMENT ON COLUMN rpt_column_override.report_code IS '报表编码';
COMMENT ON COLUMN rpt_column_override.user_id IS '用户标识（个人覆盖）';
COMMENT ON COLUMN rpt_column_override.organization_id IS '机构标识（机构覆盖）';
COMMENT ON COLUMN rpt_column_override.columns_json IS '列配置 JSON';
COMMENT ON COLUMN rpt_column_override.created_at IS '创建时间';
COMMENT ON COLUMN rpt_column_override.updated_at IS '最后更新时间';
COMMENT ON COLUMN rpt_column_override.is_deleted IS '软删标记';

COMMENT ON TABLE rpt_print_event_log IS '报表 print event log';

COMMENT ON COLUMN rpt_print_event_log.id IS '主键';
COMMENT ON COLUMN rpt_print_event_log.tenant_id IS '租户标识';
COMMENT ON COLUMN rpt_print_event_log.event_type IS 'event type';
COMMENT ON COLUMN rpt_print_event_log.target_type IS 'target type';
COMMENT ON COLUMN rpt_print_event_log.target_code IS 'target code';
COMMENT ON COLUMN rpt_print_event_log.template_version IS 'template version';
COMMENT ON COLUMN rpt_print_event_log.dataset_code IS 'dataset code';
COMMENT ON COLUMN rpt_print_event_log.biz_ref_json IS 'biz ref json';
COMMENT ON COLUMN rpt_print_event_log.operator_id IS 'operator id';
COMMENT ON COLUMN rpt_print_event_log.operator_name IS 'operator name';
COMMENT ON COLUMN rpt_print_event_log.organization_id IS 'organization id';
COMMENT ON COLUMN rpt_print_event_log.copies IS 'copies';
COMMENT ON COLUMN rpt_print_event_log.status IS 'status';
COMMENT ON COLUMN rpt_print_event_log.error_code IS 'error code';
COMMENT ON COLUMN rpt_print_event_log.error_message IS 'error message';
COMMENT ON COLUMN rpt_print_event_log.content_hash IS 'content hash';
COMMENT ON COLUMN rpt_print_event_log.content_type IS 'content type';
COMMENT ON COLUMN rpt_print_event_log.printer_name IS 'printer name';
COMMENT ON COLUMN rpt_print_event_log.client_job_id IS 'client job id';
COMMENT ON COLUMN rpt_print_event_log.win_duration_ms IS 'win duration ms';
COMMENT ON COLUMN rpt_print_event_log.render_duration_ms IS 'render duration ms';
COMMENT ON COLUMN rpt_print_event_log.client_ip IS 'client ip';
COMMENT ON COLUMN rpt_print_event_log.user_agent IS 'user agent';
COMMENT ON COLUMN rpt_print_event_log.source IS 'source';
COMMENT ON COLUMN rpt_print_event_log.parent_event_id IS 'parent event id';
COMMENT ON COLUMN rpt_print_event_log.created_at IS '创建时间';

COMMENT ON TABLE rpt_print_daily_summary IS '报表 print daily summary';

COMMENT ON COLUMN rpt_print_daily_summary.id IS '主键';
COMMENT ON COLUMN rpt_print_daily_summary.tenant_id IS '租户标识';
COMMENT ON COLUMN rpt_print_daily_summary.stat_date IS 'stat date';
COMMENT ON COLUMN rpt_print_daily_summary.operator_id IS 'operator id';
COMMENT ON COLUMN rpt_print_daily_summary.target_type IS 'target type';
COMMENT ON COLUMN rpt_print_daily_summary.target_code IS 'target code';
COMMENT ON COLUMN rpt_print_daily_summary.event_type IS 'event type';
COMMENT ON COLUMN rpt_print_daily_summary.success_count IS 'success count';
COMMENT ON COLUMN rpt_print_daily_summary.fail_count IS 'fail count';
COMMENT ON COLUMN rpt_print_daily_summary.total_copies IS 'total copies';
COMMENT ON COLUMN rpt_print_daily_summary.last_event_at IS 'last event at';
COMMENT ON COLUMN rpt_print_daily_summary.updated_at IS '最后更新时间';

COMMENT ON TABLE rpt_print_binding IS '报表 print binding';

COMMENT ON COLUMN rpt_print_binding.id IS '主键';
COMMENT ON COLUMN rpt_print_binding.tenant_id IS '租户标识';
COMMENT ON COLUMN rpt_print_binding.binding_code IS 'binding code';
COMMENT ON COLUMN rpt_print_binding.template_code IS 'template code';
COMMENT ON COLUMN rpt_print_binding.created_at IS '创建时间';
COMMENT ON COLUMN rpt_print_binding.updated_at IS '最后更新时间';
COMMENT ON COLUMN rpt_print_binding.created_by IS '创建人';
COMMENT ON COLUMN rpt_print_binding.updated_by IS '最后更新人';
COMMENT ON COLUMN rpt_print_binding.is_deleted IS '软删标记';
COMMENT ON COLUMN rpt_print_binding.deleted_at IS '删除时间';
COMMENT ON COLUMN rpt_print_binding.deleted_by IS '删除人';

