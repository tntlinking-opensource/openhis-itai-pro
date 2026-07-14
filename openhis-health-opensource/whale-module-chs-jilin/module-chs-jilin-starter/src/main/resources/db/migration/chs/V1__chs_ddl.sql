-- Squashed chs DDL (V1–V12)

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
    mdtrt_cert_no       VARCHAR(64)  NOT NULL,
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

CREATE UNIQUE INDEX IF NOT EXISTS uk_yb_person_cache_cert
    ON yb_person_cache (contract_id, mdtrt_cert_type, mdtrt_cert_no, tenant_id)
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
    mdtrt_cert_no VARCHAR(64),
    insutype VARCHAR(6),
    acct_used_flag VARCHAR(1),
    psn_setlway VARCHAR(6),
    fulamt_ownpay_amt NUMERIC(18, 2),
    overlmt_selfpay NUMERIC(18, 2),
    preselfpay_amt NUMERIC(18, 2),
    inscp_scp_amt NUMERIC(18, 2),

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
    status VARCHAR(16) NOT NULL DEFAULT 'CONFIRMED'
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
    mdtrt_cert_no VARCHAR(64),
    insutype VARCHAR(6),
    acct_used_flag VARCHAR(1),
    psn_setlway VARCHAR(6),
    fulamt_ownpay_amt NUMERIC(18, 2),
    overlmt_selfpay NUMERIC(18, 2),
    preselfpay_amt NUMERIC(18, 2),
    inscp_scp_amt NUMERIC(18, 2),

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

CREATE TABLE IF NOT EXISTS yb_settlement_fund_detail (
    id                      BIGINT       NOT NULL PRIMARY KEY,
    settlement_record_id    BIGINT       NOT NULL,
    fund_pay_type           VARCHAR(6),
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
COMMENT ON COLUMN yb_settlement_fund_detail.fund_pay_type IS '基金支付类型';
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
