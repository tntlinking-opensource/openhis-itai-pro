-- wkf_slot（建表）
-- Flyway V70

CREATE TABLE IF NOT EXISTS "public"."wkf_slot" (
    "id" int8 NOT NULL,
    "tenant_id" int8,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    "version" int8 NOT NULL DEFAULT 0,
    "organization_id" int8 NOT NULL,
    "schedule_id" int8 NOT NULL,
    "start_time" timestamptz(6) NOT NULL,
    "end_time" timestamptz(6) NOT NULL,
    "status_code" varchar(255) NOT NULL,
    "capacity" int4 NOT NULL,
    "booked_count" int4 NOT NULL,
    "overbooked_flag" bool NOT NULL,
    "service_category_json" varchar(255),
    "service_type_json" varchar(255),
    "specialty_json" varchar(255),
    "appointment_type_json" varchar(255),
    "comment_text" varchar(255),
    CONSTRAINT "wkf_slot_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE wkf_slot IS '号源时段（FHIR Slot 语义）';
COMMENT ON COLUMN wkf_slot.id IS '主键';
COMMENT ON COLUMN wkf_slot.tenant_id IS '租户标识';
COMMENT ON COLUMN wkf_slot.created_at IS '创建时间';
COMMENT ON COLUMN wkf_slot.updated_at IS '最后更新时间';
COMMENT ON COLUMN wkf_slot.created_by IS '创建人';
COMMENT ON COLUMN wkf_slot.updated_by IS '最后更新人';
COMMENT ON COLUMN wkf_slot.is_deleted IS '软删标记';
COMMENT ON COLUMN wkf_slot.deleted_at IS '删除时间';
COMMENT ON COLUMN wkf_slot.deleted_by IS '删除人';
COMMENT ON COLUMN wkf_slot.version IS '乐观锁版本号';
COMMENT ON COLUMN wkf_slot.organization_id IS '机构标识（冗余自 Schedule）';
COMMENT ON COLUMN wkf_slot.schedule_id IS '所属日程标识';
COMMENT ON COLUMN wkf_slot.start_time IS '时段开始时刻';
COMMENT ON COLUMN wkf_slot.end_time IS '时段结束时刻';
COMMENT ON COLUMN wkf_slot.status_code IS '状态码（free/busy/busy-unavailable 等）';
COMMENT ON COLUMN wkf_slot.capacity IS '额定容量';
COMMENT ON COLUMN wkf_slot.booked_count IS '已预约计数';
COMMENT ON COLUMN wkf_slot.overbooked_flag IS '超售标记';
COMMENT ON COLUMN wkf_slot.service_category_json IS '服务大类快照 JSON';
COMMENT ON COLUMN wkf_slot.service_type_json IS '服务类型快照 JSON';
COMMENT ON COLUMN wkf_slot.specialty_json IS '专科快照 JSON';
COMMENT ON COLUMN wkf_slot.appointment_type_json IS '预约类型/场景快照 JSON';
COMMENT ON COLUMN wkf_slot.comment_text IS '备注';
