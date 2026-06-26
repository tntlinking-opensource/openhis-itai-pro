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
