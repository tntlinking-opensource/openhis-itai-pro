-- CHS Flyway V2：医保文件通知（AS-CHS-CAT-005 M1）

CREATE TABLE IF NOT EXISTS yb_catalog_notice_doc (
    id                      BIGINT       NOT NULL PRIMARY KEY,
    contract_id             BIGINT,
    doc_kind                VARCHAR(32)  NOT NULL,
    category                VARCHAR(32)  NOT NULL,
    title                   VARCHAR(256),
    source_filename         VARCHAR(512),
    source_file_hash        VARCHAR(64)  NOT NULL,
    canonical_pack_uri      VARCHAR(1024) NOT NULL,
    source_file_uri         VARCHAR(1024),
    notice_date             DATE,
    effective_date          DATE,
    parse_status            VARCHAR(32)  NOT NULL,
    apply_status            VARCHAR(32)  NOT NULL,
    sheet_summary_json      TEXT,
    error_message           VARCHAR(1024),
    imported_by             BIGINT,
    imported_at             TIMESTAMP,
    applied_by              BIGINT,
    applied_at              TIMESTAMP,
    line_count              INT          NOT NULL DEFAULT 0,

    created_at              TIMESTAMP    NOT NULL,
    updated_at              TIMESTAMP,
    created_by              BIGINT,
    updated_by              BIGINT,
    is_deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMP,
    deleted_by              BIGINT,
    tenant_id               BIGINT       NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_yb_cat_notice_doc_hash
    ON yb_catalog_notice_doc (tenant_id, source_file_hash)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS ix_yb_cat_notice_doc_list
    ON yb_catalog_notice_doc (tenant_id, doc_kind, category, apply_status, imported_at DESC NULLS LAST, id DESC)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE yb_catalog_notice_doc IS '医保文件通知-文件头';
COMMENT ON COLUMN yb_catalog_notice_doc.source_file_hash IS '标准落地包内容 SHA-256，租户内去重';
COMMENT ON COLUMN yb_catalog_notice_doc.canonical_pack_uri IS '标准落地包存储位置（必留）';
COMMENT ON COLUMN yb_catalog_notice_doc.doc_kind IS 'CATALOG_DELTA / PAYMENT_MAP';
COMMENT ON COLUMN yb_catalog_notice_doc.parse_status IS 'PENDING/PARSED/PARSE_FAILED/PARTIAL';
COMMENT ON COLUMN yb_catalog_notice_doc.apply_status IS 'NOT_APPLIED/APPLIED/REVOKED';

CREATE TABLE IF NOT EXISTS yb_catalog_notice_line (
    id                      BIGINT       NOT NULL PRIMARY KEY,
    doc_id                  BIGINT       NOT NULL,
    action                  VARCHAR(32)  NOT NULL,
    list_type               VARCHAR(30),
    hilist_code             VARCHAR(64),
    project_code            VARCHAR(64),
    hilist_name             VARCHAR(512),
    begin_date              DATE,
    end_date                DATE,
    change_fields           VARCHAR(512),
    chrgitm_lv              VARCHAR(8),
    lmt_used_flag           VARCHAR(8),
    lmt_scope               VARCHAR(512),
    price_limit_amt         NUMERIC(18, 4),
    overlmt_dspo_way        VARCHAR(8),
    dual_channel_flag       VARCHAR(8),
    payload_json            TEXT,
    sheet_name              VARCHAR(128),
    row_no                  INT,
    match_status            VARCHAR(32),
    apply_result            VARCHAR(32),
    line_status             VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    warning_message         VARCHAR(1024),

    created_at              TIMESTAMP    NOT NULL,
    updated_at              TIMESTAMP,
    created_by              BIGINT,
    updated_by              BIGINT,
    is_deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMP,
    deleted_by              BIGINT,
    tenant_id               BIGINT       NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_yb_cat_notice_line_doc_action
    ON yb_catalog_notice_line (doc_id, action)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS ix_yb_cat_notice_line_hilist
    ON yb_catalog_notice_line (tenant_id, hilist_code)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS ix_yb_cat_notice_line_project
    ON yb_catalog_notice_line (tenant_id, project_code)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS ix_yb_cat_notice_line_action_end
    ON yb_catalog_notice_line (tenant_id, action, end_date)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE yb_catalog_notice_line IS '医保文件通知-明细行';
COMMENT ON COLUMN yb_catalog_notice_line.action IS 'ADD/MODIFY/STOP/PROVINCE_MODIFY/SET_PAY_CLASS/UNKNOWN';
COMMENT ON COLUMN yb_catalog_notice_line.payload_json IS '整行规范化 JSON，保留全部原列';
COMMENT ON COLUMN yb_catalog_notice_line.line_status IS 'ACTIVE/SUPERSEDED';

CREATE TABLE IF NOT EXISTS yb_catalog_notice_apply_log (
    id                      BIGINT       NOT NULL PRIMARY KEY,
    doc_id                  BIGINT       NOT NULL,
    line_id                 BIGINT,
    target_table            VARCHAR(64),
    target_id               BIGINT,
    before_json             TEXT,
    after_json              TEXT,
    op                      VARCHAR(64)  NOT NULL,
    affected_refs_json      TEXT,
    operator_id             BIGINT,
    operated_at             TIMESTAMP    NOT NULL,

    created_at              TIMESTAMP    NOT NULL,
    updated_at              TIMESTAMP,
    created_by              BIGINT,
    updated_by              BIGINT,
    is_deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMP,
    deleted_by              BIGINT,
    tenant_id               BIGINT       NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_yb_cat_notice_apply_log_doc
    ON yb_catalog_notice_apply_log (doc_id, operated_at DESC)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE yb_catalog_notice_apply_log IS '医保文件通知-应用审计';
COMMENT ON COLUMN yb_catalog_notice_apply_log.affected_refs_json IS '关联单据（如未收费收费单列表）';

CREATE TABLE IF NOT EXISTS yb_national_catalog_notice_overlay (
    id                      BIGINT       NOT NULL PRIMARY KEY,
    contract_id             BIGINT       NOT NULL,
    list_type               VARCHAR(30)  NOT NULL,
    hilist_code             VARCHAR(64)  NOT NULL,
    notice_avali_flag       VARCHAR(8),
    notice_end_date         DATE,
    notice_patch_json       TEXT,
    last_notice_doc_id      BIGINT,
    last_notice_line_id     BIGINT,
    effective_at            TIMESTAMP,

    created_at              TIMESTAMP    NOT NULL,
    updated_at              TIMESTAMP,
    created_by              BIGINT,
    updated_by              BIGINT,
    is_deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMP,
    deleted_by              BIGINT,
    tenant_id               BIGINT       NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_yb_nat_catalog_notice_overlay_biz
    ON yb_national_catalog_notice_overlay (tenant_id, contract_id, list_type, hilist_code)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE yb_national_catalog_notice_overlay IS '国家目录通知叠加（不改 13xx 镜像主列）';
COMMENT ON COLUMN yb_national_catalog_notice_overlay.notice_avali_flag IS '通知口径可用：1 可用 / 0 停用';

CREATE TABLE IF NOT EXISTS yb_service_project_map (
    id                      BIGINT       NOT NULL PRIMARY KEY,
    project_code            VARCHAR(64)  NOT NULL,
    hilist_code             VARCHAR(64),
    status                  VARCHAR(32)  NOT NULL,
    notice_doc_id           BIGINT,
    notice_line_id          BIGINT,
    mapped_at               TIMESTAMP,

    created_at              TIMESTAMP    NOT NULL,
    updated_at              TIMESTAMP,
    created_by              BIGINT,
    updated_by              BIGINT,
    is_deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMP,
    deleted_by              BIGINT,
    tenant_id               BIGINT       NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_yb_service_project_map_code
    ON yb_service_project_map (tenant_id, project_code)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE yb_service_project_map IS '01* 物价项目码与国家目录编码映射';
COMMENT ON COLUMN yb_service_project_map.status IS 'MAPPED/PENDING';

CREATE TABLE IF NOT EXISTS yb_catalog_notice_todo (
    id                      BIGINT       NOT NULL PRIMARY KEY,
    todo_type               VARCHAR(32)  NOT NULL,
    hilist_code             VARCHAR(64),
    project_code            VARCHAR(64),
    product_name            VARCHAR(512),
    effective_date          DATE,
    doc_id                  BIGINT,
    line_id                 BIGINT,
    status                  VARCHAR(32)  NOT NULL,
    close_reason            VARCHAR(512),
    closed_by               BIGINT,
    closed_at               TIMESTAMP,

    created_at              TIMESTAMP    NOT NULL,
    updated_at              TIMESTAMP,
    created_by              BIGINT,
    updated_by              BIGINT,
    is_deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMP,
    deleted_by              BIGINT,
    tenant_id               BIGINT       NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_yb_cat_notice_todo_open
    ON yb_catalog_notice_todo (tenant_id, todo_type, status, id DESC)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE yb_catalog_notice_todo IS '医保文件通知-首页催办待办';
COMMENT ON COLUMN yb_catalog_notice_todo.todo_type IS 'STOP / PAY_CLASS';
COMMENT ON COLUMN yb_catalog_notice_todo.status IS 'OPEN / CLOSED';

CREATE TABLE IF NOT EXISTS yb_catalog_notice_compliance_report (
    id                      BIGINT       NOT NULL PRIMARY KEY,
    contract_id             BIGINT,
    list_type               VARCHAR(30),
    summary_json            TEXT,
    issues_json             TEXT,
    issue_count             INT          NOT NULL DEFAULT 0,
    ran_at                  TIMESTAMP    NOT NULL,
    ran_by                  BIGINT,

    created_at              TIMESTAMP    NOT NULL,
    updated_at              TIMESTAMP,
    created_by              BIGINT,
    updated_by              BIGINT,
    is_deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMP,
    deleted_by              BIGINT,
    tenant_id               BIGINT       NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_yb_cat_notice_compliance_latest
    ON yb_catalog_notice_compliance_report (tenant_id, contract_id, list_type, ran_at DESC)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE yb_catalog_notice_compliance_report IS '医保文件通知-合规校验报告（覆盖最新语义由查询取 ran_at 最大）';
