-- pacs_report_template
-- Flyway V15

CREATE TABLE IF NOT EXISTS "public"."pacs_report_template" (
    "id" int8 NOT NULL,
    "tenant_id" int8,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    "name" varchar(128) NOT NULL,
    "modality_code" varchar(32),
    "findings" text,
    "conclusion" text,
    "enabled_flag" bool NOT NULL DEFAULT true,
    "sort_order" int4,
    "owner_practitioner_id" int8,
    CONSTRAINT "pacs_report_template_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE pacs_report_template IS '检查报告正文模板';
COMMENT ON COLUMN pacs_report_template.id IS '主键';
COMMENT ON COLUMN pacs_report_template.tenant_id IS '租户标识';
COMMENT ON COLUMN pacs_report_template.created_at IS '创建时间';
COMMENT ON COLUMN pacs_report_template.updated_at IS '最后更新时间';
COMMENT ON COLUMN pacs_report_template.created_by IS '创建人';
COMMENT ON COLUMN pacs_report_template.updated_by IS '最后更新人';
COMMENT ON COLUMN pacs_report_template.is_deleted IS '软删标记';
COMMENT ON COLUMN pacs_report_template.deleted_at IS '删除时间';
COMMENT ON COLUMN pacs_report_template.deleted_by IS '删除人';
COMMENT ON COLUMN pacs_report_template.name IS '模板名称';
COMMENT ON COLUMN pacs_report_template.modality_code IS '模态码；空表示通用';
COMMENT ON COLUMN pacs_report_template.findings IS '所见样本文本';
COMMENT ON COLUMN pacs_report_template.conclusion IS '结论样本文本';
COMMENT ON COLUMN pacs_report_template.enabled_flag IS '是否启用';
COMMENT ON COLUMN pacs_report_template.sort_order IS '列表排序';
COMMENT ON COLUMN pacs_report_template.owner_practitioner_id IS '个人模板归属；空表示科室/院级';

CREATE INDEX IF NOT EXISTS idx_pacs_report_template_tenant_modality
    ON pacs_report_template (tenant_id, modality_code);
