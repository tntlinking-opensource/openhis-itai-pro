-- Squashed V8 peis_ddl

-- from V78__peis_service_request_ext.sql
-- peis_service_request_ext（建表）
-- Flyway V79 · DM-PEIS-001

CREATE TABLE IF NOT EXISTS "public"."peis_service_request_ext" (
    "service_request_id" int8 NOT NULL,
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
    "exec_status_enum" varchar(64) NOT NULL DEFAULT 'pending',
    "lis_dispatch_enum" varchar(64) NOT NULL DEFAULT 'notSent',
    "pacs_dispatch_enum" varchar(64) NOT NULL DEFAULT 'notSent',
    CONSTRAINT "peis_service_request_ext_pkey" PRIMARY KEY ("service_request_id")
);

COMMENT ON TABLE peis_service_request_ext IS '体检 ServiceRequest 执行/外发扩展（无 barcode，D6）';
COMMENT ON COLUMN peis_service_request_ext.service_request_id IS '服务请求标识（主键）';
COMMENT ON COLUMN peis_service_request_ext.tenant_id IS '租户标识';
COMMENT ON COLUMN peis_service_request_ext.org_id IS '机构标识';
COMMENT ON COLUMN peis_service_request_ext.created_at IS '创建时间';
COMMENT ON COLUMN peis_service_request_ext.updated_at IS '最后更新时间';
COMMENT ON COLUMN peis_service_request_ext.created_by IS '创建人';
COMMENT ON COLUMN peis_service_request_ext.updated_by IS '最后更新人';
COMMENT ON COLUMN peis_service_request_ext.is_deleted IS '软删标记';
COMMENT ON COLUMN peis_service_request_ext.deleted_at IS '删除时间';
COMMENT ON COLUMN peis_service_request_ext.deleted_by IS '删除人';
COMMENT ON COLUMN peis_service_request_ext.version IS '乐观锁版本号';
COMMENT ON COLUMN peis_service_request_ext.exec_status_enum IS '执行状态枚举';
COMMENT ON COLUMN peis_service_request_ext.lis_dispatch_enum IS 'LIS 外发状态枚举';
COMMENT ON COLUMN peis_service_request_ext.pacs_dispatch_enum IS 'PACS 外发状态枚举';

-- from V79__peis_encounter_ext.sql
-- peis_encounter_ext（建表）
-- Flyway V80 · DM-PEIS-007

CREATE TABLE IF NOT EXISTS "public"."peis_encounter_ext" (
    "encounter_id" int8 NOT NULL,
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
    "package_activity_id" int8,
    "package_snapshot_json" text,
    "group_flag" bool NOT NULL DEFAULT false,
    "company_id" int8,
    "contract_id" int8,
    "group_batch_id" int8,
    "booking_appointment_id" int8,
    "bus_no" varchar(255),
    CONSTRAINT "peis_encounter_ext_pkey" PRIMARY KEY ("encounter_id")
);
CREATE INDEX IF NOT EXISTS "idx_peis_enc_ext_bus_no"
    ON "public"."peis_encounter_ext" ("bus_no")
    WHERE "is_deleted" = false;

COMMENT ON TABLE peis_encounter_ext IS 'Encounter(PE) 体检扩展（个检/团检）';
COMMENT ON COLUMN peis_encounter_ext.encounter_id IS '就诊标识（主键）';
COMMENT ON COLUMN peis_encounter_ext.tenant_id IS '租户标识';
COMMENT ON COLUMN peis_encounter_ext.org_id IS '机构标识';
COMMENT ON COLUMN peis_encounter_ext.created_at IS '创建时间';
COMMENT ON COLUMN peis_encounter_ext.updated_at IS '最后更新时间';
COMMENT ON COLUMN peis_encounter_ext.created_by IS '创建人';
COMMENT ON COLUMN peis_encounter_ext.updated_by IS '最后更新人';
COMMENT ON COLUMN peis_encounter_ext.is_deleted IS '软删标记';
COMMENT ON COLUMN peis_encounter_ext.deleted_at IS '删除时间';
COMMENT ON COLUMN peis_encounter_ext.deleted_by IS '删除人';
COMMENT ON COLUMN peis_encounter_ext.version IS '乐观锁版本号';
COMMENT ON COLUMN peis_encounter_ext.package_activity_id IS '套餐活动定义标识';
COMMENT ON COLUMN peis_encounter_ext.package_snapshot_json IS '套餐快照 JSON';
COMMENT ON COLUMN peis_encounter_ext.group_flag IS '是否团检';
COMMENT ON COLUMN peis_encounter_ext.company_id IS '单位标识';
COMMENT ON COLUMN peis_encounter_ext.contract_id IS '合约标识';
COMMENT ON COLUMN peis_encounter_ext.group_batch_id IS '团检批次标识';
COMMENT ON COLUMN peis_encounter_ext.booking_appointment_id IS '预约队列标识';
COMMENT ON COLUMN peis_encounter_ext.bus_no IS '业务编号';

