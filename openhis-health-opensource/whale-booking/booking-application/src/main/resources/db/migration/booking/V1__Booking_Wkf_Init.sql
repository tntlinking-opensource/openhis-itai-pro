-- =============================================================================
-- V1 · Booking 运行时核心表（wkf_*）
-- 预约队列、号源日程、号源时段及参与者/外部标识（自 health Flyway 迁入）
-- =============================================================================

-- ---------------------------------------------------------------------------
-- wkf_appointment_queue · 预约与候诊队列（FHIR Appointment）
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS "public"."wkf_appointment_queue" (
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
    "appointment_kind" varchar(255) NOT NULL,
    "bus_no" varchar(255),
    "patient_id" int8,
    "encounter_id" int8,
    "organization_id" int8 NOT NULL,
    "destination_location_id" int8,
    "triage_level" varchar(255),
    "triage_priority_code" varchar(255),
    "triage_time" timestamptz(6),
    "display_order" int4,
    "slot_id" int8,
    "service_type_code" varchar(255),
    "appointment_type_code" varchar(255),
    "start_instant" timestamptz(6),
    "end_instant" timestamptz(6),
    "cancel_reason" varchar(255),
    "cancellation_reason_code" varchar(255),
    "cancellation_at" timestamptz(6),
    "called_at" timestamptz(6),
    "last_practitioner_id" int8,
    "origin_channel" varchar(255),
    "origin_channel_session_id" varchar(255),
    "origin_source_device_id" int8,
    "origin_operator_id" int8,
    "origin_client_app_version" varchar(255),
    CONSTRAINT "wkf_appointment_queue_pkey" PRIMARY KEY ("id")
);


CREATE TABLE IF NOT EXISTS "public"."wkf_schedule" (
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
    "organization_id" int8 NOT NULL,
    "name" varchar(255),
    "status_enum" varchar(255) NOT NULL,
    "active_flag" bool NOT NULL,
    "planning_horizon_start" timestamptz(6),
    "planning_horizon_end" timestamptz(6),
    "service_type_concept_json" varchar(255),
    "healthcare_service_id" int8,
    "service_category_json" varchar(255),
    "specialty_json" varchar(255),
    "comment_markdown" varchar(255),
    "substitute_practitioner_id" int8,
    "substitute_reason" varchar(255),
    CONSTRAINT "wkf_schedule_pkey" PRIMARY KEY ("id")
);


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


CREATE TABLE IF NOT EXISTS "public"."wkf_schedule_identifier" (
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
    "id_system" varchar(255) NOT NULL,
    "id_value" varchar(255) NOT NULL,
    "use_code" varchar(255),
    CONSTRAINT "wkf_schedule_identifier_pkey" PRIMARY KEY ("id")
);


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


