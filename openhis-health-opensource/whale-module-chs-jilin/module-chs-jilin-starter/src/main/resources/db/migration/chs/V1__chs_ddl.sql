-- Squashed chs DDL (final state through former V12, 2026-08).
-- Folded: former V2 notice tables, V4 invoke_log indexes, V6 pg_trgm + picker indexes,
-- V7 detail audit, V8 institution catalog match, V9 retail/upload/diff columns,
-- V10 retail/upload/diff backfill, V11 national catalog pack fields + backfill,
-- V12 fund_pay_type VARCHAR(16).

-- from V1__yb_catalog_download_cursor.sql
-- yb_catalog_download_cursor（建表）
-- CHS Flyway V1

CREATE TABLE IF NOT EXISTS yb_catalog_download_cursor (
    id                  BIGINT       NOT NULL PRIMARY KEY,
    contract_id         BIGINT       NOT NULL,
    infno               VARCHAR(16)  NOT NULL,
    local_ver           VARCHAR(64)  NOT NULL DEFAULT '0',
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

CREATE INDEX IF NOT EXISTS ix_yb_cat_dl_cursor_lookup
    ON yb_catalog_download_cursor (contract_id, infno, tenant_id, last_success_at DESC NULLS LAST, id DESC)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS ix_yb_cat_dl_cursor_contract_tenant_time
    ON yb_catalog_download_cursor (contract_id, tenant_id, last_success_at DESC NULLS LAST, id DESC)
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
    invoke_log_id BIGINT,

    tenant_id               BIGINT       NOT NULL
);
COMMENT ON TABLE yb_catalog_download_cursor IS '医保目录下载游标历史（每次成功入库追加一行，取最新一条为下次 1301 起点）';
COMMENT ON COLUMN yb_catalog_download_cursor.id IS '主键';
COMMENT ON COLUMN yb_catalog_download_cursor.contract_id IS '合同标识';
COMMENT ON COLUMN yb_catalog_download_cursor.infno IS '接口编号';
COMMENT ON COLUMN yb_catalog_download_cursor.local_ver IS '医保目录下载游标（1301/1305 等文件型接口）';
COMMENT ON COLUMN yb_catalog_download_cursor.last_file_qury_no IS 'lastfilequry编号';
COMMENT ON COLUMN yb_catalog_download_cursor.last_filename IS '最近文件名';
COMMENT ON COLUMN yb_catalog_download_cursor.last_success_at IS 'last成功时间';
COMMENT ON COLUMN yb_catalog_download_cursor.created_at IS '创建时间';
COMMENT ON COLUMN yb_catalog_download_cursor.updated_at IS '最后更新时间';
COMMENT ON COLUMN yb_catalog_download_cursor.created_by IS '创建人';
COMMENT ON COLUMN yb_catalog_download_cursor.updated_by IS '最后更新人';
COMMENT ON COLUMN yb_catalog_download_cursor.is_deleted IS '软删标记';
COMMENT ON COLUMN yb_catalog_download_cursor.deleted_at IS '删除时间';
COMMENT ON COLUMN yb_catalog_download_cursor.deleted_by IS '删除人';
COMMENT ON COLUMN yb_catalog_download_cursor.tenant_id IS '租户标识';

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

-- from V2__yb_catalog_upload_record.sql
-- yb_catalog_upload_record（建表）
-- CHS Flyway V2

