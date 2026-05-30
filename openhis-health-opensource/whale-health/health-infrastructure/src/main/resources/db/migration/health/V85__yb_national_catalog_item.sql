-- yb_national_catalog_item（建表）
-- Flyway V85

CREATE TABLE IF NOT EXISTS yb_national_catalog_item (
    id                  BIGINT       NOT NULL PRIMARY KEY,
    contract_id         BIGINT       NOT NULL,
    list_type           VARCHAR(30)  NOT NULL,
    hilist_code         VARCHAR(30)  NOT NULL,
    hilist_name         VARCHAR(200),
    chrgitm_lv          VARCHAR(3),
    lmt_used_flag       VARCHAR(3),
    begndate            DATE,
    enddate             DATE,
    vali_flag           VARCHAR(3),
    spec                VARCHAR(200),
    dosform             VARCHAR(200),
    aprvno              VARCHAR(30),
    pinyin              VARCHAR(30),
    raw_json            TEXT,
    source_infno        VARCHAR(16),
    updt_time           TIMESTAMP,

    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP,
    created_by          BIGINT,
    updated_by          BIGINT,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    deleted_by          BIGINT,
    tenant_id           BIGINT       NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_yb_nat_cat_item
    ON yb_national_catalog_item (contract_id, list_type, hilist_code, tenant_id)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_yb_nat_cat_name
    ON yb_national_catalog_item (contract_id, hilist_name)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_yb_nat_cat_pinyin
    ON yb_national_catalog_item (pinyin)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE yb_national_catalog_item IS '国家医保目录镜像（1312/1301）';