CREATE TABLE IF NOT EXISTS "public"."wkf_slot_identifier" (
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
    "slot_id" int8 NOT NULL,
    "id_system" varchar(255) NOT NULL,
    "id_value" varchar(255) NOT NULL,
    "use_code" varchar(255),
    CONSTRAINT "wkf_slot_identifier_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE wkf_appointment_queue IS '预约与候诊队列（FHIR Appointment 语义）';
COMMENT ON COLUMN wkf_appointment_queue.id IS '主键';
COMMENT ON COLUMN wkf_appointment_queue.tenant_id IS '租户标识';
COMMENT ON COLUMN wkf_appointment_queue.created_at IS '创建时间';
COMMENT ON COLUMN wkf_appointment_queue.updated_at IS '最后更新时间';
COMMENT ON COLUMN wkf_appointment_queue.created_by IS '创建人';
COMMENT ON COLUMN wkf_appointment_queue.updated_by IS '最后更新人';
COMMENT ON COLUMN wkf_appointment_queue.is_deleted IS '软删标记';
COMMENT ON COLUMN wkf_appointment_queue.deleted_at IS '删除时间';
COMMENT ON COLUMN wkf_appointment_queue.deleted_by IS '删除人';
COMMENT ON COLUMN wkf_appointment_queue.version IS '乐观锁版本号';
COMMENT ON COLUMN wkf_appointment_queue.appointment_kind IS '预约分类（OUTPATIENT_BOOKING / CHECK_IN_ONLY 等）';
COMMENT ON COLUMN wkf_appointment_queue.bus_no IS '业务号';
COMMENT ON COLUMN wkf_appointment_queue.patient_id IS '患者标识';
COMMENT ON COLUMN wkf_appointment_queue.encounter_id IS '就诊标识';
COMMENT ON COLUMN wkf_appointment_queue.organization_id IS '机构标识';
COMMENT ON COLUMN wkf_appointment_queue.destination_location_id IS '去向诊室/地点标识';
COMMENT ON COLUMN wkf_appointment_queue.triage_level IS '分诊等级（文本）';
COMMENT ON COLUMN wkf_appointment_queue.triage_priority_code IS '分诊优先级编码';
COMMENT ON COLUMN wkf_appointment_queue.triage_time IS '分诊时间';
COMMENT ON COLUMN wkf_appointment_queue.display_order IS '候诊显示顺序';
COMMENT ON COLUMN wkf_appointment_queue.slot_id IS '号源标识（门诊预约）';
COMMENT ON COLUMN wkf_appointment_queue.service_type_code IS '服务类型编码';
COMMENT ON COLUMN wkf_appointment_queue.appointment_type_code IS '预约类型编码';
COMMENT ON COLUMN wkf_appointment_queue.start_instant IS '计划开始时间';
COMMENT ON COLUMN wkf_appointment_queue.end_instant IS '计划结束时间';
COMMENT ON COLUMN wkf_appointment_queue.cancel_reason IS '取消/终态原因（文本）';
COMMENT ON COLUMN wkf_appointment_queue.cancellation_reason_code IS '取消/终态原因编码';
COMMENT ON COLUMN wkf_appointment_queue.cancellation_at IS '取消/过号时间';
COMMENT ON COLUMN wkf_appointment_queue.called_at IS '最近叫号时间';
COMMENT ON COLUMN wkf_appointment_queue.last_practitioner_id IS '最近叫号医师标识';
COMMENT ON COLUMN wkf_appointment_queue.origin_channel IS '渠道编码（KIOSK/MINIAPP/COUNTER 等）';
COMMENT ON COLUMN wkf_appointment_queue.origin_channel_session_id IS '渠道会话标识';
COMMENT ON COLUMN wkf_appointment_queue.origin_source_device_id IS '来源设备标识';
COMMENT ON COLUMN wkf_appointment_queue.origin_operator_id IS '代操作操作者标识';
COMMENT ON COLUMN wkf_appointment_queue.origin_client_app_version IS '客户端版本号';

COMMENT ON TABLE wkf_schedule IS '号源日程（FHIR Schedule 语义）';
COMMENT ON COLUMN wkf_schedule.id IS '主键';
COMMENT ON COLUMN wkf_schedule.tenant_id IS '租户标识';
COMMENT ON COLUMN wkf_schedule.created_at IS '创建时间';
COMMENT ON COLUMN wkf_schedule.updated_at IS '最后更新时间';
COMMENT ON COLUMN wkf_schedule.created_by IS '创建人';
COMMENT ON COLUMN wkf_schedule.updated_by IS '最后更新人';
COMMENT ON COLUMN wkf_schedule.is_deleted IS '软删标记';
COMMENT ON COLUMN wkf_schedule.deleted_at IS '删除时间';
COMMENT ON COLUMN wkf_schedule.deleted_by IS '删除人';
COMMENT ON COLUMN wkf_schedule.version IS '乐观锁版本号';
COMMENT ON COLUMN wkf_schedule.organization_id IS '机构标识';
COMMENT ON COLUMN wkf_schedule.name IS '日程显示名称';
COMMENT ON COLUMN wkf_schedule.status_enum IS '业务状态（draft/active/inactive/retired）';
COMMENT ON COLUMN wkf_schedule.active_flag IS '启用标记';
COMMENT ON COLUMN wkf_schedule.planning_horizon_start IS '规划窗口起始时间';
COMMENT ON COLUMN wkf_schedule.planning_horizon_end IS '规划窗口结束时间';
COMMENT ON COLUMN wkf_schedule.service_type_concept_json IS '服务类型概念快照 JSON';
COMMENT ON COLUMN wkf_schedule.healthcare_service_id IS '医疗服务引用';
COMMENT ON COLUMN wkf_schedule.service_category_json IS '服务大类快照 JSON';
COMMENT ON COLUMN wkf_schedule.specialty_json IS '专科快照 JSON';
COMMENT ON COLUMN wkf_schedule.comment_markdown IS '可用性说明（Markdown）';
COMMENT ON COLUMN wkf_schedule.substitute_practitioner_id IS '替诊医师主键（顶班 B；原医师仍保留在 Actor）';
COMMENT ON COLUMN wkf_schedule.substitute_reason IS '替诊原因说明';

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

COMMENT ON TABLE wkf_schedule_identifier IS '日程外部标识（FHIR Identifier 语义）';
COMMENT ON COLUMN wkf_schedule_identifier.id IS '主键';
COMMENT ON COLUMN wkf_schedule_identifier.tenant_id IS '租户标识';
COMMENT ON COLUMN wkf_schedule_identifier.created_at IS '创建时间';
COMMENT ON COLUMN wkf_schedule_identifier.updated_at IS '最后更新时间';
COMMENT ON COLUMN wkf_schedule_identifier.created_by IS '创建人';
COMMENT ON COLUMN wkf_schedule_identifier.updated_by IS '最后更新人';
COMMENT ON COLUMN wkf_schedule_identifier.is_deleted IS '软删标记';
COMMENT ON COLUMN wkf_schedule_identifier.deleted_at IS '删除时间';
COMMENT ON COLUMN wkf_schedule_identifier.deleted_by IS '删除人';
COMMENT ON COLUMN wkf_schedule_identifier.version IS '乐观锁版本号';
COMMENT ON COLUMN wkf_schedule_identifier.schedule_id IS '所属日程标识';
COMMENT ON COLUMN wkf_schedule_identifier.id_system IS '标识体系 URI';
COMMENT ON COLUMN wkf_schedule_identifier.id_value IS '标识值';
COMMENT ON COLUMN wkf_schedule_identifier.use_code IS '用途编码';

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

COMMENT ON TABLE wkf_slot_identifier IS '号源时段外部标识（FHIR Identifier 语义）';
COMMENT ON COLUMN wkf_slot_identifier.id IS '主键';
COMMENT ON COLUMN wkf_slot_identifier.tenant_id IS '租户标识';
COMMENT ON COLUMN wkf_slot_identifier.created_at IS '创建时间';
COMMENT ON COLUMN wkf_slot_identifier.updated_at IS '最后更新时间';
COMMENT ON COLUMN wkf_slot_identifier.created_by IS '创建人';
COMMENT ON COLUMN wkf_slot_identifier.updated_by IS '最后更新人';
COMMENT ON COLUMN wkf_slot_identifier.is_deleted IS '软删标记';
COMMENT ON COLUMN wkf_slot_identifier.deleted_at IS '删除时间';
COMMENT ON COLUMN wkf_slot_identifier.deleted_by IS '删除人';
COMMENT ON COLUMN wkf_slot_identifier.version IS '乐观锁版本号';
COMMENT ON COLUMN wkf_slot_identifier.slot_id IS '所属号源时段标识';
COMMENT ON COLUMN wkf_slot_identifier.id_system IS '标识体系 URI';
COMMENT ON COLUMN wkf_slot_identifier.id_value IS '标识值';
COMMENT ON COLUMN wkf_slot_identifier.use_code IS '用途编码';
