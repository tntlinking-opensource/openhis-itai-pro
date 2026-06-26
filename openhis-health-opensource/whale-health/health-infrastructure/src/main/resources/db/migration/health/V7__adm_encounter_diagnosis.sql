-- adm_encounter_diagnosis（建表）
-- Flyway V7

CREATE TABLE IF NOT EXISTS "public"."adm_encounter_diagnosis" (
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
    "encounter_id" int8 NOT NULL,
    "condition_id" int8 NOT NULL,
    "primary_flag" bool,
    "rank" int4,
    "description" varchar(255),
    "diag_type_code" varchar(32),
    "diag_code" int4,
    "condition_code" int4,
    "med_type" varchar(255),
    "highest_diag_flag" bool,
    "tcm_syndrome_group" varchar(255),
    "tcm_flag" bool,
    CONSTRAINT "adm_encounter_diagnosis_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE adm_encounter_diagnosis IS '管理域 encounter diagnosis';
COMMENT ON COLUMN adm_encounter_diagnosis.id IS '主键';
COMMENT ON COLUMN adm_encounter_diagnosis.tenant_id IS '租户标识';
COMMENT ON COLUMN adm_encounter_diagnosis.created_at IS '创建时间';
COMMENT ON COLUMN adm_encounter_diagnosis.updated_at IS '最后更新时间';
COMMENT ON COLUMN adm_encounter_diagnosis.created_by IS '创建人';
COMMENT ON COLUMN adm_encounter_diagnosis.updated_by IS '最后更新人';
COMMENT ON COLUMN adm_encounter_diagnosis.is_deleted IS '软删标记';
COMMENT ON COLUMN adm_encounter_diagnosis.deleted_at IS '删除时间';
COMMENT ON COLUMN adm_encounter_diagnosis.deleted_by IS '删除人';
COMMENT ON COLUMN adm_encounter_diagnosis.version IS '乐观锁版本号';
COMMENT ON COLUMN adm_encounter_diagnosis.encounter_id IS '就诊诊断关联子实体：持久化就诊与病情（Condition）的多对多事实及诊断扩展字段';
COMMENT ON COLUMN adm_encounter_diagnosis.condition_id IS '病情/诊断条件主键';
COMMENT ON COLUMN adm_encounter_diagnosis.primary_flag IS '是否主诊断';
COMMENT ON COLUMN adm_encounter_diagnosis.rank IS '排序序号';
COMMENT ON COLUMN adm_encounter_diagnosis.description IS '诊断描述';
COMMENT ON COLUMN adm_encounter_diagnosis.diag_type_code IS '诊断类型码（术语 code 字符串，与 ipt_dise_type_code 对齐，不做整型转换）';
COMMENT ON COLUMN adm_encounter_diagnosis.diag_code IS '诊断编码';
COMMENT ON COLUMN adm_encounter_diagnosis.condition_code IS '病情编码';
COMMENT ON COLUMN adm_encounter_diagnosis.med_type IS '医保或诊疗类别';
COMMENT ON COLUMN adm_encounter_diagnosis.highest_diag_flag IS '是否最高诊断';
COMMENT ON COLUMN adm_encounter_diagnosis.tcm_syndrome_group IS '中医证候组';
COMMENT ON COLUMN adm_encounter_diagnosis.tcm_flag IS '是否中医诊断';
