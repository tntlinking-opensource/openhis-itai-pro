-- cus_activity_observation_link（建表）
-- Flyway V78 · DM-CUS-013

CREATE TABLE IF NOT EXISTS "public"."cus_activity_observation_link" (
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
    "activity_definition_id" int8 NOT NULL,
    "observation_definition_id" int8 NOT NULL,
    "sort_num" int4 NOT NULL DEFAULT 0,
    "required_flag" bool NOT NULL DEFAULT true,
    CONSTRAINT "cus_activity_observation_link_pkey" PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX IF NOT EXISTS "uk_aol_activity_od"
    ON "public"."cus_activity_observation_link" ("activity_definition_id", "observation_definition_id")
    WHERE "is_deleted" = false;

COMMENT ON TABLE cus_activity_observation_link IS '体检项目 AD ↔ 小项 OD 绑定';
COMMENT ON COLUMN cus_activity_observation_link.id IS '主键';
COMMENT ON COLUMN cus_activity_observation_link.tenant_id IS '租户标识';
COMMENT ON COLUMN cus_activity_observation_link.org_id IS '机构标识';
COMMENT ON COLUMN cus_activity_observation_link.created_at IS '创建时间';
COMMENT ON COLUMN cus_activity_observation_link.updated_at IS '最后更新时间';
COMMENT ON COLUMN cus_activity_observation_link.created_by IS '创建人';
COMMENT ON COLUMN cus_activity_observation_link.updated_by IS '最后更新人';
COMMENT ON COLUMN cus_activity_observation_link.is_deleted IS '软删标记';
COMMENT ON COLUMN cus_activity_observation_link.deleted_at IS '删除时间';
COMMENT ON COLUMN cus_activity_observation_link.deleted_by IS '删除人';
COMMENT ON COLUMN cus_activity_observation_link.version IS '乐观锁版本号';
COMMENT ON COLUMN cus_activity_observation_link.activity_definition_id IS '活动定义标识';
COMMENT ON COLUMN cus_activity_observation_link.observation_definition_id IS '观察项定义标识';
COMMENT ON COLUMN cus_activity_observation_link.sort_num IS '排序号';
COMMENT ON COLUMN cus_activity_observation_link.required_flag IS '是否必填';
