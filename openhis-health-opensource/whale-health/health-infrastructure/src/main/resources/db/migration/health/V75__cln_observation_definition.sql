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
