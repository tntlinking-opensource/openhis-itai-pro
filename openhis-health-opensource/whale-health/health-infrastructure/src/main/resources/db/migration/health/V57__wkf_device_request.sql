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
COMMENT ON COLUMN wkf_device_request.bus_no IS '业务编号';
COMMENT ON COLUMN wkf_device_request.prescription_no IS '处方编号';
COMMENT ON COLUMN wkf_device_request.activity_id IS '活动标识';
COMMENT ON COLUMN wkf_device_request.package_id IS 'package标识';
COMMENT ON COLUMN wkf_device_request.status_enum IS '状态枚举值';
COMMENT ON COLUMN wkf_device_request.intent_code IS 'intent编码';
COMMENT ON COLUMN wkf_device_request.category_enum IS 'category枚举值';
COMMENT ON COLUMN wkf_device_request.priority_enum IS 'priority枚举值';
COMMENT ON COLUMN wkf_device_request.perform_flag IS 'perform标志位';
COMMENT ON COLUMN wkf_device_request.group_no IS '分组编号';
COMMENT ON COLUMN wkf_device_request.device_type_code IS '设备类型编码';
COMMENT ON COLUMN wkf_device_request.quantity IS '数量';
COMMENT ON COLUMN wkf_device_request.unit_code IS '单元编码';
COMMENT ON COLUMN wkf_device_request.lot_number IS '批号';
COMMENT ON COLUMN wkf_device_request.device_def_id IS '设备def标识';
COMMENT ON COLUMN wkf_device_request.device_specifications IS '设备specifications';
COMMENT ON COLUMN wkf_device_request.requester_id IS 'requester标识';
COMMENT ON COLUMN wkf_device_request.org_id IS '机构标识';
COMMENT ON COLUMN wkf_device_request.location_id IS '位置/科室标识';
COMMENT ON COLUMN wkf_device_request.perform_location IS 'perform位置';
COMMENT ON COLUMN wkf_device_request.encounter_id IS '就诊标识';
COMMENT ON COLUMN wkf_device_request.patient_id IS '患者标识';
COMMENT ON COLUMN wkf_device_request.rate_code IS 'rate编码';
COMMENT ON COLUMN wkf_device_request.use_time IS 'use时间';
COMMENT ON COLUMN wkf_device_request.use_start_time IS 'use开始时间';
COMMENT ON COLUMN wkf_device_request.use_end_time IS 'use结束时间';
COMMENT ON COLUMN wkf_device_request.use_timing IS '用药时机';
COMMENT ON COLUMN wkf_device_request.req_authored_time IS 'reqauthored时间';
COMMENT ON COLUMN wkf_device_request.performer_enum IS 'performer枚举值';
COMMENT ON COLUMN wkf_device_request.performer_id IS 'performer标识';
COMMENT ON COLUMN wkf_device_request.perform_org_id IS 'performorg标识';
COMMENT ON COLUMN wkf_device_request.as_need_flag IS 'asneed标志位';
COMMENT ON COLUMN wkf_device_request.as_need_reason IS 'asneed原因';
COMMENT ON COLUMN wkf_device_request.contract_code IS '合同编码';
COMMENT ON COLUMN wkf_device_request.refund_device_id IS 'refund设备标识';
COMMENT ON COLUMN wkf_device_request.yb_class_enum IS '医保class枚举值';
COMMENT ON COLUMN wkf_device_request.trace_no IS '追踪编号';
COMMENT ON COLUMN wkf_device_request.condition_definition_id IS '耗材请求聚合根';
COMMENT ON COLUMN wkf_device_request.encounter_diagnosis_id IS '就诊诊断标识';
COMMENT ON COLUMN wkf_device_request.observation_id_json IS '观察项ID JSON';
COMMENT ON COLUMN wkf_device_request.support_info IS '辅助信息';
COMMENT ON COLUMN wkf_device_request.based_on_table IS '依据表名';
COMMENT ON COLUMN wkf_device_request.based_on_id IS 'basedon标识';
COMMENT ON COLUMN wkf_device_request.generate_source_enum IS 'generate来源枚举值';
COMMENT ON COLUMN wkf_device_request.remark IS '备注';
COMMENT ON COLUMN wkf_device_request.sort_number IS '排序号';
