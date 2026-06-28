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
