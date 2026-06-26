-- wkf_activity_definition_children（建表）
-- Flyway V60

CREATE TABLE IF NOT EXISTS "public"."wkf_activity_definition_children" (
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
    "activity_id" int8 NOT NULL,
    "children_id" int8 NOT NULL,
    "children_num" int4,
    "children_name" varchar(255),
    CONSTRAINT "wkf_activity_definition_children_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE wkf_activity_definition_children IS '工作流 activity definition children';
COMMENT ON COLUMN wkf_activity_definition_children.id IS '主键';
COMMENT ON COLUMN wkf_activity_definition_children.tenant_id IS '租户标识';
COMMENT ON COLUMN wkf_activity_definition_children.created_at IS '创建时间';
COMMENT ON COLUMN wkf_activity_definition_children.updated_at IS '最后更新时间';
COMMENT ON COLUMN wkf_activity_definition_children.created_by IS '创建人';
COMMENT ON COLUMN wkf_activity_definition_children.updated_by IS '最后更新人';
COMMENT ON COLUMN wkf_activity_definition_children.is_deleted IS '软删标记';
COMMENT ON COLUMN wkf_activity_definition_children.deleted_at IS '删除时间';
COMMENT ON COLUMN wkf_activity_definition_children.deleted_by IS '删除人';
COMMENT ON COLUMN wkf_activity_definition_children.version IS '乐观锁版本号';
COMMENT ON COLUMN wkf_activity_definition_children.activity_id IS '诊疗项目子项实体';
COMMENT ON COLUMN wkf_activity_definition_children.children_id IS '子项诊疗项目标识（被引用项目主键）';
COMMENT ON COLUMN wkf_activity_definition_children.children_num IS '子项数量';
COMMENT ON COLUMN wkf_activity_definition_children.children_name IS '子项名称快照';
