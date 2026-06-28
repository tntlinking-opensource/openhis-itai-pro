-- mrms_qc_rule（建表 + 种子）
-- Flyway V92

CREATE TABLE IF NOT EXISTS mrms_qc_rule (
    id                  BIGINT       NOT NULL PRIMARY KEY,
    tenant_id           BIGINT       NOT NULL,
    rule_code           VARCHAR(64)  NOT NULL,
    rule_name           VARCHAR(200) NOT NULL,
    severity_code       VARCHAR(16)  NOT NULL,
    category_code       VARCHAR(32)  NOT NULL,
    enabled             BOOLEAN      NOT NULL DEFAULT TRUE,
    expression_json     TEXT,
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP,
    created_by          BIGINT,
    updated_by          BIGINT,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    deleted_by          BIGINT
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_mrms_qc_rule_tenant_code
    ON mrms_qc_rule (tenant_id, rule_code)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE mrms_qc_rule IS '病案质控规则';
COMMENT ON COLUMN mrms_qc_rule.id IS '主键';
COMMENT ON COLUMN mrms_qc_rule.tenant_id IS '租户标识';
COMMENT ON COLUMN mrms_qc_rule.rule_code IS '规则编码';
COMMENT ON COLUMN mrms_qc_rule.rule_name IS '规则名称';
COMMENT ON COLUMN mrms_qc_rule.severity_code IS '严重级别编码';
COMMENT ON COLUMN mrms_qc_rule.category_code IS '规则分类编码';
COMMENT ON COLUMN mrms_qc_rule.enabled IS '是否启用';
COMMENT ON COLUMN mrms_qc_rule.expression_json IS '规则表达式 JSON';
COMMENT ON COLUMN mrms_qc_rule.created_at IS '创建时间';
COMMENT ON COLUMN mrms_qc_rule.updated_at IS '最后更新时间';
COMMENT ON COLUMN mrms_qc_rule.created_by IS '创建人';
COMMENT ON COLUMN mrms_qc_rule.updated_by IS '最后更新人';
COMMENT ON COLUMN mrms_qc_rule.is_deleted IS '软删标记';
COMMENT ON COLUMN mrms_qc_rule.deleted_at IS '删除时间';
COMMENT ON COLUMN mrms_qc_rule.deleted_by IS '删除人';

-- seed: mrms_qc_rule（tenant_id=0 全局默认）
INSERT INTO mrms_qc_rule (id, tenant_id, rule_code, rule_name, severity_code, category_code, enabled, expression_json, created_at, is_deleted)
VALUES
    (880001, 0, 'QC_HP_SUBMIT_REQUIRED', '首页提交必填项', 'block', 'completeness', TRUE, '{"requiredFields":["psn_name","dscg_way_code"]}', NOW(), FALSE),
    (880002, 0, 'QC_HP_DATE_LOGIC', '入出院日期逻辑', 'block', 'consistency', TRUE, '{}', NOW(), FALSE),
    (880003, 0, 'QC_HP_DISCHARGE_MAIN_DIAG', '出院主诊断', 'block', 'completeness', TRUE, '{}', NOW(), FALSE),
    (880004, 0, 'QC_SUBMIT_OVERDUE', '出院后未及时提交', 'warn', 'timeliness', TRUE, '{"maxDaysAfterDischarge":30}', NOW(), FALSE),
    (880005, 0, 'QC_ARCHIVE_OVERDUE', '出院后未及时归档', 'warn', 'timeliness', TRUE, '{"maxDaysAfterDischarge":60}', NOW(), FALSE),
    (880006, 0, 'QC_DOC_REQUIRED', '必写文书齐全', 'block', 'document', TRUE, '{}', NOW(), FALSE)
ON CONFLICT (id) DO NOTHING;
