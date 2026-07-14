-- EInvoice Boss Flyway V1 · 表结构（含冲红字段、子表审计列）

-- einv_boss_document
CREATE TABLE einv_boss_document (
    id              BIGINT       NOT NULL PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL,
    invoice_id      BIGINT       NOT NULL,
    payment_id      BIGINT       NOT NULL,
    issue_kind      VARCHAR(10)  NOT NULL,
    bus_no          VARCHAR(50)  NOT NULL,
    bus_type        VARCHAR(10)  NOT NULL,
    total_amt       DECIMAL(14,2) NOT NULL,
    payment_amt     DECIMAL(14,2) NOT NULL,
    amount_status   VARCHAR(20)  NOT NULL DEFAULT 'MATCH',
    boss_bill_no    VARCHAR(50),
    boss_batch_code VARCHAR(50),
    random          VARCHAR(50),
    picture_url     VARCHAR(500),
    picture_net_url VARCHAR(500),
    bill_qr_code    TEXT,
    payload_json    TEXT,
    platform_profile VARCHAR(50) NOT NULL,
    payer_name       VARCHAR(100),
    payer_phone      VARCHAR(20),
    payer_id_card    VARCHAR(30),
    payer_type       VARCHAR(1),
    payer_sex        VARCHAR(4),
    payer_age        VARCHAR(10),
    remark           VARCHAR(200),
    operator_name    VARCHAR(50),
    write_off_status      VARCHAR(20),
    write_off_bill_no     VARCHAR(50),
    write_off_batch_code  VARCHAR(50),
    write_off_at          TIMESTAMP,
    write_off_message     VARCHAR(500),
    is_deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP    NOT NULL,
    created_by       BIGINT,
    updated_at       TIMESTAMP,
    updated_by       BIGINT,
    deleted_at       TIMESTAMP,
    deleted_by       BIGINT,
    CONSTRAINT uk_einv_doc_invoice UNIQUE (invoice_id)
);
CREATE INDEX idx_einv_doc_payment ON einv_boss_document(payment_id);
CREATE INDEX idx_einv_doc_bus_no ON einv_boss_document(bus_no);

-- einv_boss_line
CREATE TABLE einv_boss_line (
    id                   BIGINT        NOT NULL PRIMARY KEY,
    tenant_id            BIGINT        NOT NULL,
    document_id          BIGINT        NOT NULL,
    line_type            VARCHAR(10)   NOT NULL,
    line_no              INT           NOT NULL,
    charge_code          VARCHAR(50)   NOT NULL,
    charge_name          VARCHAR(200)  NOT NULL,
    std_amount           DECIMAL(14,2) NOT NULL,
    amount               DECIMAL(14,2) NOT NULL,
    quantity             DECIMAL(14,4),
    unit                 VARCHAR(20),
    source_charge_item_id BIGINT,
    adjusted             BOOLEAN       NOT NULL DEFAULT FALSE,
    adjust_reason        VARCHAR(500),
    is_deleted           BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMP     NOT NULL,
    created_by           BIGINT,
    updated_at           TIMESTAMP,
    updated_by           BIGINT,
    deleted_at           TIMESTAMP,
    deleted_by           BIGINT,
    CONSTRAINT fk_einv_line_doc FOREIGN KEY (document_id) REFERENCES einv_boss_document(id)
);
CREATE INDEX idx_einv_line_doc ON einv_boss_line(document_id);

-- einv_boss_amount_adjust_log
CREATE TABLE einv_boss_amount_adjust_log (
    id           BIGINT        NOT NULL PRIMARY KEY,
    tenant_id    BIGINT        NOT NULL,
    document_id  BIGINT        NOT NULL,
    operator_id  BIGINT        NOT NULL,
    before_total DECIMAL(14,2) NOT NULL,
    after_total  DECIMAL(14,2) NOT NULL,
    diff_json    TEXT,
    reason       VARCHAR(500)  NOT NULL,
    created_at   TIMESTAMP     NOT NULL
);

-- einv_boss_issue_task
CREATE TABLE einv_boss_issue_task (
    id              BIGINT       NOT NULL PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL,
    invoice_id      BIGINT       NOT NULL,
    document_id     BIGINT       NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    attempt_count   INT          NOT NULL DEFAULT 0,
    next_retry_at   TIMESTAMP,
    last_error_code VARCHAR(50),
    last_error_msg  VARCHAR(1000),
    trigger_source  VARCHAR(20)  NOT NULL,
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP    NOT NULL,
    created_by      BIGINT,
    updated_at      TIMESTAMP,
    updated_by      BIGINT,
    deleted_at      TIMESTAMP,
    deleted_by      BIGINT,
    CONSTRAINT uk_einv_task_invoice_active UNIQUE (invoice_id)
);
CREATE INDEX idx_einv_task_status_retry ON einv_boss_issue_task(status, next_retry_at);

