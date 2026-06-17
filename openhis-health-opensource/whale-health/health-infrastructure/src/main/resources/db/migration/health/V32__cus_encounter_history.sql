-- cus_encounter_history（建表）
-- Flyway V32

CREATE TABLE IF NOT EXISTS "public"."cus_encounter_history" (
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
    "name" varchar(255) NOT NULL,
    "encounter_id" int8 NOT NULL,
    "request_id" int8,
    "request_table_name" varchar(255),
    "original_organization_id" int8,
    "target_organization_id" int8,
    "original_location_id" int8,
    "target_location_id" int8,
    "out_way_code" varchar(255),
    CONSTRAINT "cus_encounter_history_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE cus_encounter_history IS '自定义 encounter history';

COMMENT ON COLUMN cus_encounter_history.id IS '主键';
COMMENT ON COLUMN cus_encounter_history.tenant_id IS '租户标识';
COMMENT ON COLUMN cus_encounter_history.created_at IS '创建时间';
COMMENT ON COLUMN cus_encounter_history.updated_at IS '最后更新时间';
COMMENT ON COLUMN cus_encounter_history.created_by IS '创建人';
COMMENT ON COLUMN cus_encounter_history.updated_by IS '最后更新人';
COMMENT ON COLUMN cus_encounter_history.is_deleted IS '软删标记';
COMMENT ON COLUMN cus_encounter_history.deleted_at IS '删除时间';
COMMENT ON COLUMN cus_encounter_history.deleted_by IS '删除人';
COMMENT ON COLUMN cus_encounter_history.version IS '乐观锁版本号';
COMMENT ON COLUMN cus_encounter_history.name IS '就诊历史里程碑聚合根';
COMMENT ON COLUMN cus_encounter_history.encounter_id IS '就诊标识';
COMMENT ON COLUMN cus_encounter_history.request_id IS '请求标识（如 ServiceRequest）';
COMMENT ON COLUMN cus_encounter_history.request_table_name IS '请求表名（与 requestId 成对出现）';
COMMENT ON COLUMN cus_encounter_history.original_organization_id IS '原科室标识';
COMMENT ON COLUMN cus_encounter_history.target_organization_id IS '目标科室标识';
COMMENT ON COLUMN cus_encounter_history.original_location_id IS '原位置标识（病区/床位等）';
COMMENT ON COLUMN cus_encounter_history.target_location_id IS '目标位置标识（病区/床位等）';
COMMENT ON COLUMN cus_encounter_history.out_way_code IS '出院方式编码（仅离院类里程碑使用）';

