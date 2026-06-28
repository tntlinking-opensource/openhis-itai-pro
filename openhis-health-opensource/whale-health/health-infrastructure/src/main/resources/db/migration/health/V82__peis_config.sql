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
