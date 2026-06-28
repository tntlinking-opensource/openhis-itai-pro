-- cln_diagnostic_report_index（建表）
-- Flyway V77 · DM-CLN-006

CREATE TABLE IF NOT EXISTS "public"."cln_diagnostic_report_index" (
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
    "module_enum" varchar(32) NOT NULL,
    "source_report_id" int8 NOT NULL,
    "encounter_id" int8,
    "service_request_id" int8,
    "patient_id" int8 NOT NULL,
    "status_enum" varchar(64) NOT NULL,
    "published_at" timestamptz(6),
    "title" varchar(512),
    "category_code" varchar(64),
    "pdf_storage_key" varchar(512),
    "external_view_url" varchar(1024),
    "amended_from_id" int8,
    "unique_key" varchar(255) NOT NULL,
    CONSTRAINT "cln_diagnostic_report_index_pkey" PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX IF NOT EXISTS "uk_cln_dr_index_unique_key"
    ON "public"."cln_diagnostic_report_index" ("unique_key")
    WHERE "is_deleted" = false;

COMMENT ON TABLE cln_diagnostic_report_index IS '多模块 DiagnosticReport 读侧索引';
COMMENT ON COLUMN cln_diagnostic_report_index.id IS '主键';
COMMENT ON COLUMN cln_diagnostic_report_index.tenant_id IS '租户标识';
COMMENT ON COLUMN cln_diagnostic_report_index.org_id IS '机构标识';
COMMENT ON COLUMN cln_diagnostic_report_index.created_at IS '创建时间';
COMMENT ON COLUMN cln_diagnostic_report_index.updated_at IS '最后更新时间';
COMMENT ON COLUMN cln_diagnostic_report_index.created_by IS '创建人';
COMMENT ON COLUMN cln_diagnostic_report_index.updated_by IS '最后更新人';
COMMENT ON COLUMN cln_diagnostic_report_index.is_deleted IS '软删标记';
COMMENT ON COLUMN cln_diagnostic_report_index.deleted_at IS '删除时间';
COMMENT ON COLUMN cln_diagnostic_report_index.deleted_by IS '删除人';
COMMENT ON COLUMN cln_diagnostic_report_index.version IS '乐观锁版本号';
COMMENT ON COLUMN cln_diagnostic_report_index.module_enum IS '来源模块枚举';
COMMENT ON COLUMN cln_diagnostic_report_index.source_report_id IS '源报告标识';
COMMENT ON COLUMN cln_diagnostic_report_index.encounter_id IS '就诊标识';
COMMENT ON COLUMN cln_diagnostic_report_index.service_request_id IS '服务请求标识';
COMMENT ON COLUMN cln_diagnostic_report_index.patient_id IS '患者标识';
COMMENT ON COLUMN cln_diagnostic_report_index.status_enum IS '状态枚举';
COMMENT ON COLUMN cln_diagnostic_report_index.published_at IS '发布时间';
COMMENT ON COLUMN cln_diagnostic_report_index.title IS '报告标题';
COMMENT ON COLUMN cln_diagnostic_report_index.category_code IS '分类编码';
COMMENT ON COLUMN cln_diagnostic_report_index.pdf_storage_key IS 'PDF 存储键';
COMMENT ON COLUMN cln_diagnostic_report_index.external_view_url IS '外部查看 URL';
COMMENT ON COLUMN cln_diagnostic_report_index.amended_from_id IS '修正来源报告标识';
COMMENT ON COLUMN cln_diagnostic_report_index.unique_key IS '唯一索引键';
