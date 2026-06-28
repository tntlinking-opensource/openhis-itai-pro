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

-- seed: cus_activity_execution_location
INSERT INTO "cus_activity_execution_location" ("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "is_deleted", "deleted_at", "deleted_by", "version", "activity_definition_id", "activity_category_code", "execution_org_id", "execution_location_id", "start_time", "end_time", "priority", "display_order", "remark")
VALUES
('329555420386430976', '1', '2026-06-28 09:35:35.063+00', '2026-06-28 09:35:35.063+00', '1', '1', false, NULL, NULL, 0, '328098713494360064', '23', '322663606385643520', NULL, '00:00:00', '23:59:59', 1, NULL, NULL),
('329555452351221760', '1', '2026-06-28 09:35:42.684+00', '2026-06-28 09:35:42.684+00', '1', '1', false, NULL, NULL, 0, '323004165299113984', '22', '322663606385643520', NULL, '00:00:00', '23:59:59', 1, NULL, NULL),
('329555473893167104', '1', '2026-06-28 09:35:47.820+00', '2026-06-28 09:35:47.820+00', '1', '1', false, NULL, NULL, 0, '323003446592540672', '22', '322663606385643520', NULL, '00:00:00', '23:59:59', 1, NULL, NULL),
('329555495963594752', '1', '2026-06-28 09:35:53.082+00', '2026-06-28 09:35:53.082+00', '1', '1', false, NULL, NULL, 0, '323002894106234880', '22', '322663606385643520', NULL, '00:00:00', '23:59:59', 1, NULL, NULL)
ON CONFLICT ("id") DO NOTHING;
