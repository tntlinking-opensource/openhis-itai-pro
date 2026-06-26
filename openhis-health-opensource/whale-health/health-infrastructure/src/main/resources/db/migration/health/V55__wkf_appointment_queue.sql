-- wkf_appointment_queue（建表）
-- Flyway V61

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
