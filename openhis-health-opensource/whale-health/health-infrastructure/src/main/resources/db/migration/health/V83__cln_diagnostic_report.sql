-- cln_diagnostic_report（建表）
-- Flyway V83 · DM-CLN-008

CREATE TABLE IF NOT EXISTS "public"."cln_diagnostic_report" (
    "id" int8 NOT NULL,
    "tenant_id" int8,
    "org_id" int8,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    "version" int4 NOT NULL DEFAULT 0,
    "encounter_id" int8 NOT NULL,
    "patient_id" int8 NOT NULL,
    "status_enum" varchar(64) NOT NULL DEFAULT 'preliminary',
    "summary_text" text,
    "conclusion_text" text,
    "result_refs_json" text,
    "pdf_storage_key" varchar(512),
    "finalized_at" timestamptz(6),
    "finalized_by" int8,
    CONSTRAINT "cln_diagnostic_report_pkey" PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX IF NOT EXISTS "uk_cln_dr_encounter"
    ON "public"."cln_diagnostic_report" ("encounter_id")
    WHERE "is_deleted" = false;

COMMENT ON TABLE cln_diagnostic_report IS 'PEIS/临床总检 DiagnosticReport 事实（非 LIS/PACS 报告）';
COMMENT ON COLUMN cln_diagnostic_report.id IS '主键';
COMMENT ON COLUMN cln_diagnostic_report.tenant_id IS '租户标识';
COMMENT ON COLUMN cln_diagnostic_report.org_id IS '机构标识';
COMMENT ON COLUMN cln_diagnostic_report.created_at IS '创建时间';
COMMENT ON COLUMN cln_diagnostic_report.updated_at IS '最后更新时间';
COMMENT ON COLUMN cln_diagnostic_report.created_by IS '创建人';
COMMENT ON COLUMN cln_diagnostic_report.updated_by IS '最后更新人';
COMMENT ON COLUMN cln_diagnostic_report.is_deleted IS '软删标记';
COMMENT ON COLUMN cln_diagnostic_report.deleted_at IS '删除时间';
COMMENT ON COLUMN cln_diagnostic_report.deleted_by IS '删除人';
COMMENT ON COLUMN cln_diagnostic_report.version IS '乐观锁版本号';
COMMENT ON COLUMN cln_diagnostic_report.encounter_id IS '就诊标识';
COMMENT ON COLUMN cln_diagnostic_report.patient_id IS '患者标识';
COMMENT ON COLUMN cln_diagnostic_report.status_enum IS '状态枚举';
COMMENT ON COLUMN cln_diagnostic_report.summary_text IS '摘要文本';
COMMENT ON COLUMN cln_diagnostic_report.conclusion_text IS '结论文本';
COMMENT ON COLUMN cln_diagnostic_report.result_refs_json IS '结果引用 JSON';
COMMENT ON COLUMN cln_diagnostic_report.pdf_storage_key IS 'PDF 存储键';
COMMENT ON COLUMN cln_diagnostic_report.finalized_at IS '定稿时间';
COMMENT ON COLUMN cln_diagnostic_report.finalized_by IS '定稿人标识';
