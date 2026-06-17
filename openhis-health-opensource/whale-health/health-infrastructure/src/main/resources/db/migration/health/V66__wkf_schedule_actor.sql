-- wkf_schedule_actor（建表）
-- Flyway V66

CREATE TABLE IF NOT EXISTS "public"."wkf_schedule_actor" (
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
    "schedule_id" int8 NOT NULL,
    "actor_type" int4 NOT NULL,
    "actor_id" int8 NOT NULL,
    "sort_no" int4 NOT NULL,
    CONSTRAINT "wkf_schedule_actor_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE wkf_schedule_actor IS '日程参与者（医师/诊室/设备等）';

COMMENT ON COLUMN wkf_schedule_actor.id IS '主键';
COMMENT ON COLUMN wkf_schedule_actor.tenant_id IS '租户标识';
COMMENT ON COLUMN wkf_schedule_actor.created_at IS '创建时间';
COMMENT ON COLUMN wkf_schedule_actor.updated_at IS '最后更新时间';
COMMENT ON COLUMN wkf_schedule_actor.created_by IS '创建人';
COMMENT ON COLUMN wkf_schedule_actor.updated_by IS '最后更新人';
COMMENT ON COLUMN wkf_schedule_actor.is_deleted IS '软删标记';
COMMENT ON COLUMN wkf_schedule_actor.deleted_at IS '删除时间';
COMMENT ON COLUMN wkf_schedule_actor.deleted_by IS '删除人';
COMMENT ON COLUMN wkf_schedule_actor.version IS '乐观锁版本号';
COMMENT ON COLUMN wkf_schedule_actor.schedule_id IS '所属日程标识';
COMMENT ON COLUMN wkf_schedule_actor.actor_type IS '参与者类型编码';
COMMENT ON COLUMN wkf_schedule_actor.actor_id IS '参与者主键';
COMMENT ON COLUMN wkf_schedule_actor.sort_no IS '排序号';

