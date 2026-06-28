-- peis_auto_express（建表）
-- Flyway V85 · DM-PEIS-006

CREATE TABLE IF NOT EXISTS "public"."peis_auto_express" (
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
    "name" varchar(512) NOT NULL,
    "express_script" text NOT NULL,
    "target_condition_def_id" int8,
    "rule_type_code" varchar(64) NOT NULL,
    "status_enum" varchar(64) NOT NULL DEFAULT 'active',
    CONSTRAINT "peis_auto_express_pkey" PRIMARY KEY ("id")
);
CREATE INDEX IF NOT EXISTS "idx_peis_auto_express_rule_type"
    ON "public"."peis_auto_express" ("tenant_id", "rule_type_code", "status_enum")
    WHERE "is_deleted" = false;

COMMENT ON TABLE peis_auto_express IS 'PEIS 自动诊断/危急值表达式规则';
COMMENT ON COLUMN peis_auto_express.id IS '主键';
COMMENT ON COLUMN peis_auto_express.tenant_id IS '租户标识';
COMMENT ON COLUMN peis_auto_express.org_id IS '机构标识';
COMMENT ON COLUMN peis_auto_express.created_at IS '创建时间';
COMMENT ON COLUMN peis_auto_express.updated_at IS '最后更新时间';
COMMENT ON COLUMN peis_auto_express.created_by IS '创建人';
COMMENT ON COLUMN peis_auto_express.updated_by IS '最后更新人';
COMMENT ON COLUMN peis_auto_express.is_deleted IS '软删标记';
COMMENT ON COLUMN peis_auto_express.deleted_at IS '删除时间';
COMMENT ON COLUMN peis_auto_express.deleted_by IS '删除人';
COMMENT ON COLUMN peis_auto_express.version IS '乐观锁版本号';
COMMENT ON COLUMN peis_auto_express.name IS '规则名称';
COMMENT ON COLUMN peis_auto_express.express_script IS '表达式脚本';
COMMENT ON COLUMN peis_auto_express.target_condition_def_id IS '目标诊断定义标识';
COMMENT ON COLUMN peis_auto_express.rule_type_code IS '规则类型编码';
COMMENT ON COLUMN peis_auto_express.status_enum IS '状态枚举';
