-- pacs_diagnostic_report
-- Flyway V13

CREATE TABLE IF NOT EXISTS "public"."pacs_diagnostic_report" (
    "id" int8 NOT NULL,
    "tenant_id" int8,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    "work_item_id" int8 NOT NULL,
    "patient_id" int8 NOT NULL,
    "encounter_id" int8 NOT NULL,
    "organization_id" int8,
    "service_request_id" int8,
    "status_enum" varchar(32) NOT NULL,
    "findings" text,
    "conclusion" text,
    "pdf_storage_key" varchar(512),
    "author_practitioner_id" int8,
    "submitted_at" timestamptz(6),
    "submitted_by" int8,
    "issued_at" timestamptz(6),
    "auditor_practitioner_id" int8,
    "amended_from_id" int8,
    CONSTRAINT "pacs_diagnostic_report_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE pacs_diagnostic_report IS '检查报告聚合根';
COMMENT ON COLUMN pacs_diagnostic_report.work_item_id IS '关联检查工单';
COMMENT ON COLUMN pacs_diagnostic_report.status_enum IS '报告状态 code';
COMMENT ON COLUMN pacs_diagnostic_report.findings IS '所见';
COMMENT ON COLUMN pacs_diagnostic_report.conclusion IS '结论';
COMMENT ON COLUMN pacs_diagnostic_report.pdf_storage_key IS 'PDF 对象存储键';
COMMENT ON COLUMN pacs_diagnostic_report.submitted_at IS '提交待审时间；与 preliminary 组合表示待审';

CREATE UNIQUE INDEX IF NOT EXISTS uk_pacs_dr_tenant_work_item_main
    ON pacs_diagnostic_report (tenant_id, work_item_id)
    WHERE amended_from_id IS NULL AND is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_pacs_dr_work_item_id ON pacs_diagnostic_report (work_item_id);
CREATE INDEX IF NOT EXISTS idx_pacs_dr_patient_id ON pacs_diagnostic_report (patient_id);
CREATE INDEX IF NOT EXISTS idx_pacs_dr_status_enum ON pacs_diagnostic_report (status_enum);
CREATE INDEX IF NOT EXISTS idx_pacs_dr_submitted_at ON pacs_diagnostic_report (submitted_at);
