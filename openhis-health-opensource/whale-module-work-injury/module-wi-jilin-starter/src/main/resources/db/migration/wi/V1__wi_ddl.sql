-- V1
-- 工伤就诊锚点（09 §5.1）

CREATE TABLE IF NOT EXISTS yb_wi_encounter_anchor (
    id                      BIGINT       NOT NULL PRIMARY KEY,
    encounter_id            BIGINT       NOT NULL,
    contract_id             BIGINT       NOT NULL,
    psn_no                  VARCHAR(30),
    qualification_id        VARCHAR(64),
    qualification_status    VARCHAR(16)  NOT NULL DEFAULT 'NONE',
    ipt_otp_no              VARCHAR(30),
    injure_affirm_id        VARCHAR(64),
    adm_diag_code           VARCHAR(32),
    med_type                VARCHAR(6)   DEFAULT '12',
    platform_registered     BOOLEAN,
    has_active_fee_upload   BOOLEAN      DEFAULT FALSE,
    setl_id                 VARCHAR(30),

    created_at              TIMESTAMP    NOT NULL,
    updated_at              TIMESTAMP,
    created_by              BIGINT,
    updated_by              BIGINT,
    is_deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMP,
    deleted_by              BIGINT,
    tenant_id               BIGINT       NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_yb_wi_encounter_anchor_encounter
    ON yb_wi_encounter_anchor (encounter_id, tenant_id)
    WHERE is_deleted = FALSE;

-- V2
-- 工伤 invoke 会话（11 §4.1 · P0 必建）

CREATE TABLE IF NOT EXISTS yb_wi_invoke_session (
    id                      BIGINT       NOT NULL PRIMARY KEY,
    session_id              VARCHAR(36)  NOT NULL,
    encounter_id            BIGINT,
    contract_id             BIGINT,
    payment_draft_id        BIGINT,
    phase                   VARCHAR(16)  NOT NULL,
    step_index              INT          NOT NULL DEFAULT 0,
    pending_step_token      VARCHAR(36),
    status                  VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',

    created_at              TIMESTAMP    NOT NULL,
    updated_at              TIMESTAMP,
    created_by              BIGINT,
    updated_by              BIGINT,
    is_deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMP,
    deleted_by              BIGINT,
    tenant_id               BIGINT       NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_yb_wi_invoke_session_id
    ON yb_wi_invoke_session (session_id, tenant_id)
    WHERE is_deleted = FALSE;

-- V3
-- 工伤门诊预结算快照（09 §5.3）

CREATE TABLE IF NOT EXISTS yb_wi_pre_settle (
    id                      BIGINT       NOT NULL PRIMARY KEY,
    contract_id             BIGINT       NOT NULL,
    encounter_id            BIGINT       NOT NULL,
    payment_draft_id        BIGINT,
    chrg_bchno              VARCHAR(30)  NOT NULL,
    contract_bus_no         VARCHAR(64)  NOT NULL,
    mdtrt_id                VARCHAR(30)  NOT NULL,
    psn_no                  VARCHAR(30)  NOT NULL,
    medfee_sumamt           NUMERIC(18, 2),
    fund_pay_sumamt         NUMERIC(18, 2),
    acct_pay                NUMERIC(18, 2),
    psn_cash_pay            NUMERIC(18, 2),
    status                  VARCHAR(16)  NOT NULL,
    snapshot_json           TEXT,

    created_at              TIMESTAMP    NOT NULL,
    updated_at              TIMESTAMP,
    created_by              BIGINT,
    updated_by              BIGINT,
    is_deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMP,
    deleted_by              BIGINT,
    tenant_id               BIGINT       NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_yb_wi_pre_settle_encounter
    ON yb_wi_pre_settle (encounter_id, payment_draft_id, tenant_id)
    WHERE is_deleted = FALSE;
