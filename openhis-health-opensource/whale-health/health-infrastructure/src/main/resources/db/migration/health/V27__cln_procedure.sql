-- cln_procedure（建表）
-- Flyway V27

CREATE TABLE IF NOT EXISTS "public"."cln_procedure" (
    "id" int8 NOT NULL,
    "tenant_id" int8,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    "version" int4 NOT NULL DEFAULT 0,
    "request_id" int8 NOT NULL,
    "request_table" varchar(255) NOT NULL,
    "status_enum" varchar(255) NOT NULL,
    "status_reason_text" varchar(255),
    "category_enum" varchar(255) NOT NULL,
    "procedure_code" varchar(255),
    "encounter_id" int8 NOT NULL,
    "patient_id" int8 NOT NULL,
    "occurrence_time" timestamptz(6),
    "occurrence_period" varchar(255),
    "occurrence_age" varchar(255),
    "occurrence_text" varchar(255),
    "recorded_time" timestamptz(6),
    "recorder_id" int8,
    "report_flag" bool,
    "reported_patient_id" int8,
    "org_id" int8,
    "location_id" int8,
    "reason_text" varchar(255),
    "body_site_json" varchar(255),
    "body_structure_id" int8,
    "result_text" varchar(255),
    "report_json" varchar(255),
    "complication_json" varchar(255),
    "follow_up_json" varchar(255),
    "group_id" int8,
    "refund_id" int8,
    CONSTRAINT "cln_procedure_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE cln_procedure IS '临床 procedure';
COMMENT ON COLUMN cln_procedure.id IS '主键';
COMMENT ON COLUMN cln_procedure.tenant_id IS '租户标识';
COMMENT ON COLUMN cln_procedure.created_at IS '创建时间';
COMMENT ON COLUMN cln_procedure.updated_at IS '最后更新时间';
COMMENT ON COLUMN cln_procedure.created_by IS '创建人';
COMMENT ON COLUMN cln_procedure.updated_by IS '最后更新人';
COMMENT ON COLUMN cln_procedure.is_deleted IS '软删标记';
COMMENT ON COLUMN cln_procedure.deleted_at IS '删除时间';
COMMENT ON COLUMN cln_procedure.deleted_by IS '删除人';
COMMENT ON COLUMN cln_procedure.version IS '乐观锁版本号';
COMMENT ON COLUMN cln_procedure.request_id IS '申请标识';
COMMENT ON COLUMN cln_procedure.request_table IS '申请table';
COMMENT ON COLUMN cln_procedure.status_enum IS '状态枚举值';
COMMENT ON COLUMN cln_procedure.status_reason_text IS '状态原因文本';
COMMENT ON COLUMN cln_procedure.category_enum IS 'category枚举值';
COMMENT ON COLUMN cln_procedure.procedure_code IS '处置编码';
COMMENT ON COLUMN cln_procedure.encounter_id IS '就诊标识';
COMMENT ON COLUMN cln_procedure.patient_id IS '患者标识';
COMMENT ON COLUMN cln_procedure.occurrence_time IS 'occurrence时间';
COMMENT ON COLUMN cln_procedure.occurrence_period IS '发生时段';
COMMENT ON COLUMN cln_procedure.occurrence_age IS '发生年龄';
COMMENT ON COLUMN cln_procedure.occurrence_text IS 'occurrence文本';
COMMENT ON COLUMN cln_procedure.recorded_time IS '记录时间';
COMMENT ON COLUMN cln_procedure.recorder_id IS '记录人标识';
COMMENT ON COLUMN cln_procedure.report_flag IS '报表标志位';
COMMENT ON COLUMN cln_procedure.reported_patient_id IS 'reported患者标识';
COMMENT ON COLUMN cln_procedure.org_id IS '机构标识';
COMMENT ON COLUMN cln_procedure.location_id IS '位置/科室标识';
COMMENT ON COLUMN cln_procedure.reason_text IS '原因文本';
COMMENT ON COLUMN cln_procedure.body_site_json IS '身体siteJSON';
COMMENT ON COLUMN cln_procedure.body_structure_id IS '身体结构标识';
COMMENT ON COLUMN cln_procedure.result_text IS 'result文本';
COMMENT ON COLUMN cln_procedure.report_json IS '报表JSON';
COMMENT ON COLUMN cln_procedure.complication_json IS '并发症JSON';
COMMENT ON COLUMN cln_procedure.follow_up_json IS '随访JSON';
COMMENT ON COLUMN cln_procedure.group_id IS '分组标识';
COMMENT ON COLUMN cln_procedure.refund_id IS 'refund标识';
