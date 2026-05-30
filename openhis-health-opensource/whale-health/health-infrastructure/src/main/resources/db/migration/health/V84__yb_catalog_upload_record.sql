-- yb_catalog_upload_record（建表）
-- Flyway V84

CREATE TABLE IF NOT EXISTS yb_catalog_upload_record (
    id                      BIGINT       NOT NULL PRIMARY KEY,
    contract_id             BIGINT       NOT NULL,
    operation               VARCHAR(8)   NOT NULL,
    product_table           VARCHAR(64)  NOT NULL,
    product_id              BIGINT       NOT NULL,
    list_type               VARCHAR(30)  NOT NULL,
    fixmedins_hilist_id     VARCHAR(30)  NOT NULL,
    med_list_codg           VARCHAR(30)  NOT NULL,
    platform_success        BOOLEAN      NOT NULL,
    msgid                   VARCHAR(64),
    request_snapshot        TEXT,
    response_snapshot       TEXT,
    error_msg               VARCHAR(512),
    operator_id             BIGINT,

    created_at              TIMESTAMP    NOT NULL,
    updated_at              TIMESTAMP,
    created_by              BIGINT,
    updated_by              BIGINT,
    is_deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMP,
    deleted_by              BIGINT,
    tenant_id               BIGINT       NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_yb_cat_upload_product
    ON yb_catalog_upload_record (product_table, product_id)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE yb_catalog_upload_record IS '3301/3302 上传审计';