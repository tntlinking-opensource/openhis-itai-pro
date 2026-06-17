-- cus_activity_execution_location（建表）
-- Flyway V29

CREATE TABLE IF NOT EXISTS "public"."cus_activity_execution_location" (
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
    "activity_definition_id" int8,
    "activity_category_code" varchar(255) NOT NULL,
    "execution_org_id" int8 NOT NULL,
    "execution_location_id" int8,
    "start_time" time(6),
    "end_time" time(6),
    "priority" int4 NOT NULL,
    "display_order" int4,
    "remark" varchar(255),
    CONSTRAINT "cus_activity_execution_location_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE cus_activity_execution_location IS '自定义 activity execution location';

COMMENT ON COLUMN cus_activity_execution_location.id IS '主键';
COMMENT ON COLUMN cus_activity_execution_location.tenant_id IS '租户标识';
COMMENT ON COLUMN cus_activity_execution_location.created_at IS '创建时间';
COMMENT ON COLUMN cus_activity_execution_location.updated_at IS '最后更新时间';
COMMENT ON COLUMN cus_activity_execution_location.created_by IS '创建人';
COMMENT ON COLUMN cus_activity_execution_location.updated_by IS '最后更新人';
COMMENT ON COLUMN cus_activity_execution_location.is_deleted IS '软删标记';
COMMENT ON COLUMN cus_activity_execution_location.deleted_at IS '删除时间';
COMMENT ON COLUMN cus_activity_execution_location.deleted_by IS '删除人';
COMMENT ON COLUMN cus_activity_execution_location.version IS '乐观锁版本号';
COMMENT ON COLUMN cus_activity_execution_location.activity_definition_id IS '执行科室规则聚合根';
COMMENT ON COLUMN cus_activity_execution_location.activity_category_code IS '活动类别编码';
COMMENT ON COLUMN cus_activity_execution_location.execution_org_id IS '执行科室标识';
COMMENT ON COLUMN cus_activity_execution_location.execution_location_id IS '执行位置标识';
COMMENT ON COLUMN cus_activity_execution_location.start_time IS '生效开始时间';
COMMENT ON COLUMN cus_activity_execution_location.end_time IS '生效结束时间';
COMMENT ON COLUMN cus_activity_execution_location.priority IS '优先级（数值越小越优先，1 最优先；与取药科室一致）';
COMMENT ON COLUMN cus_activity_execution_location.display_order IS '展示顺序';
COMMENT ON COLUMN cus_activity_execution_location.remark IS '备注';

