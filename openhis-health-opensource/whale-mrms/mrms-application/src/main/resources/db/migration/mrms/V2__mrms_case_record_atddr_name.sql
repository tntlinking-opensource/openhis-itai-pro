-- 补齐 mrms_case_record 列表冗余字段 atddr_name（与 CaseRecord 实体对齐）
-- Flyway history: flyway_schema_history_mrms

ALTER TABLE mrms_case_record ADD COLUMN IF NOT EXISTS atddr_name VARCHAR(50);

COMMENT ON COLUMN mrms_case_record.atddr_name IS 'atddr名称';
