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
