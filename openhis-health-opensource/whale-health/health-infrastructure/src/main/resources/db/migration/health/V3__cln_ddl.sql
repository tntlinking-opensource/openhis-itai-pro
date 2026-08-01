-- Squashed V3 cln_ddl

-- from V24__cln_allergy_intolerance.sql
-- cln_allergy_intolerance（建表）
-- Flyway V24

CREATE TABLE IF NOT EXISTS "public"."cln_allergy_intolerance" (
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
    "encounter_id" int8 NOT NULL,
    "patient_id" int8 NOT NULL,
    "medication_id" int8,
    "lot_number" varchar(255),
    "clinical_status_enum" varchar(255) NOT NULL,
    "verification_status_enum" varchar(255) NOT NULL,
    "type_enum" varchar(255),
    "category_code" varchar(255),
    "criticality_enum" varchar(255),
    "code" varchar(255),
    "description" varchar(255),
    "severity_enum" varchar(255),
    "onset_date_time" timestamptz(6),
    "recorded_date" timestamptz(6),
    "last_reaction_occurrence" timestamptz(6),
    "exposure_route" varchar(255),
    "practitioner_id" int8,
    "check_practitioner_id" int8,
    "note" varchar(255),
    CONSTRAINT "cln_allergy_intolerance_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE cln_allergy_intolerance IS '临床 allergy intolerance';
COMMENT ON COLUMN cln_allergy_intolerance.id IS '主键';
COMMENT ON COLUMN cln_allergy_intolerance.tenant_id IS '租户标识';
COMMENT ON COLUMN cln_allergy_intolerance.created_at IS '创建时间';
COMMENT ON COLUMN cln_allergy_intolerance.updated_at IS '最后更新时间';
COMMENT ON COLUMN cln_allergy_intolerance.created_by IS '创建人';
COMMENT ON COLUMN cln_allergy_intolerance.updated_by IS '最后更新人';
COMMENT ON COLUMN cln_allergy_intolerance.is_deleted IS '软删标记';
COMMENT ON COLUMN cln_allergy_intolerance.deleted_at IS '删除时间';
COMMENT ON COLUMN cln_allergy_intolerance.deleted_by IS '删除人';
COMMENT ON COLUMN cln_allergy_intolerance.version IS '乐观锁版本号';
COMMENT ON COLUMN cln_allergy_intolerance.request_id IS '申请标识';
COMMENT ON COLUMN cln_allergy_intolerance.encounter_id IS '就诊标识';
COMMENT ON COLUMN cln_allergy_intolerance.patient_id IS '患者标识';
COMMENT ON COLUMN cln_allergy_intolerance.medication_id IS '药品标识';
COMMENT ON COLUMN cln_allergy_intolerance.lot_number IS '批号';
COMMENT ON COLUMN cln_allergy_intolerance.clinical_status_enum IS 'clinical状态枚举值';
COMMENT ON COLUMN cln_allergy_intolerance.verification_status_enum IS 'verification状态枚举值';
COMMENT ON COLUMN cln_allergy_intolerance.type_enum IS '类型枚举值';
COMMENT ON COLUMN cln_allergy_intolerance.category_code IS 'category编码';
COMMENT ON COLUMN cln_allergy_intolerance.criticality_enum IS 'criticality枚举值';
COMMENT ON COLUMN cln_allergy_intolerance.code IS '编码';
COMMENT ON COLUMN cln_allergy_intolerance.description IS '描述';
COMMENT ON COLUMN cln_allergy_intolerance.severity_enum IS 'severity枚举值';
COMMENT ON COLUMN cln_allergy_intolerance.onset_date_time IS 'onset日期时间';
COMMENT ON COLUMN cln_allergy_intolerance.recorded_date IS '记录日期';
COMMENT ON COLUMN cln_allergy_intolerance.last_reaction_occurrence IS '末次反应发生时间';
COMMENT ON COLUMN cln_allergy_intolerance.exposure_route IS '暴露途径';
COMMENT ON COLUMN cln_allergy_intolerance.practitioner_id IS '医护人员标识';
COMMENT ON COLUMN cln_allergy_intolerance.check_practitioner_id IS 'check医护人员标识';
COMMENT ON COLUMN cln_allergy_intolerance.note IS '备注说明';

-- from V25__cln_condition.sql
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

-- from V26__cln_condition_definition.sql
-- cln_condition_definition（建表 + 种子数据）
-- Flyway V26

CREATE TABLE IF NOT EXISTS "public"."cln_condition_definition" (
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
    "source_enum" varchar(255) NOT NULL,
    "condition_code" varchar(255) NOT NULL,
    "name" varchar(255) NOT NULL,
    "py_str" varchar(255),
    "wb_str" varchar(255),
    "type_code" varchar(255),
    "description" varchar(255),
    "yb_flag" bool,
    "yb_no" varchar(255),
    "yb_match_flag" bool,
    "clinic_code" varchar(255),
    "status_enum" varchar(255) NOT NULL,
    CONSTRAINT "cln_condition_definition_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE cln_condition_definition IS '临床 condition definition';
COMMENT ON COLUMN cln_condition_definition.id IS '主键';
COMMENT ON COLUMN cln_condition_definition.tenant_id IS '租户标识';
COMMENT ON COLUMN cln_condition_definition.created_at IS '创建时间';
COMMENT ON COLUMN cln_condition_definition.updated_at IS '最后更新时间';
COMMENT ON COLUMN cln_condition_definition.created_by IS '创建人';
COMMENT ON COLUMN cln_condition_definition.updated_by IS '最后更新人';
COMMENT ON COLUMN cln_condition_definition.is_deleted IS '软删标记';
COMMENT ON COLUMN cln_condition_definition.deleted_at IS '删除时间';
COMMENT ON COLUMN cln_condition_definition.deleted_by IS '删除人';
COMMENT ON COLUMN cln_condition_definition.version IS '乐观锁版本号';
COMMENT ON COLUMN cln_condition_definition.source_enum IS '来源枚举值';
COMMENT ON COLUMN cln_condition_definition.condition_code IS '病情编码';
COMMENT ON COLUMN cln_condition_definition.name IS '名称';
COMMENT ON COLUMN cln_condition_definition.py_str IS '拼音码';
COMMENT ON COLUMN cln_condition_definition.wb_str IS '五笔码';
COMMENT ON COLUMN cln_condition_definition.type_code IS '类型编码';
COMMENT ON COLUMN cln_condition_definition.description IS '描述';
COMMENT ON COLUMN cln_condition_definition.yb_flag IS '医保标志位';
COMMENT ON COLUMN cln_condition_definition.yb_no IS '医保编号';
COMMENT ON COLUMN cln_condition_definition.yb_match_flag IS '医保match标志位';
COMMENT ON COLUMN cln_condition_definition.clinic_code IS '国家临床版诊断编码（国临码）';
COMMENT ON COLUMN cln_condition_definition.status_enum IS '状态枚举值';

-- seed: cln_condition_definition

-- cln_procedure_definition（手术操作定义目录）
CREATE TABLE IF NOT EXISTS "public"."cln_procedure_definition" (
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
    "source_enum" varchar(255) NOT NULL,
    "procedure_code" varchar(255) NOT NULL,
    "name" varchar(255) NOT NULL,
    "py_str" varchar(255),
    "wb_str" varchar(255),
    "type_code" varchar(255),
    "description" varchar(255),
    "yb_flag" bool,
    "yb_no" varchar(255),
    "yb_match_flag" bool,
    "clinic_code" varchar(255),
    "status_enum" varchar(255) NOT NULL,
    CONSTRAINT "cln_procedure_definition_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE cln_procedure_definition IS '手术操作定义目录（国临/医保对照）';
COMMENT ON COLUMN cln_procedure_definition.id IS '主键';
COMMENT ON COLUMN cln_procedure_definition.tenant_id IS '租户标识';
COMMENT ON COLUMN cln_procedure_definition.created_at IS '创建时间';
COMMENT ON COLUMN cln_procedure_definition.updated_at IS '最后更新时间';
COMMENT ON COLUMN cln_procedure_definition.created_by IS '创建人';
COMMENT ON COLUMN cln_procedure_definition.updated_by IS '最后更新人';
COMMENT ON COLUMN cln_procedure_definition.is_deleted IS '软删标记';
COMMENT ON COLUMN cln_procedure_definition.deleted_at IS '删除时间';
COMMENT ON COLUMN cln_procedure_definition.deleted_by IS '删除人';
COMMENT ON COLUMN cln_procedure_definition.version IS '乐观锁版本号';
COMMENT ON COLUMN cln_procedure_definition.source_enum IS '目录来源分类';
COMMENT ON COLUMN cln_procedure_definition.procedure_code IS '手术操作编码';
COMMENT ON COLUMN cln_procedure_definition.name IS '名称';
COMMENT ON COLUMN cln_procedure_definition.py_str IS '拼音码';
COMMENT ON COLUMN cln_procedure_definition.wb_str IS '五笔码';
COMMENT ON COLUMN cln_procedure_definition.type_code IS '类别/类型编码';
COMMENT ON COLUMN cln_procedure_definition.description IS '描述';
COMMENT ON COLUMN cln_procedure_definition.yb_flag IS '医保标志位';
COMMENT ON COLUMN cln_procedure_definition.yb_no IS '医保手术操作编码';
COMMENT ON COLUMN cln_procedure_definition.yb_match_flag IS '医保对码标记';
COMMENT ON COLUMN cln_procedure_definition.clinic_code IS '国家临床版手术编码（国临码）';
COMMENT ON COLUMN cln_procedure_definition.status_enum IS '发布状态';

CREATE INDEX IF NOT EXISTS idx_cln_proc_def_source_code
    ON cln_procedure_definition (source_enum, procedure_code);
CREATE INDEX IF NOT EXISTS idx_cln_proc_def_clinic_code
    ON cln_procedure_definition (clinic_code);

-- from V27__cln_procedure.sql
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
    "occurrence_slot_active" bool NOT NULL DEFAULT true,
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
COMMENT ON COLUMN cln_procedure.occurrence_slot_active IS '计划点占用标记；CANCEL 对冲后释放，允许同 occurrence 再执行';

CREATE UNIQUE INDEX IF NOT EXISTS uk_cln_procedure_request_occurrence_terminal
    ON cln_procedure (request_id, request_table, occurrence_time)
    WHERE is_deleted = false
      AND occurrence_slot_active = true
      AND status_enum IN ('COMPLETED', 'STOPPED');

COMMENT ON INDEX uk_cln_procedure_request_occurrence_terminal IS '同请求锚点+occurrence 仅允许一条有效终态占用（COMPLETED/STOPPED）';

-- from V28__cln_procedure_performer.sql
-- cln_procedure_performer（建表）
-- Flyway V28

CREATE TABLE IF NOT EXISTS "public"."cln_procedure_performer" (
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
    "procedure_id" int8 NOT NULL,
    "function_code" varchar(255),
    "start_time" timestamptz(6) NOT NULL,
    "end_time" timestamptz(6),
    "practitioner_id" int8 NOT NULL,
    "org_from_id" varchar(255),
    CONSTRAINT "cln_procedure_performer_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE cln_procedure_performer IS '临床 procedure performer';
COMMENT ON COLUMN cln_procedure_performer.id IS '主键';
COMMENT ON COLUMN cln_procedure_performer.tenant_id IS '租户标识';
COMMENT ON COLUMN cln_procedure_performer.created_at IS '创建时间';
COMMENT ON COLUMN cln_procedure_performer.updated_at IS '最后更新时间';
COMMENT ON COLUMN cln_procedure_performer.created_by IS '创建人';
COMMENT ON COLUMN cln_procedure_performer.updated_by IS '最后更新人';
COMMENT ON COLUMN cln_procedure_performer.is_deleted IS '软删标记';
COMMENT ON COLUMN cln_procedure_performer.deleted_at IS '删除时间';
COMMENT ON COLUMN cln_procedure_performer.deleted_by IS '删除人';
COMMENT ON COLUMN cln_procedure_performer.version IS '乐观锁版本号';
COMMENT ON COLUMN cln_procedure_performer.procedure_id IS '处置标识';
COMMENT ON COLUMN cln_procedure_performer.function_code IS 'function编码';
COMMENT ON COLUMN cln_procedure_performer.start_time IS '开始时间';
COMMENT ON COLUMN cln_procedure_performer.end_time IS '结束时间';
COMMENT ON COLUMN cln_procedure_performer.practitioner_id IS '医护人员标识';
COMMENT ON COLUMN cln_procedure_performer.org_from_id IS 'orgfrom标识';

-- from V41__dia_body_structure.sql
-- dia_body_structure（建表）
-- Flyway V42

CREATE TABLE IF NOT EXISTS "public"."dia_body_structure" (
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
    "bus_no" varchar(255) NOT NULL,
    "name" varchar(255) NOT NULL,
    "status_enum" varchar(255) NOT NULL,
    "py_str" varchar(255),
    "wb_str" varchar(255),
    "parent_id" int8,
    CONSTRAINT "dia_body_structure_pkey" PRIMARY KEY ("id")
);

-- ----- seed sync from DB (dia_body_structure, 3 rows) -----
COMMENT ON TABLE dia_body_structure IS '诊断 body structure';
COMMENT ON COLUMN dia_body_structure.id IS '主键';
COMMENT ON COLUMN dia_body_structure.tenant_id IS '租户标识';
COMMENT ON COLUMN dia_body_structure.created_at IS '创建时间';
COMMENT ON COLUMN dia_body_structure.updated_at IS '最后更新时间';
COMMENT ON COLUMN dia_body_structure.created_by IS '创建人';
COMMENT ON COLUMN dia_body_structure.updated_by IS '最后更新人';
COMMENT ON COLUMN dia_body_structure.is_deleted IS '软删标记';
COMMENT ON COLUMN dia_body_structure.deleted_at IS '删除时间';
COMMENT ON COLUMN dia_body_structure.deleted_by IS '删除人';
COMMENT ON COLUMN dia_body_structure.version IS '乐观锁版本号';
COMMENT ON COLUMN dia_body_structure.bus_no IS '业务编号';
COMMENT ON COLUMN dia_body_structure.name IS '名称';
COMMENT ON COLUMN dia_body_structure.status_enum IS '状态枚举值';
COMMENT ON COLUMN dia_body_structure.py_str IS '拼音码';
COMMENT ON COLUMN dia_body_structure.wb_str IS '五笔码';
COMMENT ON COLUMN dia_body_structure.parent_id IS 'parent标识';

-- from V69__dia_vital_sign.sql
-- dia_vital_sign 主从结构（DM-DIA-002 v2.1 最终态；术语种子见 V51/V52）

CREATE TABLE IF NOT EXISTS "public"."dia_vital_sign" (
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
    "patient_id" int8 NOT NULL,
    "encounter_id" int8 NOT NULL,
    "recorded_at" timestamptz(6) NOT NULL,
    "type_enum" varchar(50) NOT NULL,
    "bus_no" varchar(100) NOT NULL,
    "location_id" int8,
    "baby_no" int4,
    "recorder_id" int8,
    "remark" varchar(500),
    CONSTRAINT "dia_vital_sign_pkey" PRIMARY KEY ("id")
);

CREATE TABLE IF NOT EXISTS "public"."dia_vital_sign_detail" (
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
    "vital_sign_id" int8 NOT NULL,
    "term_code" varchar(100) NOT NULL,
    "item_code" varchar(100) NOT NULL,
    "item_value" varchar(500) NOT NULL,
    "units" varchar(100),
    "remark" varchar(500),
    CONSTRAINT "dia_vital_sign_detail_pkey" PRIMARY KEY ("id")
);

CREATE INDEX IF NOT EXISTS "idx_dia_vital_sign_encounter_recorded"
    ON "public"."dia_vital_sign" ("encounter_id", "recorded_at")
    WHERE "is_deleted" = false;

CREATE INDEX IF NOT EXISTS "idx_dia_vital_sign_encounter_type_recorded"
    ON "public"."dia_vital_sign" ("encounter_id", "type_enum", "recorded_at")
    WHERE "is_deleted" = false;

CREATE INDEX IF NOT EXISTS "idx_dia_vital_sign_patient_recorded"
    ON "public"."dia_vital_sign" ("patient_id", "recorded_at")
    WHERE "is_deleted" = false;

CREATE UNIQUE INDEX IF NOT EXISTS "uk_dia_vital_sign_bus_no"
    ON "public"."dia_vital_sign" ("tenant_id", "bus_no")
    WHERE "is_deleted" = false;

CREATE INDEX IF NOT EXISTS "idx_dia_vital_sign_detail_vital_sign_id"
    ON "public"."dia_vital_sign_detail" ("vital_sign_id")
    WHERE "is_deleted" = false;

CREATE UNIQUE INDEX IF NOT EXISTS "uk_dia_vital_sign_detail_natural_key"
    ON "public"."dia_vital_sign_detail" ("tenant_id", "vital_sign_id", "term_code", "item_code")
    WHERE "is_deleted" = false;
COMMENT ON TABLE dia_vital_sign IS '生命体征/护理测量单据主表（DM-DIA-002 v2.1）';
COMMENT ON COLUMN dia_vital_sign.id IS '主键';
COMMENT ON COLUMN dia_vital_sign.tenant_id IS '租户标识';
COMMENT ON COLUMN dia_vital_sign.created_at IS '创建时间';
COMMENT ON COLUMN dia_vital_sign.updated_at IS '最后更新时间';
COMMENT ON COLUMN dia_vital_sign.created_by IS '创建人';
COMMENT ON COLUMN dia_vital_sign.updated_by IS '最后更新人';
COMMENT ON COLUMN dia_vital_sign.is_deleted IS '软删标记';
COMMENT ON COLUMN dia_vital_sign.deleted_at IS '删除时间';
COMMENT ON COLUMN dia_vital_sign.deleted_by IS '删除人';
COMMENT ON COLUMN dia_vital_sign.version IS '乐观锁版本号';
COMMENT ON COLUMN dia_vital_sign.patient_id IS '患者标识';
COMMENT ON COLUMN dia_vital_sign.encounter_id IS '就诊标识';
COMMENT ON COLUMN dia_vital_sign.recorded_at IS '记录时间';
COMMENT ON COLUMN dia_vital_sign.type_enum IS '单据类型：TEMPERATURE_SHEET / NURSING_RECORD';
COMMENT ON COLUMN dia_vital_sign.bus_no IS '业务单号，租户内唯一';
COMMENT ON COLUMN dia_vital_sign.location_id IS '位置/科室标识';
COMMENT ON COLUMN dia_vital_sign.baby_no IS '婴儿编号';
COMMENT ON COLUMN dia_vital_sign.recorder_id IS '记录人标识';
COMMENT ON COLUMN dia_vital_sign.remark IS '备注';

COMMENT ON TABLE dia_vital_sign_detail IS '生命体征/护理测量明细（通用字典项行）';
COMMENT ON COLUMN dia_vital_sign_detail.id IS '主键';
COMMENT ON COLUMN dia_vital_sign_detail.tenant_id IS '租户标识';
COMMENT ON COLUMN dia_vital_sign_detail.created_at IS '创建时间';
COMMENT ON COLUMN dia_vital_sign_detail.updated_at IS '最后更新时间';
COMMENT ON COLUMN dia_vital_sign_detail.created_by IS '创建人';
COMMENT ON COLUMN dia_vital_sign_detail.updated_by IS '最后更新人';
COMMENT ON COLUMN dia_vital_sign_detail.is_deleted IS '软删标记';
COMMENT ON COLUMN dia_vital_sign_detail.deleted_at IS '删除时间';
COMMENT ON COLUMN dia_vital_sign_detail.deleted_by IS '删除人';
COMMENT ON COLUMN dia_vital_sign_detail.version IS '乐观锁版本号';
COMMENT ON COLUMN dia_vital_sign_detail.vital_sign_id IS '生命体征体征标识';
COMMENT ON COLUMN dia_vital_sign_detail.term_code IS '术语体系 code（如 vital_signs_code）';
COMMENT ON COLUMN dia_vital_sign_detail.item_code IS '字典项 code';
COMMENT ON COLUMN dia_vital_sign_detail.item_value IS '字典项值';
COMMENT ON COLUMN dia_vital_sign_detail.units IS '单位';
COMMENT ON COLUMN dia_vital_sign_detail.remark IS '明细备注';

-- from V74__cln_observation_definition.sql
-- cln_observation_definition（建表）
-- Flyway V75 · DM-CLN-005

CREATE TABLE IF NOT EXISTS "public"."cln_observation_definition" (
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
    "code" varchar(255) NOT NULL,
    "display" varchar(512) NOT NULL,
    "value_type_enum" varchar(64) NOT NULL,
    "unit_code" varchar(64),
    "category_code" varchar(64) NOT NULL,
    "status_enum" varchar(64) NOT NULL DEFAULT 'active',
    CONSTRAINT "cln_observation_definition_pkey" PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX IF NOT EXISTS "uk_cln_obs_def_tenant_code"
    ON "public"."cln_observation_definition" ("tenant_id", "code")
    WHERE "is_deleted" = false;

COMMENT ON TABLE cln_observation_definition IS '临床观察项目录（体检小项/分科结论 OD）';
COMMENT ON COLUMN cln_observation_definition.id IS '主键';
COMMENT ON COLUMN cln_observation_definition.tenant_id IS '租户标识';
COMMENT ON COLUMN cln_observation_definition.org_id IS '机构标识';
COMMENT ON COLUMN cln_observation_definition.created_at IS '创建时间';
COMMENT ON COLUMN cln_observation_definition.updated_at IS '最后更新时间';
COMMENT ON COLUMN cln_observation_definition.created_by IS '创建人';
COMMENT ON COLUMN cln_observation_definition.updated_by IS '最后更新人';
COMMENT ON COLUMN cln_observation_definition.is_deleted IS '软删标记';
COMMENT ON COLUMN cln_observation_definition.deleted_at IS '删除时间';
COMMENT ON COLUMN cln_observation_definition.deleted_by IS '删除人';
COMMENT ON COLUMN cln_observation_definition.version IS '乐观锁版本号';
COMMENT ON COLUMN cln_observation_definition.code IS '观察项编码';
COMMENT ON COLUMN cln_observation_definition.display IS '展示名称';
COMMENT ON COLUMN cln_observation_definition.value_type_enum IS '值类型枚举';
COMMENT ON COLUMN cln_observation_definition.unit_code IS '单位编码';
COMMENT ON COLUMN cln_observation_definition.category_code IS '分类编码';
COMMENT ON COLUMN cln_observation_definition.status_enum IS '状态枚举';

-- from V75__cln_observation.sql
-- cln_observation（建表）
-- Flyway V76 · DM-CLN-005

CREATE TABLE IF NOT EXISTS "public"."cln_observation" (
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
    "service_request_id" int8,
    "definition_id" int8 NOT NULL,
    "code" varchar(255),
    "value_quantity" numeric(18, 6),
    "value_string" text,
    "value_codeable_concept" varchar(512),
    "interpretation_code" varchar(64),
    "status_enum" varchar(64) NOT NULL,
    "effective_time" timestamptz(6),
    "source_observation_id" int8,
    "source_module_enum" varchar(32),
    "performer_id" int8,
    CONSTRAINT "cln_observation_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "idx_cln_obs_encounter"
    ON "public"."cln_observation" ("encounter_id")
    WHERE "is_deleted" = false;
CREATE INDEX IF NOT EXISTS "idx_cln_obs_sr_def"
    ON "public"."cln_observation" ("service_request_id", "definition_id")
    WHERE "is_deleted" = false;

COMMENT ON TABLE cln_observation IS '临床观察实例（体检小项/分科结论/LIS 投影）';
COMMENT ON COLUMN cln_observation.id IS '主键';
COMMENT ON COLUMN cln_observation.tenant_id IS '租户标识';
COMMENT ON COLUMN cln_observation.org_id IS '机构标识';
COMMENT ON COLUMN cln_observation.created_at IS '创建时间';
COMMENT ON COLUMN cln_observation.updated_at IS '最后更新时间';
COMMENT ON COLUMN cln_observation.created_by IS '创建人';
COMMENT ON COLUMN cln_observation.updated_by IS '最后更新人';
COMMENT ON COLUMN cln_observation.is_deleted IS '软删标记';
COMMENT ON COLUMN cln_observation.deleted_at IS '删除时间';
COMMENT ON COLUMN cln_observation.deleted_by IS '删除人';
COMMENT ON COLUMN cln_observation.version IS '乐观锁版本号';
COMMENT ON COLUMN cln_observation.encounter_id IS '就诊标识';
COMMENT ON COLUMN cln_observation.service_request_id IS '服务请求标识';
COMMENT ON COLUMN cln_observation.definition_id IS '观察项定义标识';
COMMENT ON COLUMN cln_observation.code IS '观察项编码';
COMMENT ON COLUMN cln_observation.value_quantity IS '数值结果';
COMMENT ON COLUMN cln_observation.value_string IS '文本结果';
COMMENT ON COLUMN cln_observation.value_codeable_concept IS '编码概念结果';
COMMENT ON COLUMN cln_observation.interpretation_code IS '解读编码';
COMMENT ON COLUMN cln_observation.status_enum IS '状态枚举';
COMMENT ON COLUMN cln_observation.effective_time IS '有效时间';
COMMENT ON COLUMN cln_observation.source_observation_id IS '来源观察实例标识（LIS 投影时指向 lis_observation.id）';
COMMENT ON COLUMN cln_observation.source_module_enum IS '来源模块枚举';
COMMENT ON COLUMN cln_observation.performer_id IS '执行人标识';

-- from V76__cln_diagnostic_report_catalog.sql
-- cln_diagnostic_report_catalog（建表）
-- Flyway V76 · DM-CLN-006

CREATE TABLE IF NOT EXISTS "public"."cln_diagnostic_report_catalog" (
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
    CONSTRAINT "cln_diagnostic_report_catalog_pkey" PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX IF NOT EXISTS "uk_cln_dr_catalog_unique_key"
    ON "public"."cln_diagnostic_report_catalog" ("unique_key")
    WHERE "is_deleted" = false;

COMMENT ON TABLE cln_diagnostic_report_catalog IS '多模块 DiagnosticReport 读侧目录';
COMMENT ON COLUMN cln_diagnostic_report_catalog.id IS '主键';
COMMENT ON COLUMN cln_diagnostic_report_catalog.tenant_id IS '租户标识';
COMMENT ON COLUMN cln_diagnostic_report_catalog.org_id IS '机构标识';
COMMENT ON COLUMN cln_diagnostic_report_catalog.created_at IS '创建时间';
COMMENT ON COLUMN cln_diagnostic_report_catalog.updated_at IS '最后更新时间';
COMMENT ON COLUMN cln_diagnostic_report_catalog.created_by IS '创建人';
COMMENT ON COLUMN cln_diagnostic_report_catalog.updated_by IS '最后更新人';
COMMENT ON COLUMN cln_diagnostic_report_catalog.is_deleted IS '软删标记';
COMMENT ON COLUMN cln_diagnostic_report_catalog.deleted_at IS '删除时间';
COMMENT ON COLUMN cln_diagnostic_report_catalog.deleted_by IS '删除人';
COMMENT ON COLUMN cln_diagnostic_report_catalog.version IS '乐观锁版本号';
COMMENT ON COLUMN cln_diagnostic_report_catalog.module_enum IS '来源模块枚举';
COMMENT ON COLUMN cln_diagnostic_report_catalog.source_report_id IS '源报告标识';
COMMENT ON COLUMN cln_diagnostic_report_catalog.encounter_id IS '就诊标识';
COMMENT ON COLUMN cln_diagnostic_report_catalog.service_request_id IS '服务请求标识';
COMMENT ON COLUMN cln_diagnostic_report_catalog.patient_id IS '患者标识';
COMMENT ON COLUMN cln_diagnostic_report_catalog.status_enum IS '状态枚举';
COMMENT ON COLUMN cln_diagnostic_report_catalog.published_at IS '发布时间';
COMMENT ON COLUMN cln_diagnostic_report_catalog.title IS '报告标题';
COMMENT ON COLUMN cln_diagnostic_report_catalog.category_code IS '分类编码';
COMMENT ON COLUMN cln_diagnostic_report_catalog.pdf_storage_key IS 'PDF 存储键';
COMMENT ON COLUMN cln_diagnostic_report_catalog.external_view_url IS '外部查看 URL';
COMMENT ON COLUMN cln_diagnostic_report_catalog.amended_from_id IS '修正来源报告标识';
COMMENT ON COLUMN cln_diagnostic_report_catalog.unique_key IS '唯一键';

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
