-- yb_catalog_download_cursor（建表）
-- Flyway V83

CREATE TABLE IF NOT EXISTS yb_catalog_download_cursor (
    id                  BIGINT       NOT NULL PRIMARY KEY,
    contract_id         BIGINT       NOT NULL,
    infno               VARCHAR(16)  NOT NULL,
    local_ver           VARCHAR(20)  NOT NULL DEFAULT '0',
    last_file_qury_no   VARCHAR(40),
    last_filename       VARCHAR(200),
    last_success_at     TIMESTAMP,

    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP,
    created_by          BIGINT,
    updated_by          BIGINT,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    deleted_by          BIGINT,
    tenant_id           BIGINT       NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_yb_cat_dl_cursor
    ON yb_catalog_download_cursor (contract_id, infno, tenant_id)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE yb_catalog_download_cursor IS '医保目录下载游标（1301/1305…）';