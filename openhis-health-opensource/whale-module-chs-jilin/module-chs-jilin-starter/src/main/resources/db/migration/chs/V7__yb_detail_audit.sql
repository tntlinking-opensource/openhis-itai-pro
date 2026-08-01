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
