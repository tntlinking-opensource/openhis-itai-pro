-- wkf_device_request（建表）
-- Flyway V63

CREATE TABLE IF NOT EXISTS "public"."wkf_device_request" (
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
    "bus_no" varchar(255),
    "prescription_no" varchar(255),
    "activity_id" int8,
    "package_id" int8,
    "status_enum" varchar(255) NOT NULL,
    "intent_code" varchar(255),
    "category_enum" varchar(255),
    "priority_enum" varchar(255),
    "perform_flag" bool,
    "group_no" varchar(255),
    "device_type_code" varchar(255),
    "quantity" numeric(18,2) NOT NULL,
    "unit_code" varchar(255),
    "lot_number" varchar(255),
    "device_def_id" int8 NOT NULL,
    "device_specifications" varchar(255),
    "requester_id" int8,
    "org_id" int8,
    "location_id" int8,
    "perform_location" int8,
    "encounter_id" int8 NOT NULL,
    "patient_id" int8 NOT NULL,
    "rate_code" varchar(255),
    "use_time" timestamptz(6),
    "use_start_time" timestamptz(6),
    "use_end_time" timestamptz(6),
    "use_timing" varchar(255),
    "req_authored_time" timestamptz(6),
    "performer_enum" varchar(255),
    "performer_id" int8,
    "perform_org_id" int8,
    "as_need_flag" bool,
    "as_need_reason" varchar(255),
    "contract_code" varchar(255),
    "refund_device_id" int8,
    "yb_class_enum" varchar(255),
    "trace_no" varchar(50000),
    "condition_definition_id" int8,
    "encounter_diagnosis_id" int8,
    "observation_id_json" varchar(255),
    "support_info" varchar(255),
    "based_on_table" varchar(255),
    "based_on_id" int8,
    "generate_source_enum" varchar(255),
    "remark" varchar(255),
    "sort_number" int4,
    CONSTRAINT "wkf_device_request_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE wkf_device_request IS '工作流 device request';

COMMENT ON COLUMN wkf_device_request.id IS '主键';
COMMENT ON COLUMN wkf_device_request.tenant_id IS '租户标识';
COMMENT ON COLUMN wkf_device_request.created_at IS '创建时间';
COMMENT ON COLUMN wkf_device_request.updated_at IS '最后更新时间';
COMMENT ON COLUMN wkf_device_request.created_by IS '创建人';
COMMENT ON COLUMN wkf_device_request.updated_by IS '最后更新人';
COMMENT ON COLUMN wkf_device_request.is_deleted IS '软删标记';
COMMENT ON COLUMN wkf_device_request.deleted_at IS '删除时间';
COMMENT ON COLUMN wkf_device_request.deleted_by IS '删除人';
COMMENT ON COLUMN wkf_device_request.version IS '乐观锁版本号';
COMMENT ON COLUMN wkf_device_request.bus_no IS 'bus no';
COMMENT ON COLUMN wkf_device_request.prescription_no IS 'prescription no';
COMMENT ON COLUMN wkf_device_request.activity_id IS 'activity id';
COMMENT ON COLUMN wkf_device_request.package_id IS 'package id';
COMMENT ON COLUMN wkf_device_request.status_enum IS 'status enum';
COMMENT ON COLUMN wkf_device_request.intent_code IS 'intent code';
COMMENT ON COLUMN wkf_device_request.category_enum IS 'category enum';
COMMENT ON COLUMN wkf_device_request.priority_enum IS 'priority enum';
COMMENT ON COLUMN wkf_device_request.perform_flag IS 'perform flag';
COMMENT ON COLUMN wkf_device_request.group_no IS 'group no';
COMMENT ON COLUMN wkf_device_request.device_type_code IS 'device type code';
COMMENT ON COLUMN wkf_device_request.quantity IS 'quantity';
COMMENT ON COLUMN wkf_device_request.unit_code IS 'unit code';
COMMENT ON COLUMN wkf_device_request.lot_number IS 'lot number';
COMMENT ON COLUMN wkf_device_request.device_def_id IS 'device def id';
COMMENT ON COLUMN wkf_device_request.device_specifications IS 'device specifications';
COMMENT ON COLUMN wkf_device_request.requester_id IS 'requester id';
COMMENT ON COLUMN wkf_device_request.org_id IS 'org id';
COMMENT ON COLUMN wkf_device_request.location_id IS 'location id';
COMMENT ON COLUMN wkf_device_request.perform_location IS 'perform location';
COMMENT ON COLUMN wkf_device_request.encounter_id IS 'encounter id';
COMMENT ON COLUMN wkf_device_request.patient_id IS 'patient id';
COMMENT ON COLUMN wkf_device_request.rate_code IS 'rate code';
COMMENT ON COLUMN wkf_device_request.use_time IS 'use time';
COMMENT ON COLUMN wkf_device_request.use_start_time IS 'use start time';
COMMENT ON COLUMN wkf_device_request.use_end_time IS 'use end time';
COMMENT ON COLUMN wkf_device_request.use_timing IS 'use timing';
COMMENT ON COLUMN wkf_device_request.req_authored_time IS 'req authored time';
COMMENT ON COLUMN wkf_device_request.performer_enum IS 'performer enum';
COMMENT ON COLUMN wkf_device_request.performer_id IS 'performer id';
COMMENT ON COLUMN wkf_device_request.perform_org_id IS 'perform org id';
COMMENT ON COLUMN wkf_device_request.as_need_flag IS 'as need flag';
COMMENT ON COLUMN wkf_device_request.as_need_reason IS 'as need reason';
COMMENT ON COLUMN wkf_device_request.contract_code IS 'contract code';
COMMENT ON COLUMN wkf_device_request.refund_device_id IS 'refund device id';
COMMENT ON COLUMN wkf_device_request.yb_class_enum IS 'yb class enum';
COMMENT ON COLUMN wkf_device_request.trace_no IS 'trace no';
COMMENT ON COLUMN wkf_device_request.condition_definition_id IS '耗材请求聚合根';
COMMENT ON COLUMN wkf_device_request.encounter_diagnosis_id IS 'encounter diagnosis id';
COMMENT ON COLUMN wkf_device_request.observation_id_json IS 'observation id json';
COMMENT ON COLUMN wkf_device_request.support_info IS 'support info';
COMMENT ON COLUMN wkf_device_request.based_on_table IS 'based on table';
COMMENT ON COLUMN wkf_device_request.based_on_id IS 'based on id';
COMMENT ON COLUMN wkf_device_request.generate_source_enum IS 'generate source enum';
COMMENT ON COLUMN wkf_device_request.remark IS 'remark';
COMMENT ON COLUMN wkf_device_request.sort_number IS 'sort number';