CREATE TABLE IF NOT EXISTS yb_catalog_upload_record (
    id                      BIGINT       NOT NULL PRIMARY KEY,
    contract_id             BIGINT       NOT NULL,
    operation               VARCHAR(16)  NOT NULL,
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

COMMENT ON TABLE yb_catalog_upload_record IS '医保 catalog upload record';
COMMENT ON COLUMN yb_catalog_upload_record.id IS '主键';
COMMENT ON COLUMN yb_catalog_upload_record.contract_id IS '合同标识';
COMMENT ON COLUMN yb_catalog_upload_record.operation IS '操作类型';
COMMENT ON COLUMN yb_catalog_upload_record.product_table IS '产品表名';
COMMENT ON COLUMN yb_catalog_upload_record.product_id IS 'product标识';
COMMENT ON COLUMN yb_catalog_upload_record.list_type IS 'list类型';
COMMENT ON COLUMN yb_catalog_upload_record.fixmedins_hilist_id IS 'fixmedinshilist标识';
COMMENT ON COLUMN yb_catalog_upload_record.med_list_codg IS '医疗目录编码';
COMMENT ON COLUMN yb_catalog_upload_record.platform_success IS 'platform成功';
COMMENT ON COLUMN yb_catalog_upload_record.msgid IS '报文标识';
COMMENT ON COLUMN yb_catalog_upload_record.request_snapshot IS '申请快照';
COMMENT ON COLUMN yb_catalog_upload_record.response_snapshot IS 'response快照';
COMMENT ON COLUMN yb_catalog_upload_record.error_msg IS '错误msg';
COMMENT ON COLUMN yb_catalog_upload_record.operator_id IS '操作人标识';
COMMENT ON COLUMN yb_catalog_upload_record.created_at IS '创建时间';
COMMENT ON COLUMN yb_catalog_upload_record.updated_at IS '最后更新时间';
COMMENT ON COLUMN yb_catalog_upload_record.created_by IS '创建人';
COMMENT ON COLUMN yb_catalog_upload_record.updated_by IS '最后更新人';
COMMENT ON COLUMN yb_catalog_upload_record.is_deleted IS '软删标记';
COMMENT ON COLUMN yb_catalog_upload_record.deleted_at IS '删除时间';
COMMENT ON COLUMN yb_catalog_upload_record.deleted_by IS '删除人';
COMMENT ON COLUMN yb_catalog_upload_record.tenant_id IS '租户标识';

-- from V3__yb_national_catalog_item.sql
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
    min_pac_cnt         VARCHAR(20),
    min_pacunt          VARCHAR(30),
    min_prepunt         VARCHAR(30),
    min_prcunt          VARCHAR(50),

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
COMMENT ON COLUMN yb_national_catalog_item.min_pac_cnt IS '1301 最小包装数量（规范第42列 / raw_json 下标41）';
COMMENT ON COLUMN yb_national_catalog_item.min_pacunt IS '1301 最小包装单位（规范第43列 / raw_json 下标42）';
COMMENT ON COLUMN yb_national_catalog_item.min_prepunt IS '1301 最小制剂单位（规范第44列 / raw_json 下标43）';
COMMENT ON COLUMN yb_national_catalog_item.min_prcunt IS '1301 最小计价单位（规范第49列 / raw_json 下标48）';
COMMENT ON COLUMN yb_national_catalog_item.created_at IS '创建时间';
COMMENT ON COLUMN yb_national_catalog_item.updated_at IS '最后更新时间';
COMMENT ON COLUMN yb_national_catalog_item.created_by IS '创建人';
COMMENT ON COLUMN yb_national_catalog_item.updated_by IS '最后更新人';
COMMENT ON COLUMN yb_national_catalog_item.is_deleted IS '软删标记';
COMMENT ON COLUMN yb_national_catalog_item.deleted_at IS '删除时间';
COMMENT ON COLUMN yb_national_catalog_item.deleted_by IS '删除人';
COMMENT ON COLUMN yb_national_catalog_item.tenant_id IS '租户标识';

-- from V4__yb_pub_sign.sql
-- 医保 9001 签到流水持久化（当日有效，避免重复签到）
-- CHS CHS Flyway V4

CREATE TABLE IF NOT EXISTS yb_pub_sign (
    id               BIGINT       NOT NULL PRIMARY KEY,
    contract_bus_no  VARCHAR(64)  NOT NULL,
    adm_vs           VARCHAR(16)  NOT NULL,
    fixmedins_code   VARCHAR(32)  NOT NULL,
    opter_no         VARCHAR(32)  NOT NULL,
    sign_no          VARCHAR(64)  NOT NULL,
    sign_time        TIMESTAMP    NOT NULL,
    sign_date        DATE         NOT NULL,
    yb_username      VARCHAR(64),
    status           VARCHAR(8)   NOT NULL DEFAULT '1',
    sign_out_time    TIMESTAMP,

    created_at       TIMESTAMP    NOT NULL,
    updated_at       TIMESTAMP,
    created_by       BIGINT,
    updated_by       BIGINT,
    is_deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at       TIMESTAMP,
    deleted_by       BIGINT,
    tenant_id        BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_yb_pub_sign_lookup
    ON yb_pub_sign (contract_bus_no, opter_no, sign_date, tenant_id)
    WHERE is_deleted = FALSE AND status = '1';

COMMENT ON TABLE yb_pub_sign IS '医保 pub sign';
COMMENT ON COLUMN yb_pub_sign.id IS '主键';
COMMENT ON COLUMN yb_pub_sign.contract_bus_no IS '合同bus编号';
COMMENT ON COLUMN yb_pub_sign.adm_vs IS '行政区划版本';
COMMENT ON COLUMN yb_pub_sign.fixmedins_code IS 'fixmedins编码';
COMMENT ON COLUMN yb_pub_sign.opter_no IS 'opter编号';
COMMENT ON COLUMN yb_pub_sign.sign_no IS '体征编号';
COMMENT ON COLUMN yb_pub_sign.sign_time IS '体征时间';
COMMENT ON COLUMN yb_pub_sign.sign_date IS '体征日期';
COMMENT ON COLUMN yb_pub_sign.yb_username IS '医保username';
COMMENT ON COLUMN yb_pub_sign.status IS '医保 9001 签到流水（对齐 yb_pub_sign）';
COMMENT ON COLUMN yb_pub_sign.sign_out_time IS '体征out时间';
COMMENT ON COLUMN yb_pub_sign.created_at IS '创建时间';
COMMENT ON COLUMN yb_pub_sign.updated_at IS '最后更新时间';
COMMENT ON COLUMN yb_pub_sign.created_by IS '创建人';
COMMENT ON COLUMN yb_pub_sign.updated_by IS '最后更新人';
COMMENT ON COLUMN yb_pub_sign.is_deleted IS '软删标记';
COMMENT ON COLUMN yb_pub_sign.deleted_at IS '删除时间';
COMMENT ON COLUMN yb_pub_sign.deleted_by IS '删除人';
COMMENT ON COLUMN yb_pub_sign.tenant_id IS '租户标识';

-- from V5__yb_outpatient_settle.sql
-- 门诊医保结算 Wave 1：人员缓存、就诊映射、预结算快照、结算记录

CREATE TABLE IF NOT EXISTS yb_person_cache (
    id                  BIGINT       NOT NULL PRIMARY KEY,
    contract_id         BIGINT       NOT NULL,
    mdtrt_cert_type     VARCHAR(3)   NOT NULL,
    mdtrt_cert_no       VARCHAR(2048) NOT NULL,
    psn_no              VARCHAR(30)  NOT NULL,
    insuplc_admdvs      VARCHAR(6),
    insutype            VARCHAR(6),
    balc                NUMERIC(18, 2),
    snapshot_json       TEXT         NOT NULL,
    cached_at           TIMESTAMP    NOT NULL,
    expire_at           TIMESTAMP    NOT NULL,

    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP,
    created_by          BIGINT,
    updated_by          BIGINT,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    deleted_by          BIGINT,
    tenant_id           BIGINT       NOT NULL
);

-- md5(mdtrt_cert_no)：社保卡 BusiCardInfo 超长时避免 btree 索引页限制
CREATE UNIQUE INDEX IF NOT EXISTS uk_yb_person_cache_cert
    ON yb_person_cache (contract_id, mdtrt_cert_type, md5(mdtrt_cert_no), tenant_id)
    WHERE is_deleted = FALSE;

CREATE TABLE IF NOT EXISTS yb_encounter_platform_map (
    id                  BIGINT       NOT NULL PRIMARY KEY,
    encounter_id        BIGINT       NOT NULL,
    contract_id         BIGINT       NOT NULL,
    mdtrt_id            VARCHAR(30)  NOT NULL,
    psn_no              VARCHAR(30),
    ipt_otp_no          VARCHAR(30),

    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP,
    created_by          BIGINT,
    updated_by          BIGINT,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    deleted_by          BIGINT,
    tenant_id           BIGINT       NOT NULL,
    med_type VARCHAR(6) NOT NULL DEFAULT '11'
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_yb_encounter_platform_map
    ON yb_encounter_platform_map (encounter_id, contract_id, med_type, tenant_id)
    WHERE is_deleted = FALSE;

CREATE TABLE IF NOT EXISTS yb_clinic_pre_settle (
    id                      BIGINT       NOT NULL PRIMARY KEY,
    contract_id             BIGINT       NOT NULL,
    encounter_id            BIGINT       NOT NULL,
    payment_draft_id        BIGINT,
    chrg_bchno              VARCHAR(30)  NOT NULL,
    contract_bus_no         VARCHAR(64)  NOT NULL,
    mdtrt_id                VARCHAR(30)  NOT NULL,
    psn_no                  VARCHAR(30)  NOT NULL,
    insuplc_admdvs          VARCHAR(6),
    medfee_sumamt           NUMERIC(18, 2),
    fund_pay_sumamt         NUMERIC(18, 2),
    acct_pay                NUMERIC(18, 2),
    psn_cash_pay            NUMERIC(18, 2),
    charge_item_ids         TEXT,
    status                  VARCHAR(16)  NOT NULL,
    med_type                VARCHAR(6),
    dise_codg               VARCHAR(30),

    created_at              TIMESTAMP    NOT NULL,
    updated_at              TIMESTAMP,
    created_by              BIGINT,
    updated_by              BIGINT,
    is_deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMP,
    deleted_by              BIGINT,
    mdtrt_cert_type VARCHAR(3),
    mdtrt_cert_no VARCHAR(2048),
    insutype VARCHAR(6),
    acct_used_flag VARCHAR(1),
    psn_setlway VARCHAR(6),
    fulamt_ownpay_amt NUMERIC(18, 2),
    overlmt_selfpay NUMERIC(18, 2),
    preselfpay_amt NUMERIC(18, 2),
    inscp_scp_amt NUMERIC(18, 2),
    retail_medfee_sumamt NUMERIC(16, 2),
    upload_medfee_sumamt NUMERIC(16, 2),
    retail_upload_diff_amt NUMERIC(16, 2),

    tenant_id               BIGINT       NOT NULL,
    account_id BIGINT
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_yb_clinic_pre_settle_bchno
    ON yb_clinic_pre_settle (chrg_bchno, tenant_id)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_yb_clinic_pre_settle_draft
    ON yb_clinic_pre_settle (payment_draft_id)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_yb_clinic_pre_settle_draft_status
    ON yb_clinic_pre_settle (payment_draft_id, status)
    WHERE is_deleted = FALSE;

CREATE TABLE IF NOT EXISTS yb_settlement_record (
    id                      BIGINT       NOT NULL PRIMARY KEY,
    contract_id             BIGINT       NOT NULL,
    encounter_id            BIGINT       NOT NULL,
    payment_id              BIGINT,
    pre_settle_id           BIGINT,
    chrg_bchno              VARCHAR(30)  NOT NULL,
    setl_id                 VARCHAR(30)  NOT NULL,
    mdtrt_id                VARCHAR(30)  NOT NULL,
    psn_no                  VARCHAR(30)  NOT NULL,
    medfee_sumamt           NUMERIC(18, 2),
    fund_pay_sumamt         NUMERIC(18, 2),
    acct_pay                NUMERIC(18, 2),
    psn_cash_pay            NUMERIC(18, 2),

    created_at              TIMESTAMP    NOT NULL,
    updated_at              TIMESTAMP,
    created_by              BIGINT,
    updated_by          BIGINT,
    is_deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMP,
    deleted_by              BIGINT,
    settle_mode VARCHAR(20),
    psn_name VARCHAR(50),
    insutype VARCHAR(6),
    med_type VARCHAR(6),
    setl_time TIMESTAMP,
    fulamt_ownpay_amt NUMERIC(18, 2),
    overlmt_selfpay NUMERIC(18, 2),
    preselfpay_amt NUMERIC(18, 2),
    inscp_scp_amt NUMERIC(18, 2),
    hifp_pay NUMERIC(18, 2),
    cvlserv_pay NUMERIC(18, 2),
    hifmi_pay NUMERIC(18, 2),
    hifob_pay NUMERIC(18, 2),
    hifes_pay NUMERIC(18, 2),
    cvlserv_flag VARCHAR(3),
    pool_prop_selfpay NUMERIC(18, 2),
    maf_pay NUMERIC(18, 2),
    oth_pay NUMERIC(18, 2),
    psn_part_amt NUMERIC(18, 2),
    hosp_part_amt NUMERIC(18, 2),
    balc NUMERIC(18, 2),
    acct_mulaid_pay NUMERIC(18, 2),
    psn_type VARCHAR(6),
    act_pay_dedc NUMERIC(18, 2),
    hifdm_pay NUMERIC(18, 2),
    exp_content TEXT,
    medins_setl_id VARCHAR(30),
    msgid_2207 VARCHAR(64),
    clr_optins VARCHAR(6),
    clr_way VARCHAR(6),
    clr_type VARCHAR(6),
    fund_breakdown_json TEXT,

    tenant_id               BIGINT       NOT NULL,
    account_id BIGINT,
    encounter_class VARCHAR(8) NOT NULL DEFAULT 'AMB',
    status VARCHAR(16) NOT NULL DEFAULT 'CONFIRMED',
    retail_medfee_sumamt NUMERIC(16, 2),
    upload_medfee_sumamt NUMERIC(16, 2),
    retail_upload_diff_amt NUMERIC(16, 2)
);

CREATE INDEX IF NOT EXISTS idx_yb_settlement_record_payment
    ON yb_settlement_record (payment_id)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE yb_person_cache IS '医保 person cache';
COMMENT ON COLUMN yb_person_cache.id IS '主键';
COMMENT ON COLUMN yb_person_cache.contract_id IS '合同标识';
COMMENT ON COLUMN yb_person_cache.mdtrt_cert_type IS '就诊cert类型';
COMMENT ON COLUMN yb_person_cache.mdtrt_cert_no IS '就诊cert编号';
COMMENT ON COLUMN yb_person_cache.psn_no IS 'psn编号';
COMMENT ON COLUMN yb_person_cache.insuplc_admdvs IS '参保地医保区划';
COMMENT ON COLUMN yb_person_cache.insutype IS '险种类型';
COMMENT ON COLUMN yb_person_cache.balc IS '账户余额';
COMMENT ON COLUMN yb_person_cache.snapshot_json IS '快照JSON';
COMMENT ON COLUMN yb_person_cache.cached_at IS 'cached时间';
COMMENT ON COLUMN yb_person_cache.expire_at IS 'expire时间';
COMMENT ON COLUMN yb_person_cache.created_at IS '创建时间';
COMMENT ON COLUMN yb_person_cache.updated_at IS '最后更新时间';
COMMENT ON COLUMN yb_person_cache.created_by IS '创建人';
COMMENT ON COLUMN yb_person_cache.updated_by IS '最后更新人';
COMMENT ON COLUMN yb_person_cache.is_deleted IS '软删标记';
COMMENT ON COLUMN yb_person_cache.deleted_at IS '删除时间';
COMMENT ON COLUMN yb_person_cache.deleted_by IS '删除人';
COMMENT ON COLUMN yb_person_cache.tenant_id IS '租户标识';

COMMENT ON TABLE yb_encounter_platform_map IS '医保 encounter platform map';
COMMENT ON COLUMN yb_encounter_platform_map.id IS '主键';
COMMENT ON COLUMN yb_encounter_platform_map.encounter_id IS '就诊标识';
COMMENT ON COLUMN yb_encounter_platform_map.contract_id IS '合同标识';
COMMENT ON COLUMN yb_encounter_platform_map.mdtrt_id IS '就诊标识';
COMMENT ON COLUMN yb_encounter_platform_map.psn_no IS 'psn编号';
COMMENT ON COLUMN yb_encounter_platform_map.ipt_otp_no IS 'iptotp编号';
COMMENT ON COLUMN yb_encounter_platform_map.created_at IS '创建时间';
COMMENT ON COLUMN yb_encounter_platform_map.updated_at IS '最后更新时间';
COMMENT ON COLUMN yb_encounter_platform_map.created_by IS '创建人';
COMMENT ON COLUMN yb_encounter_platform_map.updated_by IS '最后更新人';
COMMENT ON COLUMN yb_encounter_platform_map.is_deleted IS '软删标记';
COMMENT ON COLUMN yb_encounter_platform_map.deleted_at IS '删除时间';
COMMENT ON COLUMN yb_encounter_platform_map.deleted_by IS '删除人';
COMMENT ON COLUMN yb_encounter_platform_map.tenant_id IS '租户标识';
COMMENT ON COLUMN yb_encounter_platform_map.med_type IS 'med类型';

COMMENT ON TABLE yb_clinic_pre_settle IS '2206 预结算快照（按 chrg_bchno）';
COMMENT ON COLUMN yb_clinic_pre_settle.id IS '主键';
COMMENT ON COLUMN yb_clinic_pre_settle.contract_id IS '合同标识';
COMMENT ON COLUMN yb_clinic_pre_settle.encounter_id IS '就诊标识';
COMMENT ON COLUMN yb_clinic_pre_settle.payment_draft_id IS '支付draft标识';
COMMENT ON COLUMN yb_clinic_pre_settle.chrg_bchno IS '收费批次号';
COMMENT ON COLUMN yb_clinic_pre_settle.contract_bus_no IS '合同bus编号';
COMMENT ON COLUMN yb_clinic_pre_settle.mdtrt_id IS '就诊标识';
COMMENT ON COLUMN yb_clinic_pre_settle.psn_no IS 'psn编号';
COMMENT ON COLUMN yb_clinic_pre_settle.insuplc_admdvs IS '参保地医保区划';
COMMENT ON COLUMN yb_clinic_pre_settle.medfee_sumamt IS '医疗费总额';
COMMENT ON COLUMN yb_clinic_pre_settle.fund_pay_sumamt IS 'fund支付sumamt';
COMMENT ON COLUMN yb_clinic_pre_settle.acct_pay IS 'acct支付';
COMMENT ON COLUMN yb_clinic_pre_settle.psn_cash_pay IS 'psncash支付';
COMMENT ON COLUMN yb_clinic_pre_settle.charge_item_ids IS '收费项ids';
COMMENT ON COLUMN yb_clinic_pre_settle.status IS '状态';
COMMENT ON COLUMN yb_clinic_pre_settle.med_type IS '医疗类别';
COMMENT ON COLUMN yb_clinic_pre_settle.dise_codg IS '慢病诊断码';
COMMENT ON COLUMN yb_clinic_pre_settle.created_at IS '创建时间';
COMMENT ON COLUMN yb_clinic_pre_settle.updated_at IS '最后更新时间';
COMMENT ON COLUMN yb_clinic_pre_settle.created_by IS '创建人';
COMMENT ON COLUMN yb_clinic_pre_settle.updated_by IS '最后更新人';
COMMENT ON COLUMN yb_clinic_pre_settle.is_deleted IS '软删标记';
COMMENT ON COLUMN yb_clinic_pre_settle.deleted_at IS '删除时间';
COMMENT ON COLUMN yb_clinic_pre_settle.deleted_by IS '删除人';
COMMENT ON COLUMN yb_clinic_pre_settle.tenant_id IS '租户标识';
COMMENT ON COLUMN yb_clinic_pre_settle.account_id IS '账户标识';
COMMENT ON COLUMN yb_clinic_pre_settle.retail_medfee_sumamt IS '院内收费合计（charge_item.total_price）';
COMMENT ON COLUMN yb_clinic_pre_settle.upload_medfee_sumamt IS '2204 上传明细合计（pric×cnt）';
COMMENT ON COLUMN yb_clinic_pre_settle.retail_upload_diff_amt IS '零售−上传分差（retail−upload）';

COMMENT ON TABLE yb_settlement_record IS '医保 settlement record';
COMMENT ON COLUMN yb_settlement_record.id IS '主键';
COMMENT ON COLUMN yb_settlement_record.contract_id IS '合同标识';
COMMENT ON COLUMN yb_settlement_record.encounter_id IS '就诊标识';
COMMENT ON COLUMN yb_settlement_record.payment_id IS '支付标识';
COMMENT ON COLUMN yb_settlement_record.pre_settle_id IS 'presettle标识';
COMMENT ON COLUMN yb_settlement_record.chrg_bchno IS '收费批次号';
COMMENT ON COLUMN yb_settlement_record.setl_id IS '结算标识';
COMMENT ON COLUMN yb_settlement_record.mdtrt_id IS '就诊标识';
COMMENT ON COLUMN yb_settlement_record.psn_no IS 'psn编号';
COMMENT ON COLUMN yb_settlement_record.medfee_sumamt IS '医疗费总额';
COMMENT ON COLUMN yb_settlement_record.fund_pay_sumamt IS 'fund支付sumamt';
COMMENT ON COLUMN yb_settlement_record.acct_pay IS 'acct支付';
COMMENT ON COLUMN yb_settlement_record.psn_cash_pay IS 'psncash支付';
COMMENT ON COLUMN yb_settlement_record.created_at IS '创建时间';
COMMENT ON COLUMN yb_settlement_record.updated_at IS '最后更新时间';
COMMENT ON COLUMN yb_settlement_record.created_by IS '创建人';
COMMENT ON COLUMN yb_settlement_record.updated_by IS '最后更新人';
COMMENT ON COLUMN yb_settlement_record.is_deleted IS '软删标记';
COMMENT ON COLUMN yb_settlement_record.deleted_at IS '删除时间';
COMMENT ON COLUMN yb_settlement_record.deleted_by IS '删除人';
COMMENT ON COLUMN yb_settlement_record.tenant_id IS '租户标识';
COMMENT ON COLUMN yb_settlement_record.account_id IS '账户标识';
COMMENT ON COLUMN yb_settlement_record.encounter_class IS '就诊class';
COMMENT ON COLUMN yb_settlement_record.status IS '状态';
COMMENT ON COLUMN yb_settlement_record.retail_medfee_sumamt IS '院内收费合计（charge_item.total_price）';
COMMENT ON COLUMN yb_settlement_record.upload_medfee_sumamt IS '费用上传明细合计（pric×cnt）';
COMMENT ON COLUMN yb_settlement_record.retail_upload_diff_amt IS '零售−上传分差（retail−upload）；日结勾稽';

-- from V6__yb_inpatient_settle.sql
-- 住院医保结算 Wave 1：预结算快照、费用上传跟踪

CREATE TABLE IF NOT EXISTS yb_inpatient_fee_upload (
    id                      BIGINT       NOT NULL PRIMARY KEY,
    encounter_id            BIGINT       NOT NULL,
    charge_item_id          BIGINT       NOT NULL,
    feedetl_sn              VARCHAR(30)  NOT NULL,
    mdtrt_id                VARCHAR(30)  NOT NULL,
    upload_status           VARCHAR(16)  NOT NULL,
    platform_result_json    TEXT,
    uploaded_at             TIMESTAMP,

    created_at              TIMESTAMP    NOT NULL,
    updated_at              TIMESTAMP,
    created_by              BIGINT,
    updated_by              BIGINT,
    is_deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMP,
    deleted_by              BIGINT,
    chrgitm_lv VARCHAR(3),
    med_chrgitm_type VARCHAR(6),
    selfpay_prop NUMERIC(5, 4),
    fulamt_ownpay_amt NUMERIC(18, 2),
    overlmt_amt NUMERIC(18, 2),
    preselfpay_amt NUMERIC(18, 2),
    inscp_scp_amt NUMERIC(18, 2),
    lmt_used_flag VARCHAR(3),
    platform_memo VARCHAR(500),

    tenant_id               BIGINT       NOT NULL,
    det_item_fee_sumamt     NUMERIC(18, 4)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_yb_ip_fee_upload_charge_item
    ON yb_inpatient_fee_upload (charge_item_id, tenant_id)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_yb_ip_fee_upload_encounter
    ON yb_inpatient_fee_upload (encounter_id, upload_status)
    WHERE is_deleted = FALSE;

CREATE TABLE IF NOT EXISTS yb_inpatient_pre_settle (
    id                      BIGINT       NOT NULL PRIMARY KEY,
    payment_draft_id        BIGINT       NOT NULL,
    encounter_id            BIGINT       NOT NULL,
    contract_id             BIGINT       NOT NULL,
    contract_bus_no         VARCHAR(64)  NOT NULL,
    mdtrt_id                VARCHAR(30)  NOT NULL,
    psn_no                  VARCHAR(30)  NOT NULL,
    med_type                VARCHAR(6),
    mid_setl_flag           VARCHAR(3),
    settle_mode             VARCHAR(20),
    medfee_sumamt           NUMERIC(18, 2),
    fund_pay_sumamt         NUMERIC(18, 2),
    acct_pay                NUMERIC(18, 2),
    psn_cash_pay            NUMERIC(18, 2),
    charge_item_ids         TEXT,
    setl_id                 VARCHAR(30),
    status                  VARCHAR(16)  NOT NULL,

    created_at              TIMESTAMP    NOT NULL,
    updated_at              TIMESTAMP,
    created_by              BIGINT,
    updated_by              BIGINT,
    is_deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMP,
    deleted_by              BIGINT,
    insuplc_admdvs VARCHAR(6),
    mdtrt_cert_type VARCHAR(3),
    mdtrt_cert_no VARCHAR(2048),
    insutype VARCHAR(6),
    acct_used_flag VARCHAR(1),
    psn_setlway VARCHAR(6),
    fulamt_ownpay_amt NUMERIC(18, 2),
    overlmt_selfpay NUMERIC(18, 2),
    preselfpay_amt NUMERIC(18, 2),
    inscp_scp_amt NUMERIC(18, 2),
    retail_medfee_sumamt NUMERIC(16, 2),
    upload_medfee_sumamt NUMERIC(16, 2),
    retail_upload_diff_amt NUMERIC(16, 2),

    tenant_id               BIGINT       NOT NULL,
    account_id BIGINT
);

CREATE INDEX IF NOT EXISTS idx_yb_ip_pre_settle_draft_status
    ON yb_inpatient_pre_settle (payment_draft_id, status)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE yb_inpatient_fee_upload IS '医保 inpatient fee upload';
COMMENT ON COLUMN yb_inpatient_fee_upload.id IS '主键';
COMMENT ON COLUMN yb_inpatient_fee_upload.encounter_id IS '就诊标识';
COMMENT ON COLUMN yb_inpatient_fee_upload.charge_item_id IS '收费项标识';
COMMENT ON COLUMN yb_inpatient_fee_upload.feedetl_sn IS '费用明细流水号';
COMMENT ON COLUMN yb_inpatient_fee_upload.mdtrt_id IS '就诊标识';
COMMENT ON COLUMN yb_inpatient_fee_upload.upload_status IS '上传状态';
COMMENT ON COLUMN yb_inpatient_fee_upload.platform_result_json IS 'platform结果JSON';
COMMENT ON COLUMN yb_inpatient_fee_upload.uploaded_at IS 'uploaded时间';
COMMENT ON COLUMN yb_inpatient_fee_upload.created_at IS '创建时间';
COMMENT ON COLUMN yb_inpatient_fee_upload.updated_at IS '最后更新时间';
COMMENT ON COLUMN yb_inpatient_fee_upload.created_by IS '创建人';
COMMENT ON COLUMN yb_inpatient_fee_upload.updated_by IS '最后更新人';
COMMENT ON COLUMN yb_inpatient_fee_upload.is_deleted IS '软删标记';
COMMENT ON COLUMN yb_inpatient_fee_upload.deleted_at IS '删除时间';
COMMENT ON COLUMN yb_inpatient_fee_upload.deleted_by IS '删除人';
COMMENT ON COLUMN yb_inpatient_fee_upload.tenant_id IS '租户标识';
COMMENT ON COLUMN yb_inpatient_fee_upload.det_item_fee_sumamt IS 'det项feesumamt';

COMMENT ON TABLE yb_inpatient_pre_settle IS '医保 inpatient pre settle';
COMMENT ON COLUMN yb_inpatient_pre_settle.id IS '主键';
COMMENT ON COLUMN yb_inpatient_pre_settle.payment_draft_id IS '支付draft标识';
COMMENT ON COLUMN yb_inpatient_pre_settle.encounter_id IS '就诊标识';
COMMENT ON COLUMN yb_inpatient_pre_settle.contract_id IS '合同标识';
COMMENT ON COLUMN yb_inpatient_pre_settle.contract_bus_no IS '合同bus编号';
COMMENT ON COLUMN yb_inpatient_pre_settle.mdtrt_id IS '就诊标识';
COMMENT ON COLUMN yb_inpatient_pre_settle.psn_no IS 'psn编号';
COMMENT ON COLUMN yb_inpatient_pre_settle.med_type IS 'med类型';
COMMENT ON COLUMN yb_inpatient_pre_settle.mid_setl_flag IS 'mid结算标志位';
COMMENT ON COLUMN yb_inpatient_pre_settle.settle_mode IS '结算模式';
COMMENT ON COLUMN yb_inpatient_pre_settle.medfee_sumamt IS '医疗费总额';
COMMENT ON COLUMN yb_inpatient_pre_settle.fund_pay_sumamt IS 'fund支付sumamt';
COMMENT ON COLUMN yb_inpatient_pre_settle.acct_pay IS 'acct支付';
COMMENT ON COLUMN yb_inpatient_pre_settle.psn_cash_pay IS 'psncash支付';
COMMENT ON COLUMN yb_inpatient_pre_settle.charge_item_ids IS '收费项ids';
COMMENT ON COLUMN yb_inpatient_pre_settle.setl_id IS '结算标识';
COMMENT ON COLUMN yb_inpatient_pre_settle.status IS '状态';
COMMENT ON COLUMN yb_inpatient_pre_settle.created_at IS '创建时间';
COMMENT ON COLUMN yb_inpatient_pre_settle.updated_at IS '最后更新时间';
COMMENT ON COLUMN yb_inpatient_pre_settle.created_by IS '创建人';
COMMENT ON COLUMN yb_inpatient_pre_settle.updated_by IS '最后更新人';
COMMENT ON COLUMN yb_inpatient_pre_settle.is_deleted IS '软删标记';
COMMENT ON COLUMN yb_inpatient_pre_settle.deleted_at IS '删除时间';
COMMENT ON COLUMN yb_inpatient_pre_settle.deleted_by IS '删除人';
COMMENT ON COLUMN yb_inpatient_pre_settle.tenant_id IS '租户标识';
COMMENT ON COLUMN yb_inpatient_pre_settle.account_id IS '账户标识';
COMMENT ON COLUMN yb_inpatient_pre_settle.retail_medfee_sumamt IS '院内收费合计（charge_item.total_price）';
COMMENT ON COLUMN yb_inpatient_pre_settle.upload_medfee_sumamt IS '2301 上传明细合计（pric×cnt）';
COMMENT ON COLUMN yb_inpatient_pre_settle.retail_upload_diff_amt IS '零售−上传分差（retail−upload）';

-- from V7__yb_reconciliation_clear.sql
-- Wave 3：吉林对账清算本地记录（3201 / 3203A）

CREATE TABLE IF NOT EXISTS yb_clear_apply (
    id                BIGINT         NOT NULL PRIMARY KEY,
    contract_id       BIGINT         NOT NULL,
    contract_bus_no   VARCHAR(64)    NOT NULL,
    clr_type          VARCHAR(30),
    clr_optins        VARCHAR(6),
    begndate          DATE,
    enddate           DATE,
    medfee_sumamt     NUMERIC(18, 2),
    med_sumfee        NUMERIC(18, 2),
    fund_appy_sum     NUMERIC(18, 2),
    cash_payamt       NUMERIC(18, 2),
    acct_pay          NUMERIC(18, 2),
    clr_appy_evt_id   VARCHAR(30),
    clr_stas          VARCHAR(6),
    status            VARCHAR(16)    NOT NULL,

    created_at        TIMESTAMP      NOT NULL,
    updated_at        TIMESTAMP,
    created_by        BIGINT,
    updated_by        BIGINT,
    is_deleted        BOOLEAN        NOT NULL DEFAULT FALSE,
    deleted_at        TIMESTAMP,
    deleted_by        BIGINT,
    tenant_id         BIGINT         NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_yb_clear_apply_contract
    ON yb_clear_apply (contract_bus_no, tenant_id)
    WHERE is_deleted = FALSE;

CREATE TABLE IF NOT EXISTS yb_recon_daily (
    id                  BIGINT         NOT NULL PRIMARY KEY,
    contract_id         BIGINT         NOT NULL,
    contract_bus_no     VARCHAR(64)    NOT NULL,
    insutype            VARCHAR(6),
    clr_type            VARCHAR(6),
    setl_optins         VARCHAR(6),
    stmt_begndate       DATE,
    stmt_enddate        DATE,
    medfee_sumamt       NUMERIC(18, 2),
    fund_pay_sumamt     NUMERIC(18, 2),
    acct_pay            NUMERIC(18, 2),
    fixmedins_setl_cnt  INTEGER,
    stmt_rslt           VARCHAR(6),
    stmt_rslt_dscr      VARCHAR(200),

    created_at          TIMESTAMP      NOT NULL,
    updated_at          TIMESTAMP,
    created_by          BIGINT,
    updated_by          BIGINT,
    is_deleted          BOOLEAN        NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    deleted_by          BIGINT,
    tenant_id           BIGINT         NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_yb_recon_daily_contract
    ON yb_recon_daily (contract_bus_no, tenant_id)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE yb_clear_apply IS '医保 clear apply';
COMMENT ON COLUMN yb_clear_apply.id IS '主键';
COMMENT ON COLUMN yb_clear_apply.contract_id IS '合同标识';
COMMENT ON COLUMN yb_clear_apply.contract_bus_no IS '合同bus编号';
COMMENT ON COLUMN yb_clear_apply.clr_type IS 'clr类型';
COMMENT ON COLUMN yb_clear_apply.clr_optins IS '清算经办机构';
COMMENT ON COLUMN yb_clear_apply.begndate IS '开始日期';
COMMENT ON COLUMN yb_clear_apply.enddate IS '结束日期';
COMMENT ON COLUMN yb_clear_apply.medfee_sumamt IS '医疗费总额';
COMMENT ON COLUMN yb_clear_apply.med_sumfee IS '医保费用总额';
COMMENT ON COLUMN yb_clear_apply.fund_appy_sum IS '基金申请总额';
COMMENT ON COLUMN yb_clear_apply.cash_payamt IS '现金支付金额';
COMMENT ON COLUMN yb_clear_apply.acct_pay IS 'acct支付';
COMMENT ON COLUMN yb_clear_apply.clr_appy_evt_id IS 'clrappyevt标识';
COMMENT ON COLUMN yb_clear_apply.clr_stas IS 'clr状态';
COMMENT ON COLUMN yb_clear_apply.status IS '状态';
COMMENT ON COLUMN yb_clear_apply.created_at IS '创建时间';
COMMENT ON COLUMN yb_clear_apply.updated_at IS '最后更新时间';
COMMENT ON COLUMN yb_clear_apply.created_by IS '创建人';
COMMENT ON COLUMN yb_clear_apply.updated_by IS '最后更新人';
COMMENT ON COLUMN yb_clear_apply.is_deleted IS '软删标记';
COMMENT ON COLUMN yb_clear_apply.deleted_at IS '删除时间';
COMMENT ON COLUMN yb_clear_apply.deleted_by IS '删除人';
COMMENT ON COLUMN yb_clear_apply.tenant_id IS '租户标识';

COMMENT ON TABLE yb_recon_daily IS '医保 recon daily';
COMMENT ON COLUMN yb_recon_daily.id IS '主键';
COMMENT ON COLUMN yb_recon_daily.contract_id IS '合同标识';
COMMENT ON COLUMN yb_recon_daily.contract_bus_no IS '合同bus编号';
COMMENT ON COLUMN yb_recon_daily.insutype IS '险种类型';
COMMENT ON COLUMN yb_recon_daily.clr_type IS 'clr类型';
COMMENT ON COLUMN yb_recon_daily.setl_optins IS '结算optins';
COMMENT ON COLUMN yb_recon_daily.stmt_begndate IS 'stmt开始日期';
COMMENT ON COLUMN yb_recon_daily.stmt_enddate IS 'stmt结束日期';
COMMENT ON COLUMN yb_recon_daily.medfee_sumamt IS '医疗费总额';
COMMENT ON COLUMN yb_recon_daily.fund_pay_sumamt IS 'fund支付sumamt';
COMMENT ON COLUMN yb_recon_daily.acct_pay IS 'acct支付';
COMMENT ON COLUMN yb_recon_daily.fixmedins_setl_cnt IS 'fixmedins结算数量';
COMMENT ON COLUMN yb_recon_daily.stmt_rslt IS 'stmt结果';
COMMENT ON COLUMN yb_recon_daily.stmt_rslt_dscr IS 'stmt结果dscr';
COMMENT ON COLUMN yb_recon_daily.created_at IS '创建时间';
COMMENT ON COLUMN yb_recon_daily.updated_at IS '最后更新时间';
COMMENT ON COLUMN yb_recon_daily.created_by IS '创建人';
COMMENT ON COLUMN yb_recon_daily.updated_by IS '最后更新人';
COMMENT ON COLUMN yb_recon_daily.is_deleted IS '软删标记';
COMMENT ON COLUMN yb_recon_daily.deleted_at IS '删除时间';
COMMENT ON COLUMN yb_recon_daily.deleted_by IS '删除人';
COMMENT ON COLUMN yb_recon_daily.tenant_id IS '租户标识';

-- from V8__yb_recon_detail.sql
-- Wave 3 扩展：3202 明细对账本地记录

CREATE TABLE IF NOT EXISTS yb_recon_detail (
    id                  BIGINT         NOT NULL PRIMARY KEY,
    contract_id         BIGINT         NOT NULL,
    contract_bus_no     VARCHAR(64)    NOT NULL,
    setl_optins         VARCHAR(6),
    clr_type            VARCHAR(6),
    refd_setl_flag      VARCHAR(3),
    stmt_begndate       DATE,
    stmt_enddate        DATE,
    upload_file_qury_no VARCHAR(64),
    upload_filename     VARCHAR(200),
    result_file_qury_no VARCHAR(64),
    result_filename     VARCHAR(200),
    dld_endtime         VARCHAR(30),
    row_count           INTEGER,
    mismatch_count      INTEGER,
    result_json         TEXT,

    created_at          TIMESTAMP      NOT NULL,
    updated_at          TIMESTAMP,
    created_by          BIGINT,
    updated_by          BIGINT,
    is_deleted          BOOLEAN        NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    deleted_by          BIGINT,
    tenant_id           BIGINT         NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_yb_recon_detail_contract
    ON yb_recon_detail (contract_bus_no, tenant_id)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE yb_recon_detail IS '医保 recon detail';
COMMENT ON COLUMN yb_recon_detail.id IS '主键';
COMMENT ON COLUMN yb_recon_detail.contract_id IS '合同标识';
COMMENT ON COLUMN yb_recon_detail.contract_bus_no IS '合同bus编号';
COMMENT ON COLUMN yb_recon_detail.setl_optins IS '结算optins';
COMMENT ON COLUMN yb_recon_detail.clr_type IS 'clr类型';
COMMENT ON COLUMN yb_recon_detail.refd_setl_flag IS 'refd结算标志位';
COMMENT ON COLUMN yb_recon_detail.stmt_begndate IS 'stmt开始日期';
COMMENT ON COLUMN yb_recon_detail.stmt_enddate IS 'stmt结束日期';
COMMENT ON COLUMN yb_recon_detail.upload_file_qury_no IS '上传filequry编号';
COMMENT ON COLUMN yb_recon_detail.upload_filename IS '上传filename';
COMMENT ON COLUMN yb_recon_detail.result_file_qury_no IS 'resultfilequry编号';
COMMENT ON COLUMN yb_recon_detail.result_filename IS '结果filename';
COMMENT ON COLUMN yb_recon_detail.dld_endtime IS '下载结束时间';
COMMENT ON COLUMN yb_recon_detail.row_count IS '行数';
COMMENT ON COLUMN yb_recon_detail.mismatch_count IS '不一致笔数';
COMMENT ON COLUMN yb_recon_detail.result_json IS '结果JSON';
COMMENT ON COLUMN yb_recon_detail.created_at IS '创建时间';
COMMENT ON COLUMN yb_recon_detail.updated_at IS '最后更新时间';
COMMENT ON COLUMN yb_recon_detail.created_by IS '创建人';
COMMENT ON COLUMN yb_recon_detail.updated_by IS '最后更新人';
COMMENT ON COLUMN yb_recon_detail.is_deleted IS '软删标记';
COMMENT ON COLUMN yb_recon_detail.deleted_at IS '删除时间';
COMMENT ON COLUMN yb_recon_detail.deleted_by IS '删除人';
COMMENT ON COLUMN yb_recon_detail.tenant_id IS '租户标识';

-- from V9__yb_inv_upload.sql
-- Wave I：3505/3506 进销存上报流水

CREATE TABLE IF NOT EXISTS yb_inv_upload (
    id                  BIGINT         NOT NULL PRIMARY KEY,
    contract_id         BIGINT         NOT NULL,
    contract_bus_no     VARCHAR(32)    NOT NULL,
    infno               VARCHAR(8)     NOT NULL,
    inv_data_type       VARCHAR(4),
    fixmedins_bchno     VARCHAR(30)    NOT NULL,
    med_list_codg       VARCHAR(50),
    source_type         VARCHAR(32)    NOT NULL,
    source_id           BIGINT,
    source_line_id      BIGINT,
    upload_status       VARCHAR(16)    NOT NULL,
    platform_ret_rslt   VARCHAR(8),
    platform_msg        VARCHAR(2000),
    msgid               VARCHAR(64),
    request_json        TEXT,
    response_json       TEXT,
    retry_count         INT            NOT NULL DEFAULT 0,
    next_retry_at       TIMESTAMP,
    operator_id         BIGINT,

    created_at          TIMESTAMP      NOT NULL,
    updated_at          TIMESTAMP,
    created_by          BIGINT,
    updated_by          BIGINT,
    is_deleted          BOOLEAN        NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    deleted_by          BIGINT,
    tenant_id           BIGINT         NOT NULL,

    CONSTRAINT uk_yb_inv_upload_idempotent UNIQUE (tenant_id, infno, fixmedins_bchno, source_line_id)
);

CREATE INDEX IF NOT EXISTS idx_yb_inv_upload_status ON yb_inv_upload (tenant_id, upload_status, next_retry_at);
CREATE INDEX IF NOT EXISTS idx_yb_inv_upload_source ON yb_inv_upload (source_type, source_id);

CREATE TABLE IF NOT EXISTS yb_inv_upload_trace (
    id              BIGINT         NOT NULL PRIMARY KEY,
    upload_id       BIGINT         NOT NULL,
    drug_trac_codg  VARCHAR(100)   NOT NULL,
    trdn_flag       VARCHAR(3),

    created_at      TIMESTAMP      NOT NULL,
    updated_at      TIMESTAMP,
    created_by      BIGINT,
    updated_by      BIGINT,
    is_deleted      BOOLEAN        NOT NULL DEFAULT FALSE,
    deleted_at      TIMESTAMP,
    deleted_by      BIGINT,
    tenant_id       BIGINT         NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_yb_inv_upload_trace_upload ON yb_inv_upload_trace (upload_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_yb_inv_upload_trace_codg
    ON yb_inv_upload_trace (tenant_id, drug_trac_codg)
    WHERE is_deleted = FALSE;

-- yb_platform_dict_item：1901 平台字典镜像（医保区划等）
-- CHS Flyway V16

CREATE TABLE IF NOT EXISTS yb_platform_dict_item (
    id                  BIGINT       NOT NULL PRIMARY KEY,
    tenant_id           BIGINT       NOT NULL,
    dict_type           VARCHAR(30)  NOT NULL,
    dict_value          VARCHAR(20)  NOT NULL,
    dict_label          VARCHAR(200) NOT NULL,
    parent_value        VARCHAR(20),
    sort_order          INTEGER,
    vali_flag           VARCHAR(3),
    platform_version    BIGINT,
    query_admdvs        VARCHAR(6),
    source_contract_id  BIGINT,
    synced_at           TIMESTAMPTZ  NOT NULL,

    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ,
    created_by          BIGINT,
    updated_by          BIGINT,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMPTZ,
    deleted_by          BIGINT,
    version             INTEGER      NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_yb_platform_dict_item
    ON yb_platform_dict_item (tenant_id, dict_type, dict_value)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_yb_platform_dict_parent
    ON yb_platform_dict_item (tenant_id, dict_type, parent_value)
    WHERE is_deleted = FALSE;

CREATE TABLE IF NOT EXISTS yb_platform_dict_sync_log (
    id                  BIGINT       NOT NULL PRIMARY KEY,
    tenant_id           BIGINT       NOT NULL,
    dict_type           VARCHAR(30)  NOT NULL,
    query_admdvs        VARCHAR(6),
    parent_value        VARCHAR(20),
    recursive           BOOLEAN      NOT NULL DEFAULT TRUE,
    contract_id         BIGINT,
    rows_upserted       INTEGER      NOT NULL DEFAULT 0,
    status              VARCHAR(16)  NOT NULL,
    error_message       TEXT,
    started_at          TIMESTAMPTZ  NOT NULL,
    finished_at         TIMESTAMPTZ,

    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ,
    created_by          BIGINT,
    updated_by          BIGINT,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMPTZ,
    deleted_by          BIGINT,
    version             INTEGER      NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_yb_platform_dict_sync_log_tenant
    ON yb_platform_dict_sync_log (tenant_id, dict_type, started_at DESC)
    WHERE is_deleted = FALSE;
COMMENT ON TABLE yb_inv_upload IS '医保 inv upload';
COMMENT ON COLUMN yb_inv_upload.id IS '主键';
COMMENT ON COLUMN yb_inv_upload.contract_id IS '合同标识';
COMMENT ON COLUMN yb_inv_upload.contract_bus_no IS '合同bus编号';
COMMENT ON COLUMN yb_inv_upload.infno IS '接口编号';
COMMENT ON COLUMN yb_inv_upload.inv_data_type IS '库存data类型';
COMMENT ON COLUMN yb_inv_upload.fixmedins_bchno IS 'fixmedins批次号';
COMMENT ON COLUMN yb_inv_upload.med_list_codg IS '医疗目录编码';
COMMENT ON COLUMN yb_inv_upload.source_type IS '来源类型';
COMMENT ON COLUMN yb_inv_upload.source_id IS '来源标识';
COMMENT ON COLUMN yb_inv_upload.source_line_id IS '来源line标识';
COMMENT ON COLUMN yb_inv_upload.upload_status IS '上传状态';
COMMENT ON COLUMN yb_inv_upload.platform_ret_rslt IS '平台返回结果';
COMMENT ON COLUMN yb_inv_upload.platform_msg IS '平台消息';
COMMENT ON COLUMN yb_inv_upload.msgid IS '报文标识';
COMMENT ON COLUMN yb_inv_upload.request_json IS '申请JSON';
COMMENT ON COLUMN yb_inv_upload.response_json IS '响应JSON';
COMMENT ON COLUMN yb_inv_upload.retry_count IS '重试次数';
COMMENT ON COLUMN yb_inv_upload.next_retry_at IS 'nextretry时间';
COMMENT ON COLUMN yb_inv_upload.operator_id IS '操作人标识';
COMMENT ON COLUMN yb_inv_upload.created_at IS '创建时间';
COMMENT ON COLUMN yb_inv_upload.updated_at IS '最后更新时间';
COMMENT ON COLUMN yb_inv_upload.created_by IS '创建人';
COMMENT ON COLUMN yb_inv_upload.updated_by IS '最后更新人';
COMMENT ON COLUMN yb_inv_upload.is_deleted IS '软删标记';
COMMENT ON COLUMN yb_inv_upload.deleted_at IS '删除时间';
COMMENT ON COLUMN yb_inv_upload.deleted_by IS '删除人';
COMMENT ON COLUMN yb_inv_upload.tenant_id IS '租户标识';

COMMENT ON TABLE yb_inv_upload_trace IS '医保 inv upload trace';
COMMENT ON COLUMN yb_inv_upload_trace.id IS '主键';
COMMENT ON COLUMN yb_inv_upload_trace.upload_id IS '上传标识';
COMMENT ON COLUMN yb_inv_upload_trace.drug_trac_codg IS 'drugtrac编码';
COMMENT ON COLUMN yb_inv_upload_trace.trdn_flag IS 'trdn标志位';
COMMENT ON COLUMN yb_inv_upload_trace.created_at IS '创建时间';
COMMENT ON COLUMN yb_inv_upload_trace.updated_at IS '最后更新时间';
COMMENT ON COLUMN yb_inv_upload_trace.created_by IS '创建人';
COMMENT ON COLUMN yb_inv_upload_trace.updated_by IS '最后更新人';
COMMENT ON COLUMN yb_inv_upload_trace.is_deleted IS '软删标记';
COMMENT ON COLUMN yb_inv_upload_trace.deleted_at IS '删除时间';
COMMENT ON COLUMN yb_inv_upload_trace.deleted_by IS '删除人';
COMMENT ON COLUMN yb_inv_upload_trace.tenant_id IS '租户标识';

COMMENT ON TABLE yb_platform_dict_item IS '1901 平台字典镜像（医保区划 INSUPLC_ADMDVS 等）';
COMMENT ON COLUMN yb_platform_dict_item.id IS '主键';
COMMENT ON COLUMN yb_platform_dict_item.tenant_id IS '租户标识';
COMMENT ON COLUMN yb_platform_dict_item.dict_type IS '1901 type，医保区划默认 INSUPLC_ADMDVS';
COMMENT ON COLUMN yb_platform_dict_item.dict_value IS '1901 value，如 220100';
COMMENT ON COLUMN yb_platform_dict_item.dict_label IS '1901 label，展示地名';
COMMENT ON COLUMN yb_platform_dict_item.parent_value IS '父级值';
COMMENT ON COLUMN yb_platform_dict_item.sort_order IS '排序号';
COMMENT ON COLUMN yb_platform_dict_item.vali_flag IS 'vali标志位';
COMMENT ON COLUMN yb_platform_dict_item.platform_version IS '平台版本';
COMMENT ON COLUMN yb_platform_dict_item.query_admdvs IS '查询admdvs';
COMMENT ON COLUMN yb_platform_dict_item.source_contract_id IS '来源合同标识';
COMMENT ON COLUMN yb_platform_dict_item.synced_at IS 'synced时间';
COMMENT ON COLUMN yb_platform_dict_item.created_at IS '创建时间';
COMMENT ON COLUMN yb_platform_dict_item.updated_at IS '最后更新时间';
COMMENT ON COLUMN yb_platform_dict_item.created_by IS '创建人';
COMMENT ON COLUMN yb_platform_dict_item.updated_by IS '最后更新人';
COMMENT ON COLUMN yb_platform_dict_item.is_deleted IS '软删标记';
COMMENT ON COLUMN yb_platform_dict_item.deleted_at IS '删除时间';
COMMENT ON COLUMN yb_platform_dict_item.deleted_by IS '删除人';
COMMENT ON COLUMN yb_platform_dict_item.version IS '乐观锁版本号';

COMMENT ON TABLE yb_platform_dict_sync_log IS '1901 字典手动同步日志';
COMMENT ON COLUMN yb_platform_dict_sync_log.id IS '主键';
COMMENT ON COLUMN yb_platform_dict_sync_log.tenant_id IS '租户标识';
COMMENT ON COLUMN yb_platform_dict_sync_log.dict_type IS 'dict类型';
COMMENT ON COLUMN yb_platform_dict_sync_log.query_admdvs IS '查询admdvs';
COMMENT ON COLUMN yb_platform_dict_sync_log.parent_value IS '父级值';
COMMENT ON COLUMN yb_platform_dict_sync_log.recursive IS '是否递归';
COMMENT ON COLUMN yb_platform_dict_sync_log.contract_id IS '合同标识';
COMMENT ON COLUMN yb_platform_dict_sync_log.rows_upserted IS '写入/更新行数';
COMMENT ON COLUMN yb_platform_dict_sync_log.status IS '状态';
COMMENT ON COLUMN yb_platform_dict_sync_log.error_message IS '错误消息';
COMMENT ON COLUMN yb_platform_dict_sync_log.started_at IS '开始时间';
COMMENT ON COLUMN yb_platform_dict_sync_log.finished_at IS '结束时间';
COMMENT ON COLUMN yb_platform_dict_sync_log.created_at IS '创建时间';
COMMENT ON COLUMN yb_platform_dict_sync_log.updated_at IS '最后更新时间';
COMMENT ON COLUMN yb_platform_dict_sync_log.created_by IS '创建人';
COMMENT ON COLUMN yb_platform_dict_sync_log.updated_by IS '最后更新人';
COMMENT ON COLUMN yb_platform_dict_sync_log.is_deleted IS '软删标记';
COMMENT ON COLUMN yb_platform_dict_sync_log.deleted_at IS '删除时间';
COMMENT ON COLUMN yb_platform_dict_sync_log.deleted_by IS '删除人';
COMMENT ON COLUMN yb_platform_dict_sync_log.version IS '乐观锁版本号';

-- from V10__yb_clinic_fee_upload.sql
-- 门诊 2204 费用上传及平台分割结果（对齐 YbClinicFeeUpload）

CREATE TABLE IF NOT EXISTS yb_clinic_fee_upload (
    id                      BIGINT       NOT NULL PRIMARY KEY,
    encounter_id            BIGINT       NOT NULL,
    charge_item_id          BIGINT       NOT NULL,
    feedetl_sn              VARCHAR(30)  NOT NULL,
    mdtrt_id                VARCHAR(30)  NOT NULL,
    chrg_bchno              VARCHAR(30)  NOT NULL,
    pre_settle_id           BIGINT,
    upload_status           VARCHAR(16)  NOT NULL,
    det_item_fee_sumamt     NUMERIC(18, 2),
    chrgitm_lv              VARCHAR(3),
    med_chrgitm_type        VARCHAR(6),
    selfpay_prop            NUMERIC(5, 4),
    fulamt_ownpay_amt       NUMERIC(18, 2),
    overlmt_amt             NUMERIC(18, 2),
    preselfpay_amt          NUMERIC(18, 2),
    inscp_scp_amt           NUMERIC(18, 2),
    lmt_used_flag           VARCHAR(3),
    platform_memo           VARCHAR(500),
    platform_result_json    TEXT,
    uploaded_at             TIMESTAMP,

    created_at              TIMESTAMP    NOT NULL,
    updated_at              TIMESTAMP,
    created_by              BIGINT,
    updated_by              BIGINT,
    is_deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMP,
    deleted_by              BIGINT,
    tenant_id               BIGINT       NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_yb_clinic_fee_upload_item
    ON yb_clinic_fee_upload (charge_item_id, chrg_bchno, tenant_id)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_yb_clinic_fee_upload_bchno
    ON yb_clinic_fee_upload (chrg_bchno, upload_status, tenant_id)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE yb_clinic_fee_upload IS '2204 门诊费用上传及平台分割结果';
COMMENT ON COLUMN yb_clinic_fee_upload.id IS '主键';
COMMENT ON COLUMN yb_clinic_fee_upload.encounter_id IS '就诊标识';
COMMENT ON COLUMN yb_clinic_fee_upload.charge_item_id IS '收费项标识';
COMMENT ON COLUMN yb_clinic_fee_upload.feedetl_sn IS '费用明细流水号';
COMMENT ON COLUMN yb_clinic_fee_upload.mdtrt_id IS '就诊标识';
COMMENT ON COLUMN yb_clinic_fee_upload.chrg_bchno IS '收费批次号';
COMMENT ON COLUMN yb_clinic_fee_upload.pre_settle_id IS '预结算标识';
COMMENT ON COLUMN yb_clinic_fee_upload.upload_status IS '上传状态';
COMMENT ON COLUMN yb_clinic_fee_upload.det_item_fee_sumamt IS '明细项目费用总额';
COMMENT ON COLUMN yb_clinic_fee_upload.chrgitm_lv IS '收费项目等级';
COMMENT ON COLUMN yb_clinic_fee_upload.med_chrgitm_type IS '医疗收费项目类别';
COMMENT ON COLUMN yb_clinic_fee_upload.selfpay_prop IS '自付比例';
COMMENT ON COLUMN yb_clinic_fee_upload.fulamt_ownpay_amt IS '全自费金额';
COMMENT ON COLUMN yb_clinic_fee_upload.overlmt_amt IS '超限价金额';
COMMENT ON COLUMN yb_clinic_fee_upload.preselfpay_amt IS '先行自付金额';
COMMENT ON COLUMN yb_clinic_fee_upload.inscp_scp_amt IS '符合政策范围金额';
COMMENT ON COLUMN yb_clinic_fee_upload.lmt_used_flag IS '限制使用标志';
COMMENT ON COLUMN yb_clinic_fee_upload.platform_memo IS '平台备注';
COMMENT ON COLUMN yb_clinic_fee_upload.platform_result_json IS '平台分割结果JSON';
COMMENT ON COLUMN yb_clinic_fee_upload.uploaded_at IS '上传时间';
COMMENT ON COLUMN yb_clinic_fee_upload.created_at IS '创建时间';
COMMENT ON COLUMN yb_clinic_fee_upload.updated_at IS '最后更新时间';
COMMENT ON COLUMN yb_clinic_fee_upload.created_by IS '创建人';
COMMENT ON COLUMN yb_clinic_fee_upload.updated_by IS '最后更新人';
COMMENT ON COLUMN yb_clinic_fee_upload.is_deleted IS '软删标记';
COMMENT ON COLUMN yb_clinic_fee_upload.deleted_at IS '删除时间';
COMMENT ON COLUMN yb_clinic_fee_upload.deleted_by IS '删除人';
COMMENT ON COLUMN yb_clinic_fee_upload.tenant_id IS '租户标识';

-- from V11__chs_settlement_schema_align.sql
-- CHS 结算域 schema 对齐：补全实体与 V5/V6 迁移之间的缺口

-- ---------------------------------------------------------------------------
-- 1. 新建表
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS yb_invoke_log (
    id                      BIGINT       NOT NULL PRIMARY KEY,
    contract_id             BIGINT,
    contract_bus_no         VARCHAR(64),
    infno                   VARCHAR(16)  NOT NULL,
    msgid                   VARCHAR(64),
    encounter_id            BIGINT,
    payment_draft_id        BIGINT,
    chrg_bchno              VARCHAR(30),
    pre_settle_id           BIGINT,
    settlement_record_id    BIGINT,
    infcode                 VARCHAR(8),
    duration_ms             INT,
    request_json            TEXT,
    response_json           TEXT,

    created_at              TIMESTAMP    NOT NULL,
    updated_at              TIMESTAMP,
    created_by              BIGINT,
    updated_by              BIGINT,
    is_deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMP,
    deleted_by              BIGINT,
    infname VARCHAR(128),
    err_msg VARCHAR(512),

    tenant_id               BIGINT       NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_yb_invoke_log_encounter
    ON yb_invoke_log (encounter_id, infno)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_yb_invoke_log_settlement
    ON yb_invoke_log (settlement_record_id)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_yb_invoke_log_msgid
    ON yb_invoke_log (msgid, tenant_id)
    WHERE is_deleted = FALSE;

-- from former V4__yb_invoke_log_page_index.sql
CREATE INDEX IF NOT EXISTS idx_yb_invoke_log_tenant_created
    ON yb_invoke_log (tenant_id, created_at DESC)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_yb_invoke_log_tenant_infno_created
    ON yb_invoke_log (tenant_id, infno, created_at DESC)
    WHERE is_deleted = FALSE;

CREATE TABLE IF NOT EXISTS yb_settlement_fund_detail (
    id                      BIGINT       NOT NULL PRIMARY KEY,
    settlement_record_id    BIGINT       NOT NULL,
    fund_pay_type           VARCHAR(16),
    fund_pay_type_name      VARCHAR(128),
    inscp_scp_amt           NUMERIC(18, 2),
    crt_payb_lmt_amt        NUMERIC(18, 2),
    fund_payamt             NUMERIC(18, 2),
    setl_proc_info          TEXT,

    created_at              TIMESTAMP    NOT NULL,
    updated_at              TIMESTAMP,
    created_by              BIGINT,
    updated_by              BIGINT,
    is_deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMP,
    deleted_by              BIGINT,
    tenant_id               BIGINT       NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_yb_settlement_fund_detail_record
    ON yb_settlement_fund_detail (settlement_record_id)
    WHERE is_deleted = FALSE;

CREATE TABLE IF NOT EXISTS yb_settlement_correction (
    id                      BIGINT       NOT NULL PRIMARY KEY,
    settlement_record_id    BIGINT,
    psn_no                  VARCHAR(30)  NOT NULL,
    omsgid                  VARCHAR(30)  NOT NULL,
    oinfno                  VARCHAR(4)   NOT NULL,
    encounter_class         VARCHAR(8),
    invoke_log_id           BIGINT,
    corrected_at            TIMESTAMP    NOT NULL,

    created_at              TIMESTAMP    NOT NULL,
    updated_at              TIMESTAMP,
    created_by              BIGINT,
    updated_by              BIGINT,
    is_deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMP,
    deleted_by              BIGINT,
    tenant_id               BIGINT       NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_yb_settlement_correction_record
    ON yb_settlement_correction (settlement_record_id)
    WHERE is_deleted = FALSE;

-- ---------------------------------------------------------------------------
-- 2. 门诊预结算：补 2206 扩展字段
-- ---------------------------------------------------------------------------

-- ---------------------------------------------------------------------------
-- 3. 住院预结算：补 2303 扩展字段
-- ---------------------------------------------------------------------------

-- ---------------------------------------------------------------------------
-- 4. 住院费用上传：补 2301 分割结果字段（对齐 yb_clinic_fee_upload）
-- ---------------------------------------------------------------------------

-- ---------------------------------------------------------------------------
-- 5. 结算凭证：补 2207/2304 setlinfo 完整字段
-- ---------------------------------------------------------------------------

-- ---------------------------------------------------------------------------
-- 6. 结算撤销：补 invoke 关联
-- ---------------------------------------------------------------------------

-- ---------------------------------------------------------------------------
-- 注释
-- ---------------------------------------------------------------------------

COMMENT ON TABLE yb_invoke_log IS '医保平台网关调用日志';
COMMENT ON COLUMN yb_invoke_log.id IS '主键';
COMMENT ON COLUMN yb_invoke_log.contract_id IS '合同标识';
COMMENT ON COLUMN yb_invoke_log.contract_bus_no IS '合同bus编号';
COMMENT ON COLUMN yb_invoke_log.infno IS '交易编号';
COMMENT ON COLUMN yb_invoke_log.msgid IS '发送方报文ID';
COMMENT ON COLUMN yb_invoke_log.encounter_id IS '就诊标识';
COMMENT ON COLUMN yb_invoke_log.payment_draft_id IS '支付draft标识';
COMMENT ON COLUMN yb_invoke_log.chrg_bchno IS '收费批次号';
COMMENT ON COLUMN yb_invoke_log.pre_settle_id IS '预结算标识';
COMMENT ON COLUMN yb_invoke_log.settlement_record_id IS '结算记录标识';
COMMENT ON COLUMN yb_invoke_log.infcode IS '平台返回码';
COMMENT ON COLUMN yb_invoke_log.duration_ms IS '调用耗时毫秒';
COMMENT ON COLUMN yb_invoke_log.request_json IS '请求JSON';
COMMENT ON COLUMN yb_invoke_log.response_json IS '响应JSON';
COMMENT ON COLUMN yb_invoke_log.created_at IS '创建时间';
COMMENT ON COLUMN yb_invoke_log.updated_at IS '最后更新时间';
COMMENT ON COLUMN yb_invoke_log.created_by IS '创建人';
COMMENT ON COLUMN yb_invoke_log.updated_by IS '最后更新人';
COMMENT ON COLUMN yb_invoke_log.is_deleted IS '软删标记';
COMMENT ON COLUMN yb_invoke_log.deleted_at IS '删除时间';
COMMENT ON COLUMN yb_invoke_log.deleted_by IS '删除人';
COMMENT ON COLUMN yb_invoke_log.tenant_id IS '租户标识';

COMMENT ON TABLE yb_settlement_fund_detail IS '2207/2304 setldetail 基金分项明细';
COMMENT ON COLUMN yb_settlement_fund_detail.id IS '主键';
COMMENT ON COLUMN yb_settlement_fund_detail.settlement_record_id IS '结算记录标识';
COMMENT ON COLUMN yb_settlement_fund_detail.fund_pay_type IS '基金支付类型（国家码，最长 16）';
COMMENT ON COLUMN yb_settlement_fund_detail.fund_pay_type_name IS '基金支付类型名称';
COMMENT ON COLUMN yb_settlement_fund_detail.inscp_scp_amt IS '符合政策范围金额';
COMMENT ON COLUMN yb_settlement_fund_detail.crt_payb_lmt_amt IS '本次可支付限额金额';
COMMENT ON COLUMN yb_settlement_fund_detail.fund_payamt IS '基金支付金额';
COMMENT ON COLUMN yb_settlement_fund_detail.setl_proc_info IS '结算过程信息';
COMMENT ON COLUMN yb_settlement_fund_detail.created_at IS '创建时间';
COMMENT ON COLUMN yb_settlement_fund_detail.updated_at IS '最后更新时间';
COMMENT ON COLUMN yb_settlement_fund_detail.created_by IS '创建人';
COMMENT ON COLUMN yb_settlement_fund_detail.updated_by IS '最后更新人';
COMMENT ON COLUMN yb_settlement_fund_detail.is_deleted IS '软删标记';
COMMENT ON COLUMN yb_settlement_fund_detail.deleted_at IS '删除时间';
COMMENT ON COLUMN yb_settlement_fund_detail.deleted_by IS '删除人';
COMMENT ON COLUMN yb_settlement_fund_detail.tenant_id IS '租户标识';

COMMENT ON TABLE yb_settlement_correction IS '2601 冲正交易审计凭证';
COMMENT ON COLUMN yb_settlement_correction.id IS '主键';
COMMENT ON COLUMN yb_settlement_correction.settlement_record_id IS '结算记录标识';
COMMENT ON COLUMN yb_settlement_correction.psn_no IS 'psn编号';
COMMENT ON COLUMN yb_settlement_correction.omsgid IS '原发送方报文ID';
COMMENT ON COLUMN yb_settlement_correction.oinfno IS '原交易编号';
COMMENT ON COLUMN yb_settlement_correction.encounter_class IS '就诊class';
COMMENT ON COLUMN yb_settlement_correction.invoke_log_id IS '调用日志标识';
COMMENT ON COLUMN yb_settlement_correction.corrected_at IS '冲正时间';
COMMENT ON COLUMN yb_settlement_correction.created_at IS '创建时间';
COMMENT ON COLUMN yb_settlement_correction.updated_at IS '最后更新时间';
COMMENT ON COLUMN yb_settlement_correction.created_by IS '创建人';
COMMENT ON COLUMN yb_settlement_correction.updated_by IS '最后更新人';
COMMENT ON COLUMN yb_settlement_correction.is_deleted IS '软删标记';
COMMENT ON COLUMN yb_settlement_correction.deleted_at IS '删除时间';
COMMENT ON COLUMN yb_settlement_correction.deleted_by IS '删除人';
COMMENT ON COLUMN yb_settlement_correction.tenant_id IS '租户标识';

COMMENT ON COLUMN yb_clinic_pre_settle.mdtrt_cert_type IS '就诊cert类型';
COMMENT ON COLUMN yb_clinic_pre_settle.mdtrt_cert_no IS '就诊cert编号';
COMMENT ON COLUMN yb_clinic_pre_settle.insutype IS '险种类型';
COMMENT ON COLUMN yb_clinic_pre_settle.acct_used_flag IS '个人账户使用标志';
COMMENT ON COLUMN yb_clinic_pre_settle.psn_setlway IS '个人结算方式';
COMMENT ON COLUMN yb_clinic_pre_settle.fulamt_ownpay_amt IS '全自费金额';
COMMENT ON COLUMN yb_clinic_pre_settle.overlmt_selfpay IS '超限价自费费用';
COMMENT ON COLUMN yb_clinic_pre_settle.preselfpay_amt IS '先行自付金额';
COMMENT ON COLUMN yb_clinic_pre_settle.inscp_scp_amt IS '符合政策范围金额';

COMMENT ON COLUMN yb_inpatient_pre_settle.insuplc_admdvs IS '参保地医保区划';
COMMENT ON COLUMN yb_inpatient_pre_settle.mdtrt_cert_type IS '就诊cert类型';
COMMENT ON COLUMN yb_inpatient_pre_settle.mdtrt_cert_no IS '就诊cert编号';
COMMENT ON COLUMN yb_inpatient_pre_settle.insutype IS '险种类型';
COMMENT ON COLUMN yb_inpatient_pre_settle.acct_used_flag IS '个人账户使用标志';
COMMENT ON COLUMN yb_inpatient_pre_settle.psn_setlway IS '个人结算方式';
COMMENT ON COLUMN yb_inpatient_pre_settle.fulamt_ownpay_amt IS '全自费金额';
COMMENT ON COLUMN yb_inpatient_pre_settle.overlmt_selfpay IS '超限价自费费用';
COMMENT ON COLUMN yb_inpatient_pre_settle.preselfpay_amt IS '先行自付金额';
COMMENT ON COLUMN yb_inpatient_pre_settle.inscp_scp_amt IS '符合政策范围金额';

COMMENT ON COLUMN yb_inpatient_fee_upload.chrgitm_lv IS '收费项目等级';
COMMENT ON COLUMN yb_inpatient_fee_upload.med_chrgitm_type IS '医疗收费项目类别';
COMMENT ON COLUMN yb_inpatient_fee_upload.selfpay_prop IS '自付比例';
COMMENT ON COLUMN yb_inpatient_fee_upload.fulamt_ownpay_amt IS '全自费金额';
COMMENT ON COLUMN yb_inpatient_fee_upload.overlmt_amt IS '超限价金额';
COMMENT ON COLUMN yb_inpatient_fee_upload.preselfpay_amt IS '先行自付金额';
COMMENT ON COLUMN yb_inpatient_fee_upload.inscp_scp_amt IS '符合政策范围金额';
COMMENT ON COLUMN yb_inpatient_fee_upload.lmt_used_flag IS '限制使用标志';
COMMENT ON COLUMN yb_inpatient_fee_upload.platform_memo IS '平台备注';

COMMENT ON COLUMN yb_settlement_record.settle_mode IS '结算模式';
COMMENT ON COLUMN yb_settlement_record.psn_name IS '人员姓名';
COMMENT ON COLUMN yb_settlement_record.insutype IS '险种类型';
COMMENT ON COLUMN yb_settlement_record.med_type IS '医疗类别';
COMMENT ON COLUMN yb_settlement_record.setl_time IS '结算时间';
COMMENT ON COLUMN yb_settlement_record.fulamt_ownpay_amt IS '全自费金额';
COMMENT ON COLUMN yb_settlement_record.overlmt_selfpay IS '超限价自费费用';
COMMENT ON COLUMN yb_settlement_record.preselfpay_amt IS '先行自付金额';
COMMENT ON COLUMN yb_settlement_record.inscp_scp_amt IS '符合政策范围金额';
COMMENT ON COLUMN yb_settlement_record.hifp_pay IS '基本医疗保险统筹基金支出';
COMMENT ON COLUMN yb_settlement_record.cvlserv_pay IS '公务员医疗补助资金支出';
COMMENT ON COLUMN yb_settlement_record.hifmi_pay IS '居民大病保险资金支出';
COMMENT ON COLUMN yb_settlement_record.hifob_pay IS '职工大额医疗费用补助基金支出';
COMMENT ON COLUMN yb_settlement_record.hifes_pay IS '企业补充医疗保险基金支出';
COMMENT ON COLUMN yb_settlement_record.cvlserv_flag IS '公务员标志';
COMMENT ON COLUMN yb_settlement_record.pool_prop_selfpay IS '基本医疗保险统筹基金支付比例';
COMMENT ON COLUMN yb_settlement_record.maf_pay IS '医疗救助基金支出';
COMMENT ON COLUMN yb_settlement_record.oth_pay IS '其他支出';
COMMENT ON COLUMN yb_settlement_record.psn_part_amt IS '个人负担总金额';
COMMENT ON COLUMN yb_settlement_record.hosp_part_amt IS '医院负担金额';
COMMENT ON COLUMN yb_settlement_record.balc IS '余额';
COMMENT ON COLUMN yb_settlement_record.acct_mulaid_pay IS '个人账户共济支付金额';
COMMENT ON COLUMN yb_settlement_record.psn_type IS '人员类别';
COMMENT ON COLUMN yb_settlement_record.act_pay_dedc IS '实际支付起付线';
COMMENT ON COLUMN yb_settlement_record.hifdm_pay IS '伤残人员医疗保障基金支出';
COMMENT ON COLUMN yb_settlement_record.exp_content IS '扩展内容JSON';
COMMENT ON COLUMN yb_settlement_record.medins_setl_id IS '医药机构结算ID';
COMMENT ON COLUMN yb_settlement_record.msgid_2207 IS '2207发送方报文ID';
COMMENT ON COLUMN yb_settlement_record.clr_optins IS '清算经办机构';
COMMENT ON COLUMN yb_settlement_record.clr_way IS '清算方式';
COMMENT ON COLUMN yb_settlement_record.clr_type IS '清算类别';
COMMENT ON COLUMN yb_settlement_record.fund_breakdown_json IS '基金分项JSON';

COMMENT ON COLUMN yb_settlement_reversal.invoke_log_id IS '调用日志标识';

-- ===========================================================================
-- Folded from former incrementals (V2, V6, V7, V8, V10, V11 backfill)
-- ===========================================================================

-- from former V2__yb_catalog_notice.sql
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

-- from former V6__yb_national_catalog_picker_index.sql
-- 药品/国家目录选码检索加速：模糊匹配 + 1312/1301 过滤路径
-- 依赖 PostgreSQL pg_trgm（托管库若无权限，需 DBA 预先 CREATE EXTENSION）

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 1312 摘要选码（vali=1，无 source_line_no 或来源 1312）
CREATE INDEX IF NOT EXISTS idx_yb_nat_cat_picker_1312
    ON yb_national_catalog_item (contract_id, list_type, tenant_id, hilist_code)
    WHERE is_deleted = FALSE
      AND vali_flag = '1'
      AND (source_infno = '1312' OR source_line_no IS NULL);

-- 1301 明细选码/分页
CREATE INDEX IF NOT EXISTS idx_yb_nat_cat_picker_1301
    ON yb_national_catalog_item (contract_id, list_type, tenant_id, hilist_code)
    WHERE is_deleted = FALSE
      AND source_infno = '1301'
      AND source_line_no IS NOT NULL;

-- 名称 / 拼音 / 编码 / 批准文号：前后模糊（配合 ILIKE）
CREATE INDEX IF NOT EXISTS idx_yb_nat_cat_hilist_name_trgm
    ON yb_national_catalog_item USING gin (hilist_name gin_trgm_ops)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_yb_nat_cat_pinyin_trgm
    ON yb_national_catalog_item USING gin (pinyin gin_trgm_ops)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_yb_nat_cat_hilist_code_trgm
    ON yb_national_catalog_item USING gin (hilist_code gin_trgm_ops)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_yb_nat_cat_aprvno_trgm
    ON yb_national_catalog_item USING gin (aprvno gin_trgm_ops)
    WHERE is_deleted = FALSE
      AND aprvno IS NOT NULL
      AND aprvno <> '';

-- from former V7__yb_detail_audit.sql
/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
-- CHS Flyway V7：明细审核 3101/3103 结构化落库（AS 试调 / W3 统计）

CREATE TABLE IF NOT EXISTS yb_detail_audit_hit (
    id                      BIGINT         NOT NULL PRIMARY KEY,
    jr_id                   VARCHAR(64)    NOT NULL,
    task_id                 VARCHAR(64),
    trig_scen               VARCHAR(8),
    contract_bus_no         VARCHAR(64),
    mdtrt_id                VARCHAR(64),
    patn_id                 VARCHAR(64),
    rule_id                 VARCHAR(64),
    rule_name               VARCHAR(256),
    vola_cont               VARCHAR(2000),
    vola_amt                NUMERIC(18, 4),
    sev_deg                 VARCHAR(8),
    vola_bhvr_type          VARCHAR(8),
    dr_codg                 VARCHAR(64),
    dr_name                 VARCHAR(128),
    dept_codg               VARCHAR(64),
    dept_name               VARCHAR(128),
    operator_id             BIGINT,

    created_at              TIMESTAMP      NOT NULL,
    updated_at              TIMESTAMP,
    created_by              BIGINT,
    updated_by              BIGINT,
    is_deleted              BOOLEAN        NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMP,
    deleted_by              BIGINT,
    tenant_id               BIGINT         NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_yb_detail_audit_hit_jr
    ON yb_detail_audit_hit (tenant_id, jr_id)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS ix_yb_detail_audit_hit_stats
    ON yb_detail_audit_hit (tenant_id, created_at DESC, dr_codg, trig_scen)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE yb_detail_audit_hit IS '明细审核违规头（3101 result 行）';
COMMENT ON COLUMN yb_detail_audit_hit.jr_id IS '平台违规结果 ID';
COMMENT ON COLUMN yb_detail_audit_hit.dr_codg IS '责任医师编码（统计页）';

CREATE TABLE IF NOT EXISTS yb_detail_audit_hit_line (
    id                      BIGINT         NOT NULL PRIMARY KEY,
    jrd_id                  VARCHAR(64)    NOT NULL,
    jr_id                   VARCHAR(64)    NOT NULL,
    rx_id                   VARCHAR(64),
    vola_item_type          VARCHAR(8),
    vola_amt                NUMERIC(18, 4),
    mdtrt_id                VARCHAR(64),

    created_at              TIMESTAMP      NOT NULL,
    updated_at              TIMESTAMP,
    created_by              BIGINT,
    updated_by              BIGINT,
    is_deleted              BOOLEAN        NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMP,
    deleted_by              BIGINT,
    tenant_id               BIGINT         NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_yb_detail_audit_hit_line_jrd
    ON yb_detail_audit_hit_line (tenant_id, jrd_id)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS ix_yb_detail_audit_hit_line_jr
    ON yb_detail_audit_hit_line (tenant_id, jr_id)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE yb_detail_audit_hit_line IS '明细审核违规明细（judge_result_detail_dtos）';
COMMENT ON COLUMN yb_detail_audit_hit_line.jrd_id IS '平台明细 ID，3103 warn_rslt_id';

CREATE TABLE IF NOT EXISTS yb_detail_audit_feedback (
    id                      BIGINT         NOT NULL PRIMARY KEY,
    warn_rslt_id            VARCHAR(64)    NOT NULL,
    dspo_way                VARCHAR(8)     NOT NULL,
    dspo_way_rea            VARCHAR(512),
    warn_type               VARCHAR(8),
    jr_id                   VARCHAR(64),
    contract_bus_no         VARCHAR(64),
    operator_id             BIGINT,

    created_at              TIMESTAMP      NOT NULL,
    updated_at              TIMESTAMP,
    created_by              BIGINT,
    updated_by              BIGINT,
    is_deleted              BOOLEAN        NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMP,
    deleted_by              BIGINT,
    tenant_id               BIGINT         NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_yb_detail_audit_feedback_rslt
    ON yb_detail_audit_feedback (tenant_id, warn_rslt_id, created_at DESC)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE yb_detail_audit_feedback IS '明细审核 3103 反馈行';
COMMENT ON COLUMN yb_detail_audit_feedback.warn_rslt_id IS '存 jrd_id（吉林 R7=B）';

-- from former V8__yb_institution_catalog_match.sql
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

-- from former V10__backfill_retail_upload_diff.sql (safe no-op on empty fresh DB)
-- 历史回填：院内零售合计 / 2204·2301 上传合计 / 分差（retail−upload）
-- 口径对齐 SettlementRetailUploadDiffSupport + ContextLoader：
--   retail = DISTINCT charge_item_ids → adm_charge_item.total_price 合计（套餐展开重复 id 去重）
--   upload = 优先 invoke_log 成功报文 feedetail.det_item_fee_sumamt 合计；否则回落 fee_upload
-- 仅更新三字段均为 NULL 的行；幂等可重跑。不入 3201。

-- ========== 门诊预结算 ==========
WITH charge_ids AS (
    SELECT
        p.id AS pre_id,
        p.tenant_id,
        p.chrg_bchno,
        CASE
            WHEN p.charge_item_ids IS NULL OR btrim(p.charge_item_ids) = '' THEN NULL
            WHEN btrim(p.charge_item_ids) LIKE '[%' THEN (
                SELECT array_agg(DISTINCT (elem)::bigint)
                FROM jsonb_array_elements_text(p.charge_item_ids::jsonb) AS t(elem)
                WHERE btrim(elem) <> ''
            )
            ELSE (
                SELECT array_agg(DISTINCT btrim(x)::bigint)
                FROM unnest(string_to_array(p.charge_item_ids, ',')) AS x
                WHERE btrim(x) <> ''
            )
        END AS ids
    FROM yb_clinic_pre_settle p
    WHERE COALESCE(p.is_deleted, FALSE) = FALSE
      AND p.retail_medfee_sumamt IS NULL
      AND p.upload_medfee_sumamt IS NULL
      AND p.retail_upload_diff_amt IS NULL
),
retail AS (
    SELECT
        c.pre_id,
        ROUND(COALESCE(SUM(ci.total_price), 0), 2) AS retail_sum
    FROM charge_ids c
    LEFT JOIN LATERAL unnest(c.ids) AS u(cid) ON TRUE
    LEFT JOIN adm_charge_item ci ON ci.id = u.cid
    GROUP BY c.pre_id
),
upload_log AS (
    SELECT
        c.pre_id,
        ROUND(COALESCE((
            SELECT SUM((e ->> 'det_item_fee_sumamt')::numeric)
            FROM jsonb_array_elements(
                COALESCE(l.request_json::jsonb -> 'feedetail', '[]'::jsonb)
            ) AS e
        ), 0), 2) AS upload_sum,
        TRUE AS found
    FROM charge_ids c
    JOIN LATERAL (
        SELECT l.request_json
        FROM yb_invoke_log l
        WHERE l.infno = '2204'
          AND COALESCE(l.is_deleted, FALSE) = FALSE
          AND l.chrg_bchno = c.chrg_bchno
          AND l.tenant_id = c.tenant_id
          AND l.request_json IS NOT NULL
          AND btrim(l.request_json) <> ''
        ORDER BY
            CASE WHEN COALESCE(l.infcode, '0') IN ('0', '') THEN 0 ELSE 1 END,
            l.created_at DESC
        LIMIT 1
    ) l ON TRUE
),
upload_fee AS (
    SELECT
        c.pre_id,
        ROUND(COALESCE(SUM(f.det_item_fee_sumamt), 0), 2) AS upload_sum,
        COUNT(f.id) > 0 AS found
    FROM charge_ids c
    LEFT JOIN yb_clinic_fee_upload f
        ON f.chrg_bchno = c.chrg_bchno
       AND f.tenant_id = c.tenant_id
       AND COALESCE(f.is_deleted, FALSE) = FALSE
       AND f.upload_status IN ('UPLOADED', 'SETTLED')
    GROUP BY c.pre_id
),
calc AS (
    SELECT
        c.pre_id,
        r.retail_sum,
        CASE
            WHEN ul.found THEN ul.upload_sum
            WHEN uf.found THEN uf.upload_sum
            ELSE NULL
        END AS upload_sum
    FROM charge_ids c
    JOIN retail r ON r.pre_id = c.pre_id
    LEFT JOIN upload_log ul ON ul.pre_id = c.pre_id
    LEFT JOIN upload_fee uf ON uf.pre_id = c.pre_id
    WHERE c.ids IS NOT NULL
)
UPDATE yb_clinic_pre_settle p
SET
    retail_medfee_sumamt = c.retail_sum,
    upload_medfee_sumamt = c.upload_sum,
    retail_upload_diff_amt = ROUND(c.retail_sum - c.upload_sum, 2),
    updated_at = COALESCE(p.updated_at, p.created_at)
FROM calc c
WHERE p.id = c.pre_id
  AND c.upload_sum IS NOT NULL
  AND p.retail_upload_diff_amt IS NULL;

-- ========== 住院预结算 ==========
WITH charge_ids AS (
    SELECT
        p.id AS pre_id,
        p.tenant_id,
        p.encounter_id,
        CASE
            WHEN p.charge_item_ids IS NULL OR btrim(p.charge_item_ids) = '' THEN NULL
            WHEN btrim(p.charge_item_ids) LIKE '[%' THEN (
                SELECT array_agg(DISTINCT (elem)::bigint)
                FROM jsonb_array_elements_text(p.charge_item_ids::jsonb) AS t(elem)
                WHERE btrim(elem) <> ''
            )
            ELSE (
                SELECT array_agg(DISTINCT btrim(x)::bigint)
                FROM unnest(string_to_array(p.charge_item_ids, ',')) AS x
                WHERE btrim(x) <> ''
            )
        END AS ids
    FROM yb_inpatient_pre_settle p
    WHERE COALESCE(p.is_deleted, FALSE) = FALSE
      AND p.retail_medfee_sumamt IS NULL
      AND p.upload_medfee_sumamt IS NULL
      AND p.retail_upload_diff_amt IS NULL
),
retail AS (
    SELECT
        c.pre_id,
        ROUND(COALESCE(SUM(ci.total_price), 0), 2) AS retail_sum
    FROM charge_ids c
    LEFT JOIN LATERAL unnest(c.ids) AS u(cid) ON TRUE
    LEFT JOIN adm_charge_item ci ON ci.id = u.cid
    GROUP BY c.pre_id
),
upload_log AS (
    SELECT
        c.pre_id,
        ROUND(COALESCE((
            SELECT SUM((e ->> 'det_item_fee_sumamt')::numeric)
            FROM jsonb_array_elements(
                COALESCE(
                    l.request_json::jsonb -> 'feedetail',
                    l.request_json::jsonb -> 'input' -> 'feedetail',
                    '[]'::jsonb
                )
            ) AS e
        ), 0), 2) AS upload_sum,
        TRUE AS found
    FROM charge_ids c
    JOIN LATERAL (
        SELECT l.request_json
        FROM yb_invoke_log l
        WHERE l.infno = '2301'
          AND COALESCE(l.is_deleted, FALSE) = FALSE
          AND l.encounter_id = c.encounter_id
          AND l.tenant_id = c.tenant_id
          AND l.request_json IS NOT NULL
          AND btrim(l.request_json) <> ''
        ORDER BY
            CASE WHEN COALESCE(l.infcode, '0') IN ('0', '') THEN 0 ELSE 1 END,
            l.created_at DESC
        LIMIT 1
    ) l ON TRUE
),
upload_fee AS (
    SELECT
        c.pre_id,
        ROUND(COALESCE(SUM(f.det_item_fee_sumamt), 0), 2) AS upload_sum,
        COUNT(f.id) > 0 AS found
    FROM charge_ids c
    LEFT JOIN LATERAL unnest(c.ids) AS u(cid) ON TRUE
    LEFT JOIN yb_inpatient_fee_upload f
        ON f.charge_item_id = u.cid
       AND f.tenant_id = c.tenant_id
       AND COALESCE(f.is_deleted, FALSE) = FALSE
       AND f.upload_status IN ('UPLOADED', 'SETTLED')
    GROUP BY c.pre_id
),
calc AS (
    SELECT
        c.pre_id,
        r.retail_sum,
        CASE
            WHEN ul.found THEN ul.upload_sum
            WHEN uf.found THEN uf.upload_sum
            ELSE NULL
        END AS upload_sum
    FROM charge_ids c
    JOIN retail r ON r.pre_id = c.pre_id
    LEFT JOIN upload_log ul ON ul.pre_id = c.pre_id
    LEFT JOIN upload_fee uf ON uf.pre_id = c.pre_id
    WHERE c.ids IS NOT NULL
)
UPDATE yb_inpatient_pre_settle p
SET
    retail_medfee_sumamt = c.retail_sum,
    upload_medfee_sumamt = c.upload_sum,
    retail_upload_diff_amt = ROUND(c.retail_sum - c.upload_sum, 2),
    updated_at = COALESCE(p.updated_at, p.created_at)
FROM calc c
WHERE p.id = c.pre_id
  AND c.upload_sum IS NOT NULL
  AND p.retail_upload_diff_amt IS NULL;

-- ========== 正式结算：自预结算透传 ==========
UPDATE yb_settlement_record sr
SET
    retail_medfee_sumamt = p.retail_medfee_sumamt,
    upload_medfee_sumamt = p.upload_medfee_sumamt,
    retail_upload_diff_amt = p.retail_upload_diff_amt,
    updated_at = COALESCE(sr.updated_at, sr.created_at)
FROM yb_clinic_pre_settle p
WHERE sr.pre_settle_id = p.id
  AND sr.encounter_class = 'AMB'
  AND COALESCE(sr.is_deleted, FALSE) = FALSE
  AND sr.retail_upload_diff_amt IS NULL
  AND p.retail_upload_diff_amt IS NOT NULL;

UPDATE yb_settlement_record sr
SET
    retail_medfee_sumamt = p.retail_medfee_sumamt,
    upload_medfee_sumamt = p.upload_medfee_sumamt,
    retail_upload_diff_amt = p.retail_upload_diff_amt,
    updated_at = COALESCE(sr.updated_at, sr.created_at)
FROM yb_inpatient_pre_settle p
WHERE sr.pre_settle_id = p.id
  AND sr.encounter_class = 'IMP'
  AND COALESCE(sr.is_deleted, FALSE) = FALSE
  AND sr.retail_upload_diff_amt IS NULL
  AND p.retail_upload_diff_amt IS NOT NULL;

-- from former V11__yb_national_catalog_pack_fields.sql (columns in CREATE above; backfill only)
-- 历史回填：仅数组型 raw_json。若运维已预填（存在 min_prcunt），则跳过以免启动重复扫全表。
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM yb_national_catalog_item
        WHERE min_prcunt IS NOT NULL
        LIMIT 1
    ) THEN
        RAISE NOTICE 'yb_national_catalog_item pack fields already populated, skip backfill';
        RETURN;
    END IF;

    UPDATE yb_national_catalog_item
    SET
        min_pac_cnt = NULLIF(btrim(raw_json::json->>41), ''),
        min_pacunt = NULLIF(btrim(raw_json::json->>42), ''),
        min_prepunt = NULLIF(btrim(raw_json::json->>43), ''),
        min_prcunt = NULLIF(btrim(raw_json::json->>48), '')
    WHERE is_deleted = false
      AND raw_json IS NOT NULL
      AND left(btrim(raw_json), 1) = '[';
END $$;

