-- 工伤对账日结果（settlement↔payment 快照 + 确认账平 + 可选 1320 平台金额）

CREATE TABLE IF NOT EXISTS yb_wi_recon_daily (
    id                      BIGINT         NOT NULL PRIMARY KEY,
    contract_id             BIGINT         NOT NULL,
    contract_bus_no         VARCHAR(64)    NOT NULL,
    recon_day               DATE           NOT NULL,

    medfee_sumamt           NUMERIC(18, 2),
    fund_pay_sumamt         NUMERIC(18, 2),
    psn_cash_pay            NUMERIC(18, 2),
    payment_medfee          NUMERIC(18, 2),
    payment_hifp            NUMERIC(18, 2),
    payment_cash            NUMERIC(18, 2),
    setl_count              INT,
    unbalanced_count        INT,

    local_status            VARCHAR(16)    NOT NULL DEFAULT 'pending',
    confirmed               BOOLEAN        NOT NULL DEFAULT FALSE,

    platform_medfee         NUMERIC(18, 2),
    platform_hifp           NUMERIC(18, 2),
    platform_cash           NUMERIC(18, 2),
    recon_scope             VARCHAR(16),

    created_at              TIMESTAMP      NOT NULL,
    updated_at              TIMESTAMP,
    created_by              BIGINT,
    updated_by              BIGINT,
    is_deleted              BOOLEAN        NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMP,
    deleted_by              BIGINT,
    tenant_id               BIGINT         NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_yb_wi_recon_daily_contract_day
    ON yb_wi_recon_daily (tenant_id, contract_id, recon_day)
    WHERE is_deleted = FALSE;