-- einv_boss_invoke_log
CREATE TABLE einv_boss_invoke_log (
    id               BIGINT       NOT NULL PRIMARY KEY,
    tenant_id        BIGINT       NOT NULL,
    task_id          BIGINT,
    service_id       VARCHAR(50)  NOT NULL,
    bus_no           VARCHAR(50),
    result_code      VARCHAR(20),
    duration_ms      INT,
    request_digest   VARCHAR(500),
    response_digest  VARCHAR(500),
    created_at       TIMESTAMP    NOT NULL
);

-- einv_charge_contrast
CREATE TABLE einv_charge_contrast (
    id              BIGINT       NOT NULL PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL,
    org_id          BIGINT,
    internal_code   VARCHAR(50)  NOT NULL,
    boss_charge_code VARCHAR(50) NOT NULL,
    boss_charge_name VARCHAR(200),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP    NOT NULL,
    created_by      BIGINT,
    updated_at      TIMESTAMP,
    updated_by      BIGINT,
    deleted_at      TIMESTAMP,
    deleted_by      BIGINT
);

COMMENT ON TABLE einv_boss_document IS 'einvBoss文档';
COMMENT ON COLUMN einv_boss_document.id IS '主键';
COMMENT ON COLUMN einv_boss_document.tenant_id IS '租户标识';
COMMENT ON COLUMN einv_boss_document.invoice_id IS '发票标识';
COMMENT ON COLUMN einv_boss_document.payment_id IS '支付标识';
COMMENT ON COLUMN einv_boss_document.issue_kind IS '开具类型';
COMMENT ON COLUMN einv_boss_document.bus_no IS '业务编号';
COMMENT ON COLUMN einv_boss_document.bus_type IS 'bus类型';
COMMENT ON COLUMN einv_boss_document.total_amt IS '合计amt';
COMMENT ON COLUMN einv_boss_document.payment_amt IS '支付amt';
COMMENT ON COLUMN einv_boss_document.amount_status IS '金额状态';
COMMENT ON COLUMN einv_boss_document.boss_bill_no IS 'Bossbill编号';
COMMENT ON COLUMN einv_boss_document.boss_batch_code IS 'Bossbatch编码';
COMMENT ON COLUMN einv_boss_document.random IS '随机数';
COMMENT ON COLUMN einv_boss_document.picture_url IS '图片地址';
COMMENT ON COLUMN einv_boss_document.picture_net_url IS '图片外网地址';
COMMENT ON COLUMN einv_boss_document.bill_qr_code IS 'billqr编码';
COMMENT ON COLUMN einv_boss_document.payload_json IS '载荷JSON';
COMMENT ON COLUMN einv_boss_document.platform_profile IS '平台配置';
COMMENT ON COLUMN einv_boss_document.write_off_status IS '冲红状态 SUCCESS/FAILED/SKIPPED/PROCESSING';
COMMENT ON COLUMN einv_boss_document.write_off_bill_no IS '博思红票号码';
COMMENT ON COLUMN einv_boss_document.write_off_batch_code IS '博思红票批次号';
COMMENT ON COLUMN einv_boss_document.write_off_at IS '冲红完成时间';
COMMENT ON COLUMN einv_boss_document.write_off_message IS '冲红失败原因或博思返回摘要';
COMMENT ON COLUMN einv_boss_document.is_deleted IS '软删标记';
COMMENT ON COLUMN einv_boss_document.created_at IS '创建时间';
COMMENT ON COLUMN einv_boss_document.created_by IS '创建人';
COMMENT ON COLUMN einv_boss_document.updated_at IS '最后更新时间';
COMMENT ON COLUMN einv_boss_document.updated_by IS '最后更新人';

COMMENT ON TABLE einv_boss_line IS 'einvBoss行';
COMMENT ON COLUMN einv_boss_line.id IS '主键';
COMMENT ON COLUMN einv_boss_line.tenant_id IS '租户标识';
COMMENT ON COLUMN einv_boss_line.document_id IS '文档标识';
COMMENT ON COLUMN einv_boss_line.line_type IS 'line类型';
COMMENT ON COLUMN einv_boss_line.line_no IS 'line编号';
COMMENT ON COLUMN einv_boss_line.charge_code IS '收费编码';
COMMENT ON COLUMN einv_boss_line.charge_name IS '收费名称';
COMMENT ON COLUMN einv_boss_line.std_amount IS 'std金额';
COMMENT ON COLUMN einv_boss_line.amount IS '金额';
COMMENT ON COLUMN einv_boss_line.quantity IS '数量';
COMMENT ON COLUMN einv_boss_line.unit IS '单元';
COMMENT ON COLUMN einv_boss_line.source_charge_item_id IS '来源收费项标识';
COMMENT ON COLUMN einv_boss_line.adjusted IS '是否已调整';
COMMENT ON COLUMN einv_boss_line.adjust_reason IS 'adjust原因';
COMMENT ON COLUMN einv_boss_line.is_deleted IS '软删标记';
COMMENT ON COLUMN einv_boss_line.created_at IS '创建时间';
COMMENT ON COLUMN einv_boss_line.updated_at IS '最后更新时间';

