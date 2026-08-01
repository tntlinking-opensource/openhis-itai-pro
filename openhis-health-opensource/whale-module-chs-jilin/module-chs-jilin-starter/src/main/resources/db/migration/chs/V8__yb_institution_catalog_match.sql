-- yb_institution_catalog_match（1317 医药机构目录匹配本地镜像）
-- CHS Flyway V8

CREATE TABLE IF NOT EXISTS yb_institution_catalog_match (
    id                  BIGINT       NOT NULL PRIMARY KEY,
    contract_id         BIGINT       NOT NULL,
    fixmedins_code      VARCHAR(30),
    medins_list_codg    VARCHAR(30)  NOT NULL,
    medins_list_name    VARCHAR(200),
    insu_admdvs         VARCHAR(6),
    list_type           VARCHAR(30)  NOT NULL,
    med_list_codg       VARCHAR(30)  NOT NULL,
    begndate            DATE,
    enddate             DATE,
    aprvno              VARCHAR(30),
    dosform             VARCHAR(200),
    exct_cont           VARCHAR(2000),
    item_cont           VARCHAR(2000),
    prcunt              VARCHAR(100),
    spec                VARCHAR(200),
    pacspec             VARCHAR(100),
    memo                VARCHAR(500),
    vali_flag           VARCHAR(16),
    rid                 VARCHAR(40),
    updt_time           TIMESTAMP,
    poolarea_no         VARCHAR(6),

    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP,
    created_by          BIGINT,
    updated_by          BIGINT,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    deleted_by          BIGINT,
    tenant_id           BIGINT       NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_yb_inst_cat_match
    ON yb_institution_catalog_match (
        contract_id, tenant_id, medins_list_codg, med_list_codg, list_type)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_yb_inst_cat_match_name
    ON yb_institution_catalog_match (contract_id, medins_list_name)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_yb_inst_cat_match_list
    ON yb_institution_catalog_match (contract_id, list_type, vali_flag)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE yb_institution_catalog_match IS '医保1317医药机构目录匹配本地镜像';
COMMENT ON COLUMN yb_institution_catalog_match.medins_list_codg IS '定点医药机构目录编号（院内编码）';
COMMENT ON COLUMN yb_institution_catalog_match.med_list_codg IS '医疗目录编码（国家医保码）';
COMMENT ON COLUMN yb_institution_catalog_match.list_type IS '目录类别';
COMMENT ON COLUMN yb_institution_catalog_match.vali_flag IS '有效标志';
