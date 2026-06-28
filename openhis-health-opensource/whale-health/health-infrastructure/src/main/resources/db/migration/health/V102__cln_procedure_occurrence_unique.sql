-- GAP-EXE-002：住院同一 planTime 并发双执行防护（部分唯一索引 + 计划点占用释放标记）

ALTER TABLE cln_procedure
    ADD COLUMN IF NOT EXISTS occurrence_slot_active BOOLEAN NOT NULL DEFAULT true;

COMMENT ON COLUMN cln_procedure.occurrence_slot_active IS '计划点占用标记；CANCEL 对冲后释放，允许同 occurrence 再执行';

CREATE UNIQUE INDEX IF NOT EXISTS uk_cln_procedure_request_occurrence_terminal
    ON cln_procedure (request_id, request_table, occurrence_time)
    WHERE is_deleted = false
      AND occurrence_slot_active = true
      AND status_enum IN ('COMPLETED', 'STOPPED');

COMMENT ON INDEX uk_cln_procedure_request_occurrence_terminal IS '同请求锚点+occurrence 仅允许一条有效终态占用（COMPLETED/STOPPED）';
