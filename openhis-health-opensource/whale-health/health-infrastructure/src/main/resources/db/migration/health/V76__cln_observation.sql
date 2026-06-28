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