-- from V80__peis_common_result.sql
-- peis_common_result（建表）
-- Flyway V81 · DM-PEIS-003

CREATE TABLE IF NOT EXISTS "public"."peis_common_result" (
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
    "observation_definition_id" int8,
    "dept_org_id" int8,
    "display" varchar(512) NOT NULL,
    "value_string" text,
    "interpretation_code" varchar(64),
    "sort_num" int4 NOT NULL DEFAULT 0,
    "status_enum" varchar(64) NOT NULL DEFAULT 'active',
    CONSTRAINT "peis_common_result_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE peis_common_result IS '分科常见结果模板';
COMMENT ON COLUMN peis_common_result.id IS '主键';
COMMENT ON COLUMN peis_common_result.tenant_id IS '租户标识';
COMMENT ON COLUMN peis_common_result.org_id IS '机构标识';
COMMENT ON COLUMN peis_common_result.created_at IS '创建时间';
COMMENT ON COLUMN peis_common_result.updated_at IS '最后更新时间';
COMMENT ON COLUMN peis_common_result.created_by IS '创建人';
COMMENT ON COLUMN peis_common_result.updated_by IS '最后更新人';
COMMENT ON COLUMN peis_common_result.is_deleted IS '软删标记';
COMMENT ON COLUMN peis_common_result.deleted_at IS '删除时间';
COMMENT ON COLUMN peis_common_result.deleted_by IS '删除人';
COMMENT ON COLUMN peis_common_result.version IS '乐观锁版本号';
COMMENT ON COLUMN peis_common_result.observation_definition_id IS '观察项定义标识';
COMMENT ON COLUMN peis_common_result.dept_org_id IS '科室机构标识';
COMMENT ON COLUMN peis_common_result.display IS '展示文本';
COMMENT ON COLUMN peis_common_result.value_string IS '结果文本';
COMMENT ON COLUMN peis_common_result.interpretation_code IS '解读编码';
COMMENT ON COLUMN peis_common_result.sort_num IS '排序号';
COMMENT ON COLUMN peis_common_result.status_enum IS '状态枚举';

-- from V81__peis_config.sql
-- peis_config（建表）
-- Flyway V82 · DM-PEIS-004

CREATE TABLE IF NOT EXISTS "public"."peis_config" (
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
    "config_key" varchar(255) NOT NULL,
    "config_value" text,
    CONSTRAINT "peis_config_pkey" PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX IF NOT EXISTS "uk_peis_config_tenant_key"
    ON "public"."peis_config" ("tenant_id", "config_key")
    WHERE "is_deleted" = false;

COMMENT ON TABLE peis_config IS 'PEIS 模块 KV 参数（对齐 lis_config）';
COMMENT ON COLUMN peis_config.id IS '主键';
COMMENT ON COLUMN peis_config.tenant_id IS '租户标识';
COMMENT ON COLUMN peis_config.org_id IS '机构标识';
COMMENT ON COLUMN peis_config.created_at IS '创建时间';
COMMENT ON COLUMN peis_config.updated_at IS '最后更新时间';
COMMENT ON COLUMN peis_config.created_by IS '创建人';
COMMENT ON COLUMN peis_config.updated_by IS '最后更新人';
COMMENT ON COLUMN peis_config.is_deleted IS '软删标记';
COMMENT ON COLUMN peis_config.deleted_at IS '删除时间';
COMMENT ON COLUMN peis_config.deleted_by IS '删除人';
COMMENT ON COLUMN peis_config.version IS '乐观锁版本号';
COMMENT ON COLUMN peis_config.config_key IS '配置键';
COMMENT ON COLUMN peis_config.config_value IS '配置值';

-- from V82__cln_diagnostic_report.sql
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

-- from V83__peis_report_grant_ext.sql
-- peis_report_grant_ext（建表）
-- Flyway V84 · DM-PEIS-005

CREATE TABLE IF NOT EXISTS "public"."peis_report_grant_ext" (
    "diagnostic_report_id" int8 NOT NULL,
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
    "grant_status_enum" varchar(64) NOT NULL DEFAULT 'pending',
    "grant_time" timestamptz(6),
    "grant_by" int8,
    "recipient_name" varchar(255),
    CONSTRAINT "peis_report_grant_ext_pkey" PRIMARY KEY ("diagnostic_report_id")
);

COMMENT ON TABLE peis_report_grant_ext IS '体检报告发放/签收扩展（1:1 DiagnosticReport）';
COMMENT ON COLUMN peis_report_grant_ext.diagnostic_report_id IS '总检报告标识（主键）';
COMMENT ON COLUMN peis_report_grant_ext.tenant_id IS '租户标识';
COMMENT ON COLUMN peis_report_grant_ext.org_id IS '机构标识';
COMMENT ON COLUMN peis_report_grant_ext.created_at IS '创建时间';
COMMENT ON COLUMN peis_report_grant_ext.updated_at IS '最后更新时间';
COMMENT ON COLUMN peis_report_grant_ext.created_by IS '创建人';
COMMENT ON COLUMN peis_report_grant_ext.updated_by IS '最后更新人';
COMMENT ON COLUMN peis_report_grant_ext.is_deleted IS '软删标记';
COMMENT ON COLUMN peis_report_grant_ext.deleted_at IS '删除时间';
COMMENT ON COLUMN peis_report_grant_ext.deleted_by IS '删除人';
COMMENT ON COLUMN peis_report_grant_ext.version IS '乐观锁版本号';
COMMENT ON COLUMN peis_report_grant_ext.grant_status_enum IS '发放状态枚举';
COMMENT ON COLUMN peis_report_grant_ext.grant_time IS '发放时间';
COMMENT ON COLUMN peis_report_grant_ext.grant_by IS '发放人标识';
COMMENT ON COLUMN peis_report_grant_ext.recipient_name IS '签收人姓名';

-- from V84__peis_auto_express.sql
-- peis_auto_express（建表）
-- Flyway V85 · DM-PEIS-006

CREATE TABLE IF NOT EXISTS "public"."peis_auto_express" (
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
    "name" varchar(512) NOT NULL,
    "express_script" text NOT NULL,
    "target_condition_def_id" int8,
    "rule_type_code" varchar(64) NOT NULL,
    "status_enum" varchar(64) NOT NULL DEFAULT 'active',
    CONSTRAINT "peis_auto_express_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "idx_peis_auto_express_rule_type"
    ON "public"."peis_auto_express" ("tenant_id", "rule_type_code", "status_enum")
    WHERE "is_deleted" = false;

COMMENT ON TABLE peis_auto_express IS 'PEIS 自动诊断/危急值表达式规则';
COMMENT ON COLUMN peis_auto_express.id IS '主键';
COMMENT ON COLUMN peis_auto_express.tenant_id IS '租户标识';
COMMENT ON COLUMN peis_auto_express.org_id IS '机构标识';
COMMENT ON COLUMN peis_auto_express.created_at IS '创建时间';
COMMENT ON COLUMN peis_auto_express.updated_at IS '最后更新时间';
COMMENT ON COLUMN peis_auto_express.created_by IS '创建人';
COMMENT ON COLUMN peis_auto_express.updated_by IS '最后更新人';
COMMENT ON COLUMN peis_auto_express.is_deleted IS '软删标记';
COMMENT ON COLUMN peis_auto_express.deleted_at IS '删除时间';
COMMENT ON COLUMN peis_auto_express.deleted_by IS '删除人';
COMMENT ON COLUMN peis_auto_express.version IS '乐观锁版本号';
COMMENT ON COLUMN peis_auto_express.name IS '规则名称';
COMMENT ON COLUMN peis_auto_express.express_script IS '表达式脚本';
COMMENT ON COLUMN peis_auto_express.target_condition_def_id IS '目标诊断定义标识';
COMMENT ON COLUMN peis_auto_express.rule_type_code IS '规则类型编码';
COMMENT ON COLUMN peis_auto_express.status_enum IS '状态枚举';