COMMENT ON TABLE einv_boss_amount_adjust_log IS 'einvBoss金额adjust日志';
COMMENT ON COLUMN einv_boss_amount_adjust_log.id IS '主键';
COMMENT ON COLUMN einv_boss_amount_adjust_log.tenant_id IS '租户标识';
COMMENT ON COLUMN einv_boss_amount_adjust_log.document_id IS '文档标识';
COMMENT ON COLUMN einv_boss_amount_adjust_log.operator_id IS '操作人标识';
COMMENT ON COLUMN einv_boss_amount_adjust_log.before_total IS 'before合计';
COMMENT ON COLUMN einv_boss_amount_adjust_log.after_total IS 'after合计';
COMMENT ON COLUMN einv_boss_amount_adjust_log.diff_json IS '差异JSON';
COMMENT ON COLUMN einv_boss_amount_adjust_log.reason IS '原因';
COMMENT ON COLUMN einv_boss_amount_adjust_log.created_at IS '创建时间';

COMMENT ON TABLE einv_boss_issue_task IS 'einv_boss_issue_task';
COMMENT ON COLUMN einv_boss_issue_task.id IS '主键';
COMMENT ON COLUMN einv_boss_issue_task.tenant_id IS '租户标识';
COMMENT ON COLUMN einv_boss_issue_task.invoice_id IS '发票标识';
COMMENT ON COLUMN einv_boss_issue_task.document_id IS '文档标识';
COMMENT ON COLUMN einv_boss_issue_task.status IS '状态';
COMMENT ON COLUMN einv_boss_issue_task.attempt_count IS '尝试次数';
COMMENT ON COLUMN einv_boss_issue_task.next_retry_at IS 'nextretry时间';
COMMENT ON COLUMN einv_boss_issue_task.last_error_code IS 'last错误编码';
COMMENT ON COLUMN einv_boss_issue_task.last_error_msg IS 'last错误msg';
COMMENT ON COLUMN einv_boss_issue_task.trigger_source IS '触发器来源';
COMMENT ON COLUMN einv_boss_issue_task.is_deleted IS '软删标记';
COMMENT ON COLUMN einv_boss_issue_task.created_at IS '创建时间';
COMMENT ON COLUMN einv_boss_issue_task.updated_at IS '最后更新时间';

COMMENT ON TABLE einv_boss_invoke_log IS 'einvBossinvoke日志';
COMMENT ON COLUMN einv_boss_invoke_log.id IS '主键';
COMMENT ON COLUMN einv_boss_invoke_log.tenant_id IS '租户标识';
COMMENT ON COLUMN einv_boss_invoke_log.task_id IS 'task标识';
COMMENT ON COLUMN einv_boss_invoke_log.service_id IS '服务标识';
COMMENT ON COLUMN einv_boss_invoke_log.bus_no IS '业务编号';
COMMENT ON COLUMN einv_boss_invoke_log.result_code IS 'result编码';
COMMENT ON COLUMN einv_boss_invoke_log.duration_ms IS '耗时ms';
COMMENT ON COLUMN einv_boss_invoke_log.request_digest IS '申请digest';
COMMENT ON COLUMN einv_boss_invoke_log.response_digest IS '响应摘要';
COMMENT ON COLUMN einv_boss_invoke_log.created_at IS '创建时间';

COMMENT ON TABLE einv_charge_contrast IS 'einv收费对照';
COMMENT ON COLUMN einv_charge_contrast.id IS '主键';
COMMENT ON COLUMN einv_charge_contrast.tenant_id IS '租户标识';
COMMENT ON COLUMN einv_charge_contrast.org_id IS '机构标识';
COMMENT ON COLUMN einv_charge_contrast.internal_code IS '内部编码';
COMMENT ON COLUMN einv_charge_contrast.boss_charge_code IS 'Boss收费编码';
COMMENT ON COLUMN einv_charge_contrast.boss_charge_name IS 'Boss收费名称';
COMMENT ON COLUMN einv_charge_contrast.is_deleted IS '软删标记';
COMMENT ON COLUMN einv_charge_contrast.created_at IS '创建时间';
COMMENT ON COLUMN einv_charge_contrast.updated_at IS '最后更新时间';
