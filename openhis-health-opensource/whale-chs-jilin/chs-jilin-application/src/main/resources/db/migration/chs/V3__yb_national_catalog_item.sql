-- yb_national_catalog_item（建表）
-- CHS Flyway V3

CREATE TABLE IF NOT EXISTS yb_national_catalog_item (
    id                  BIGINT       NOT NULL PRIMARY KEY,
    contract_id         BIGINT       NOT NULL,
    list_type           VARCHAR(30)  NOT NULL,
    hilist_code         VARCHAR(30)  NOT NULL,
    hilist_name         VARCHAR(200),
    chrgitm_lv          VARCHAR(16),
    lmt_used_flag       VARCHAR(16),
    begndate            DATE,
    enddate             DATE,
    vali_flag           VARCHAR(16),
    spec                VARCHAR(200),
    dosform             VARCHAR(200),
    aprvno              VARCHAR(100),
    pinyin              VARCHAR(100),
    raw_json            TEXT,
    source_infno        VARCHAR(16),
    source_file_qury_no VARCHAR(40),
    source_line_no      INTEGER,
    prodentp_name       VARCHAR(200),
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

CREATE UNIQUE INDEX IF NOT EXISTS uk_yb_nat_cat_item_1312
    ON yb_national_catalog_item (contract_id, list_type, hilist_code, tenant_id)
    WHERE is_deleted = FALSE AND source_line_no IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_yb_nat_cat_item_1301
    ON yb_national_catalog_item (contract_id, list_type, tenant_id, source_file_qury_no, source_line_no)
    WHERE is_deleted = FALSE AND source_line_no IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_yb_nat_cat_name
    ON yb_national_catalog_item (contract_id, hilist_name)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_yb_nat_cat_pinyin
    ON yb_national_catalog_item (pinyin)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS ix_yb_nat_cat_aprvno
    ON yb_national_catalog_item (contract_id, tenant_id, aprvno)
    WHERE is_deleted = FALSE AND aprvno IS NOT NULL AND aprvno <> '';

CREATE INDEX IF NOT EXISTS ix_yb_nat_cat_contract_list_tenant
    ON yb_national_catalog_item (contract_id, list_type, tenant_id)
    WHERE is_deleted = FALSE;


CREATE TABLE IF NOT EXISTS yb_settlement_reversal (
    id                      BIGINT       NOT NULL PRIMARY KEY,
    settlement_record_id    BIGINT       NOT NULL,
    revoke_setl_id            VARCHAR(30),
    mdtrt_id                  VARCHAR(30)  NOT NULL,
    psn_no                    VARCHAR(30)  NOT NULL,
    encounter_class           VARCHAR(8)   NOT NULL,
    medfee_sumamt             NUMERIC(18, 2),
    fund_pay_sumamt           NUMERIC(18, 2),
    acct_pay                  NUMERIC(18, 2),
    psn_cash_pay              NUMERIC(18, 2),
    fund_breakdown_json       TEXT,
    reversed_at               TIMESTAMP    NOT NULL,

    created_at              TIMESTAMP    NOT NULL,
    updated_at              TIMESTAMP,
    created_by              BIGINT,
    updated_by              BIGINT,
    is_deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMP,
    deleted_by              BIGINT,
    tenant_id               BIGINT       NOT NULL
);
COMMENT ON TABLE yb_national_catalog_item IS '医保 national catalog item';
COMMENT ON COLUMN yb_national_catalog_item.id IS '主键';
COMMENT ON COLUMN yb_national_catalog_item.contract_id IS '合同标识';
COMMENT ON COLUMN yb_national_catalog_item.list_type IS 'list类型';
COMMENT ON COLUMN yb_national_catalog_item.hilist_code IS 'hilist编码';
COMMENT ON COLUMN yb_national_catalog_item.hilist_name IS 'hilist名称';
COMMENT ON COLUMN yb_national_catalog_item.chrgitm_lv IS '国家医保目录镜像行（1312 查询 / 1301 解析入库）';
COMMENT ON COLUMN yb_national_catalog_item.lmt_used_flag IS 'lmtused标志位';
COMMENT ON COLUMN yb_national_catalog_item.begndate IS '开始日期';
COMMENT ON COLUMN yb_national_catalog_item.enddate IS '结束日期';
COMMENT ON COLUMN yb_national_catalog_item.vali_flag IS 'vali标志位';
COMMENT ON COLUMN yb_national_catalog_item.spec IS '规格';
COMMENT ON COLUMN yb_national_catalog_item.dosform IS '剂型';
COMMENT ON COLUMN yb_national_catalog_item.aprvno IS '批准文号';
COMMENT ON COLUMN yb_national_catalog_item.pinyin IS '拼音';
COMMENT ON COLUMN yb_national_catalog_item.raw_json IS '原始JSON';
COMMENT ON COLUMN yb_national_catalog_item.source_infno IS '来源infno';
COMMENT ON COLUMN yb_national_catalog_item.source_file_qury_no IS '1301 来源文件查询号（9102/1301）';
COMMENT ON COLUMN yb_national_catalog_item.source_line_no IS '1301 TXT 行号（从 1 起）；NULL 表示 1312 等非文件行';
COMMENT ON COLUMN yb_national_catalog_item.prodentp_name IS '1301 生产企业名称（TXT 第 54 列）';
COMMENT ON COLUMN yb_national_catalog_item.updt_time IS 'updt时间';
COMMENT ON COLUMN yb_national_catalog_item.created_at IS '创建时间';
COMMENT ON COLUMN yb_national_catalog_item.updated_at IS '最后更新时间';
COMMENT ON COLUMN yb_national_catalog_item.created_by IS '创建人';
COMMENT ON COLUMN yb_national_catalog_item.updated_by IS '最后更新人';
COMMENT ON COLUMN yb_national_catalog_item.is_deleted IS '软删标记';
COMMENT ON COLUMN yb_national_catalog_item.deleted_at IS '删除时间';
COMMENT ON COLUMN yb_national_catalog_item.deleted_by IS '删除人';
COMMENT ON COLUMN yb_national_catalog_item.tenant_id IS '租户标识';

COMMENT ON TABLE yb_settlement_reversal IS '医保结算reversal';
COMMENT ON COLUMN yb_settlement_reversal.id IS '主键';
COMMENT ON COLUMN yb_settlement_reversal.settlement_record_id IS '结算记录标识';
COMMENT ON COLUMN yb_settlement_reversal.revoke_setl_id IS 'revoke结算标识';
COMMENT ON COLUMN yb_settlement_reversal.mdtrt_id IS '就诊标识';
COMMENT ON COLUMN yb_settlement_reversal.psn_no IS 'psn编号';
COMMENT ON COLUMN yb_settlement_reversal.encounter_class IS '就诊class';
COMMENT ON COLUMN yb_settlement_reversal.medfee_sumamt IS '医疗费总额';
COMMENT ON COLUMN yb_settlement_reversal.fund_pay_sumamt IS 'fund支付sumamt';
COMMENT ON COLUMN yb_settlement_reversal.acct_pay IS 'acct支付';
COMMENT ON COLUMN yb_settlement_reversal.psn_cash_pay IS 'psncash支付';
COMMENT ON COLUMN yb_settlement_reversal.fund_breakdown_json IS '基金分项JSON';
COMMENT ON COLUMN yb_settlement_reversal.reversed_at IS 'reversed时间';
COMMENT ON COLUMN yb_settlement_reversal.created_at IS '创建时间';
COMMENT ON COLUMN yb_settlement_reversal.updated_at IS '最后更新时间';
COMMENT ON COLUMN yb_settlement_reversal.created_by IS '创建人';
COMMENT ON COLUMN yb_settlement_reversal.updated_by IS '最后更新人';
COMMENT ON COLUMN yb_settlement_reversal.is_deleted IS '软删标记';
COMMENT ON COLUMN yb_settlement_reversal.deleted_at IS '删除时间';
COMMENT ON COLUMN yb_settlement_reversal.deleted_by IS '删除人';
COMMENT ON COLUMN yb_settlement_reversal.tenant_id IS '租户标识';
