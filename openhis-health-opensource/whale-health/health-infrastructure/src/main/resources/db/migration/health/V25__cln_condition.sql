-- cln_condition（建表）
-- Flyway V25

CREATE TABLE IF NOT EXISTS "public"."cln_condition" (
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
    "verification_status_enum" varchar(255),
    "clinical_status_enum" varchar(255),
    "category_enum" varchar(255),
    "severity_enum" varchar(255),
    "bus_no" varchar(255),
    "body_site_json" varchar(255),
    "body_structure_id" int8,
    "patient_id" int8 NOT NULL,
    "description" varchar(255),
    "onset_datetime" timestamptz(6),
    "onset_age" int4,
    "onset_text" varchar(255),
    "abatement_datetime" timestamptz(6),
    "abatement_age" int4,
    "abatement_string" varchar(255),
    "recorded_datetime" timestamptz(6),
    "recorder_id" int8,
    "stage_summary_text" varchar(255),
    "evidence" varchar(255),
    "yb_no" varchar(255),
    "definition_id" int8,
    "tcm_flag" bool,
    "encounter_id" int8,
    CONSTRAINT "cln_condition_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE cln_condition IS '临床 condition';
COMMENT ON COLUMN cln_condition.id IS '主键';
COMMENT ON COLUMN cln_condition.tenant_id IS '租户标识';
COMMENT ON COLUMN cln_condition.created_at IS '创建时间';
COMMENT ON COLUMN cln_condition.updated_at IS '最后更新时间';
COMMENT ON COLUMN cln_condition.created_by IS '创建人';
COMMENT ON COLUMN cln_condition.updated_by IS '最后更新人';
COMMENT ON COLUMN cln_condition.is_deleted IS '软删标记';
COMMENT ON COLUMN cln_condition.deleted_at IS '删除时间';
COMMENT ON COLUMN cln_condition.deleted_by IS '删除人';
COMMENT ON COLUMN cln_condition.version IS '乐观锁版本号';
COMMENT ON COLUMN cln_condition.verification_status_enum IS 'verification状态枚举值';
COMMENT ON COLUMN cln_condition.clinical_status_enum IS 'clinical状态枚举值';
COMMENT ON COLUMN cln_condition.category_enum IS 'category枚举值';
COMMENT ON COLUMN cln_condition.severity_enum IS 'severity枚举值';
COMMENT ON COLUMN cln_condition.bus_no IS '业务编号';
COMMENT ON COLUMN cln_condition.body_site_json IS '身体siteJSON';
COMMENT ON COLUMN cln_condition.body_structure_id IS '身体结构标识';
COMMENT ON COLUMN cln_condition.patient_id IS '患者标识';
COMMENT ON COLUMN cln_condition.description IS '描述';
COMMENT ON COLUMN cln_condition.onset_datetime IS '发病时间';
COMMENT ON COLUMN cln_condition.onset_age IS '发病年龄';
COMMENT ON COLUMN cln_condition.onset_text IS 'onset文本';
COMMENT ON COLUMN cln_condition.abatement_datetime IS '缓解时间';
COMMENT ON COLUMN cln_condition.abatement_age IS '缓解年龄';
COMMENT ON COLUMN cln_condition.abatement_string IS '缓解描述';
COMMENT ON COLUMN cln_condition.recorded_datetime IS '记录datetime';
COMMENT ON COLUMN cln_condition.recorder_id IS '记录人标识';
COMMENT ON COLUMN cln_condition.stage_summary_text IS 'stagesummary文本';
COMMENT ON COLUMN cln_condition.evidence IS '临床证据';
COMMENT ON COLUMN cln_condition.yb_no IS '医保编号';
COMMENT ON COLUMN cln_condition.definition_id IS '定义标识';
COMMENT ON COLUMN cln_condition.tcm_flag IS 'tcm标志位';
COMMENT ON COLUMN cln_condition.encounter_id IS '诊断记录聚合根';
