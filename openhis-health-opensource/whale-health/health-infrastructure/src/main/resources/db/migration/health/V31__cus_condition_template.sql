-- cus_condition_template（建表）
-- Flyway V31

CREATE TABLE IF NOT EXISTS "public"."cus_condition_template" (
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
    "object_id" int8 NOT NULL,
    "definition_id" int8 NOT NULL,
    "binding_enum" varchar(255) NOT NULL,
    "sort_weight" int4,
    "remark" varchar(255),
    CONSTRAINT "cus_condition_template_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE cus_condition_template IS '自定义 condition template';
COMMENT ON COLUMN cus_condition_template.id IS '主键';
COMMENT ON COLUMN cus_condition_template.tenant_id IS '租户标识';
COMMENT ON COLUMN cus_condition_template.created_at IS '创建时间';
COMMENT ON COLUMN cus_condition_template.updated_at IS '最后更新时间';
COMMENT ON COLUMN cus_condition_template.created_by IS '创建人';
COMMENT ON COLUMN cus_condition_template.updated_by IS '最后更新人';
COMMENT ON COLUMN cus_condition_template.is_deleted IS '软删标记';
COMMENT ON COLUMN cus_condition_template.deleted_at IS '删除时间';
COMMENT ON COLUMN cus_condition_template.deleted_by IS '删除人';
COMMENT ON COLUMN cus_condition_template.version IS '乐观锁版本号';
COMMENT ON COLUMN cus_condition_template.object_id IS '诊断常用模板聚合根';
COMMENT ON COLUMN cus_condition_template.definition_id IS '诊断定义标识';
COMMENT ON COLUMN cus_condition_template.binding_enum IS '绑定类型枚举';
COMMENT ON COLUMN cus_condition_template.sort_weight IS '排序权重';
COMMENT ON COLUMN cus_condition_template.remark IS '备注';
