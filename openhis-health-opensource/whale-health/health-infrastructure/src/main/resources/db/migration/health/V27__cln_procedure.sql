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
COMMENT ON COLUMN cln_procedure.request_id IS 'request id';
COMMENT ON COLUMN cln_procedure.request_table IS 'request table';
COMMENT ON COLUMN cln_procedure.status_enum IS 'status enum';
COMMENT ON COLUMN cln_procedure.status_reason_text IS 'status reason text';
COMMENT ON COLUMN cln_procedure.category_enum IS 'category enum';
COMMENT ON COLUMN cln_procedure.procedure_code IS 'procedure code';
COMMENT ON COLUMN cln_procedure.encounter_id IS 'encounter id';
COMMENT ON COLUMN cln_procedure.patient_id IS 'patient id';
COMMENT ON COLUMN cln_procedure.occurrence_time IS 'occurrence time';
COMMENT ON COLUMN cln_procedure.occurrence_period IS 'occurrence period';
COMMENT ON COLUMN cln_procedure.occurrence_age IS 'occurrence age';
COMMENT ON COLUMN cln_procedure.occurrence_text IS 'occurrence text';
COMMENT ON COLUMN cln_procedure.recorded_time IS 'recorded time';
COMMENT ON COLUMN cln_procedure.recorder_id IS 'recorder id';
COMMENT ON COLUMN cln_procedure.report_flag IS 'report flag';
COMMENT ON COLUMN cln_procedure.reported_patient_id IS 'reported patient id';
COMMENT ON COLUMN cln_procedure.org_id IS 'org id';
COMMENT ON COLUMN cln_procedure.location_id IS 'location id';
COMMENT ON COLUMN cln_procedure.reason_text IS 'reason text';
COMMENT ON COLUMN cln_procedure.body_site_json IS 'body site json';
COMMENT ON COLUMN cln_procedure.body_structure_id IS 'body structure id';
COMMENT ON COLUMN cln_procedure.result_text IS 'result text';
COMMENT ON COLUMN cln_procedure.report_json IS 'report json';
COMMENT ON COLUMN cln_procedure.complication_json IS 'complication json';
COMMENT ON COLUMN cln_procedure.follow_up_json IS 'follow up json';
COMMENT ON COLUMN cln_procedure.group_id IS 'group id';
COMMENT ON COLUMN cln_procedure.refund_id IS 'refund id';

