-- 补齐 BaseAuditedEntity（AuditedEntityBase）软删审计列；V1 已落库的库通过本脚本升级

ALTER TABLE mrms_case_record
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS deleted_by BIGINT;

ALTER TABLE mrms_case_record_homepage
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS deleted_by BIGINT;
