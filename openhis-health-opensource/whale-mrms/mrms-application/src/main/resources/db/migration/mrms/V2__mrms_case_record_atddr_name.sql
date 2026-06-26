-- 补齐 mrms_case_record 列表冗余字段 atddr_name（与 CaseRecord 实体对齐）
-- Flyway history: flyway_schema_history_mrms

ALTER TABLE mrms_case_record ADD COLUMN IF NOT EXISTS atddr_name VARCHAR(50);

COMMENT ON COLUMN mrms_case_record.atddr_name IS 'atddr名称';

CREATE INDEX IF NOT EXISTS idx_mrms_case_list_atddr
    ON mrms_case_record (tenant_id, atddr_practitioner_id, dscg_time)
    WHERE is_deleted = FALSE;
