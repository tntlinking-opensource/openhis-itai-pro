-- Squashed V6 wkf_ddl

-- from V52__wkf_activity_definition.sql
-- wkf_activity_definition（建表 + 种子数据）
-- Flyway V59

CREATE TABLE IF NOT EXISTS "public"."wkf_activity_definition" (
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
    "category_code" varchar(255) NOT NULL,
    "bus_no" varchar(255) NOT NULL,
    "name" varchar(255) NOT NULL,
    "py_str" varchar(255),
    "wb_str" varchar(255),
    "package_unit_code" varchar(255),
    "type_enum" varchar(255) NOT NULL,
    "status_enum" varchar(255) NOT NULL,
    "description_text" varchar(255),
    "body_site_code" varchar(255),
    "specimen_code" varchar(255),
    "org_id" int8,
    "location_id" int8,
    "rule_id" int4,
    "pricing_flag" bool,
    "yb_flag" bool,
    "yb_no" varchar(255),
    "yb_match_flag" bool,
    "chrgitm_lv" int4,
    "mr_settlement_type_code" varchar(255),
    "children_json" varchar(255),
    "children_flag" bool,
    "activity_version" varchar(255),
    "merchandise_name" varchar(255),
    "merchandise_py_str" varchar(255),
    "merchandise_wb_str" varchar(255),
    "def_encounter_unit_code" varchar(255),
    "def_inhospital_unit_code" varchar(255),
    CONSTRAINT "wkf_activity_definition_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE wkf_activity_definition IS '工作流 activity definition';
COMMENT ON COLUMN wkf_activity_definition.id IS '主键';
COMMENT ON COLUMN wkf_activity_definition.tenant_id IS '租户标识';
COMMENT ON COLUMN wkf_activity_definition.created_at IS '创建时间';
COMMENT ON COLUMN wkf_activity_definition.updated_at IS '最后更新时间';
COMMENT ON COLUMN wkf_activity_definition.created_by IS '创建人';
COMMENT ON COLUMN wkf_activity_definition.updated_by IS '最后更新人';
COMMENT ON COLUMN wkf_activity_definition.is_deleted IS '软删标记';
COMMENT ON COLUMN wkf_activity_definition.deleted_at IS '删除时间';
COMMENT ON COLUMN wkf_activity_definition.deleted_by IS '删除人';
COMMENT ON COLUMN wkf_activity_definition.version IS '乐观锁版本号';
COMMENT ON COLUMN wkf_activity_definition.category_code IS '诊疗项目定义聚合根';
COMMENT ON COLUMN wkf_activity_definition.bus_no IS '业务编码（全局唯一）';
COMMENT ON COLUMN wkf_activity_definition.name IS '项目名称';
COMMENT ON COLUMN wkf_activity_definition.py_str IS '拼音检索码';
COMMENT ON COLUMN wkf_activity_definition.wb_str IS '五笔检索码';
COMMENT ON COLUMN wkf_activity_definition.package_unit_code IS '销售/计价单位编码';
COMMENT ON COLUMN wkf_activity_definition.type_enum IS '诊疗类型枚举编码';
COMMENT ON COLUMN wkf_activity_definition.status_enum IS '发布状态枚举编码';
COMMENT ON COLUMN wkf_activity_definition.description_text IS '说明文本';
COMMENT ON COLUMN wkf_activity_definition.body_site_code IS '身体部位编码';
COMMENT ON COLUMN wkf_activity_definition.specimen_code IS '标本编码';
COMMENT ON COLUMN wkf_activity_definition.org_id IS '所属机构标识';
COMMENT ON COLUMN wkf_activity_definition.location_id IS '位置标识';
COMMENT ON COLUMN wkf_activity_definition.rule_id IS '计费规则标识';
COMMENT ON COLUMN wkf_activity_definition.pricing_flag IS '划价标记';
COMMENT ON COLUMN wkf_activity_definition.yb_flag IS '医保标记';
COMMENT ON COLUMN wkf_activity_definition.yb_no IS '医保编码';
COMMENT ON COLUMN wkf_activity_definition.yb_match_flag IS '医保对码标记';
COMMENT ON COLUMN wkf_activity_definition.chrgitm_lv IS '医保收费等级';
COMMENT ON COLUMN wkf_activity_definition.mr_settlement_type_code IS '病案结算类型（术语 mr_settlement_type_code）';
COMMENT ON COLUMN wkf_activity_definition.children_json IS '子项 JSON 快照';
COMMENT ON COLUMN wkf_activity_definition.children_flag IS '子项标记（需与子项子表事实一致）';
COMMENT ON COLUMN wkf_activity_definition.activity_version IS '诊疗项目业务版本（非乐观锁 version）';
COMMENT ON COLUMN wkf_activity_definition.merchandise_name IS '商品名称（可与项目名称分列维护）';
COMMENT ON COLUMN wkf_activity_definition.merchandise_py_str IS '商品名拼音检索码';
COMMENT ON COLUMN wkf_activity_definition.merchandise_wb_str IS '商品名五笔检索码';
COMMENT ON COLUMN wkf_activity_definition.def_encounter_unit_code IS '默认门诊/就诊单位编码';
COMMENT ON COLUMN wkf_activity_definition.def_inhospital_unit_code IS '默认住院单位编码';

-- seed: wkf_activity_definition
-- ----- seed sync from DB (wkf_activity_definition, 7 rows) -----

-- from V53__wkf_activity_definition_children.sql
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

-- from V54__wkf_appointment_queue.sql
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
    "healthcare_service_id" int8,
    "booking_date" date,
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

-- from V55__wkf_device_dispense.sql
-- wkf_device_dispense（建表）
-- Flyway V62

CREATE TABLE IF NOT EXISTS "public"."wkf_device_dispense" (
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
    "bus_no" varchar(255) NOT NULL,
    "device_req_id" int8 NOT NULL,
    "status_enum" varchar(255) NOT NULL,
    "based_on_table" varchar(255),
    "based_on_id" int8,
    "not_performed_reason_enum" varchar(255),
    "dispense_category_enum" varchar(255),
    "quantity" numeric(18,2) NOT NULL,
    "unit_code" varchar(255) NOT NULL,
    "lot_number" varchar(255),
    "device_def_id" int8 NOT NULL,
    "patient_id" int8 NOT NULL,
    "encounter_id" int8 NOT NULL,
    "performer_id" int8,
    "location_id" int8,
    "part_type_code" varchar(255),
    "dispense_quantity" numeric(18,2),
    "preparer_id" int8,
    "prepare_time" timestamptz(6),
    "dispense_time" timestamptz(6),
    "limit_time" timestamptz(6),
    "planned_dispense_time" timestamptz(6),
    "des_location_id" varchar(255),
    "rec_practitioner_id" varchar(255),
    "support_info" varchar(255),
    "usage_instruction" varchar(255),
    "dispense_frequency_code" varchar(255),
    "trace_no" varchar(50000),
    "procedure_id" int8,
    CONSTRAINT "wkf_device_dispense_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE wkf_device_dispense IS '工作流 device dispense';
COMMENT ON COLUMN wkf_device_dispense.id IS '主键';
COMMENT ON COLUMN wkf_device_dispense.tenant_id IS '租户标识';
COMMENT ON COLUMN wkf_device_dispense.created_at IS '创建时间';
COMMENT ON COLUMN wkf_device_dispense.updated_at IS '最后更新时间';
COMMENT ON COLUMN wkf_device_dispense.created_by IS '创建人';
COMMENT ON COLUMN wkf_device_dispense.updated_by IS '最后更新人';
COMMENT ON COLUMN wkf_device_dispense.is_deleted IS '软删标记';
COMMENT ON COLUMN wkf_device_dispense.deleted_at IS '删除时间';
COMMENT ON COLUMN wkf_device_dispense.deleted_by IS '删除人';
COMMENT ON COLUMN wkf_device_dispense.version IS '乐观锁版本号';
COMMENT ON COLUMN wkf_device_dispense.bus_no IS '业务编号';
COMMENT ON COLUMN wkf_device_dispense.device_req_id IS '设备req标识';
COMMENT ON COLUMN wkf_device_dispense.status_enum IS '状态枚举值';
COMMENT ON COLUMN wkf_device_dispense.based_on_table IS '依据表名';
COMMENT ON COLUMN wkf_device_dispense.based_on_id IS 'basedon标识';
COMMENT ON COLUMN wkf_device_dispense.not_performed_reason_enum IS 'notperformed原因枚举值';
COMMENT ON COLUMN wkf_device_dispense.dispense_category_enum IS '发药category枚举值';
COMMENT ON COLUMN wkf_device_dispense.quantity IS '数量';
COMMENT ON COLUMN wkf_device_dispense.unit_code IS '单元编码';
COMMENT ON COLUMN wkf_device_dispense.lot_number IS '批号';
COMMENT ON COLUMN wkf_device_dispense.device_def_id IS '设备def标识';
COMMENT ON COLUMN wkf_device_dispense.patient_id IS '患者标识';
COMMENT ON COLUMN wkf_device_dispense.encounter_id IS '就诊标识';
COMMENT ON COLUMN wkf_device_dispense.performer_id IS 'performer标识';
COMMENT ON COLUMN wkf_device_dispense.location_id IS '位置/科室标识';
COMMENT ON COLUMN wkf_device_dispense.part_type_code IS 'part类型编码';
COMMENT ON COLUMN wkf_device_dispense.dispense_quantity IS '发药数量';
COMMENT ON COLUMN wkf_device_dispense.preparer_id IS 'preparer标识';
COMMENT ON COLUMN wkf_device_dispense.prepare_time IS 'prepare时间';
COMMENT ON COLUMN wkf_device_dispense.dispense_time IS '发药时间';
COMMENT ON COLUMN wkf_device_dispense.limit_time IS 'limit时间';
COMMENT ON COLUMN wkf_device_dispense.planned_dispense_time IS 'planned发药时间';
COMMENT ON COLUMN wkf_device_dispense.des_location_id IS 'des位置标识';
COMMENT ON COLUMN wkf_device_dispense.rec_practitioner_id IS 'rec医护人员标识';
COMMENT ON COLUMN wkf_device_dispense.support_info IS '辅助信息';
COMMENT ON COLUMN wkf_device_dispense.usage_instruction IS '用法说明';
COMMENT ON COLUMN wkf_device_dispense.dispense_frequency_code IS '发药频次编码';
COMMENT ON COLUMN wkf_device_dispense.trace_no IS '追踪编号';
COMMENT ON COLUMN wkf_device_dispense.procedure_id IS '处置标识';

-- from V56__wkf_device_request.sql
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
    "req_opened_time" timestamptz(6),
    "req_authored_time" timestamptz(6),
    "stop_time" timestamptz(6),
    "stopper_id" int8,
    "performer_enum" varchar(255),
    "performer_id" int8,
    "perform_org_id" int8,
    "as_need_flag" bool,
    "as_need_reason" varchar(255),
    "contract_code" varchar(255),
    "refund_device_id" int8,
    "yb_class_enum" varchar(255),
    "trace_no" varchar(50000),
    "encounter_diagnosis_ids" varchar(2000),
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
COMMENT ON COLUMN wkf_device_request.req_opened_time IS '开立时间（首次建草稿）';
COMMENT ON COLUMN wkf_device_request.req_authored_time IS '签发时间';
COMMENT ON COLUMN wkf_device_request.stop_time IS '停嘱时间';
COMMENT ON COLUMN wkf_device_request.stopper_id IS '停嘱人（医务人员主键）';
COMMENT ON COLUMN wkf_device_request.performer_enum IS 'performer枚举值';
COMMENT ON COLUMN wkf_device_request.performer_id IS 'performer标识';
COMMENT ON COLUMN wkf_device_request.perform_org_id IS 'performorg标识';
COMMENT ON COLUMN wkf_device_request.as_need_flag IS 'asneed标志位';
COMMENT ON COLUMN wkf_device_request.as_need_reason IS 'asneed原因';
COMMENT ON COLUMN wkf_device_request.contract_code IS '合同编码';
COMMENT ON COLUMN wkf_device_request.refund_device_id IS 'refund设备标识';
COMMENT ON COLUMN wkf_device_request.yb_class_enum IS '医保class枚举值';
COMMENT ON COLUMN wkf_device_request.trace_no IS '追踪编号';
COMMENT ON COLUMN wkf_device_request.encounter_diagnosis_ids IS '绑定就诊诊断主键 CSV（有序，首位为主诊断 adm_encounter_diagnosis.id）';
COMMENT ON COLUMN wkf_device_request.observation_id_json IS '观察项ID JSON';
COMMENT ON COLUMN wkf_device_request.support_info IS '辅助信息';
COMMENT ON COLUMN wkf_device_request.based_on_table IS '依据表名';
COMMENT ON COLUMN wkf_device_request.based_on_id IS 'basedon标识';
COMMENT ON COLUMN wkf_device_request.generate_source_enum IS 'generate来源枚举值';
COMMENT ON COLUMN wkf_device_request.remark IS '备注';
COMMENT ON COLUMN wkf_device_request.sort_number IS '排序号';

-- from V57__wkf_inventory_item.sql
-- wkf_inventory_item（建表）
-- Flyway V64

CREATE TABLE IF NOT EXISTS "public"."wkf_inventory_item" (
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
    "category_code" varchar(255),
    "item_table" varchar(255) NOT NULL,
    "item_id" int8 NOT NULL,
    "name" varchar(255) NOT NULL,
    "py_str" varchar(255),
    "wb_str" varchar(255),
    "inventory_status_enum" varchar(255) NOT NULL,
    "supplier_id" int8 NOT NULL,
    "description_text" varchar(255),
    "unit_code" varchar(255) NOT NULL,
    "quantity" numeric(18,2) NOT NULL,
    "characteristic_json" varchar(255),
    "lot_number" varchar(255) NOT NULL,
    "production_date" timestamptz(6),
    "expiration_date" timestamptz(6),
    "validity_mon" int4,
    "location_store_id" int8,
    "location_id" int8 NOT NULL,
    "trace_no" varchar(50000),
    "packaging_levels" int4,
    CONSTRAINT "wkf_inventory_item_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE wkf_inventory_item IS '工作流 inventory item';
COMMENT ON COLUMN wkf_inventory_item.id IS '主键';
COMMENT ON COLUMN wkf_inventory_item.tenant_id IS '租户标识';
COMMENT ON COLUMN wkf_inventory_item.created_at IS '创建时间';
COMMENT ON COLUMN wkf_inventory_item.updated_at IS '最后更新时间';
COMMENT ON COLUMN wkf_inventory_item.created_by IS '创建人';
COMMENT ON COLUMN wkf_inventory_item.updated_by IS '最后更新人';
COMMENT ON COLUMN wkf_inventory_item.is_deleted IS '软删标记';
COMMENT ON COLUMN wkf_inventory_item.deleted_at IS '删除时间';
COMMENT ON COLUMN wkf_inventory_item.deleted_by IS '删除人';
COMMENT ON COLUMN wkf_inventory_item.version IS '乐观锁版本号';
COMMENT ON COLUMN wkf_inventory_item.category_code IS '库存项聚合根（DM-WKF-008）';
COMMENT ON COLUMN wkf_inventory_item.item_table IS '物品定义表名';
COMMENT ON COLUMN wkf_inventory_item.item_id IS '物品定义标识';
COMMENT ON COLUMN wkf_inventory_item.name IS '物品名称（冗余）';
COMMENT ON COLUMN wkf_inventory_item.py_str IS '拼音码';
COMMENT ON COLUMN wkf_inventory_item.wb_str IS '五笔码';
COMMENT ON COLUMN wkf_inventory_item.inventory_status_enum IS '库存状态（ACTIVE/INACTIVE），与 PublicationStatus 编码对齐';
COMMENT ON COLUMN wkf_inventory_item.supplier_id IS '供应商标识';
COMMENT ON COLUMN wkf_inventory_item.description_text IS '描述文本';
COMMENT ON COLUMN wkf_inventory_item.unit_code IS '计量单位编码';
COMMENT ON COLUMN wkf_inventory_item.quantity IS '当前库存数量';
COMMENT ON COLUMN wkf_inventory_item.characteristic_json IS '特征扩展 JSON';
COMMENT ON COLUMN wkf_inventory_item.lot_number IS '产品批号';
COMMENT ON COLUMN wkf_inventory_item.production_date IS '生产日期';
COMMENT ON COLUMN wkf_inventory_item.expiration_date IS '效期';
COMMENT ON COLUMN wkf_inventory_item.validity_mon IS '有效期月数';
COMMENT ON COLUMN wkf_inventory_item.location_store_id IS '货位标识';
COMMENT ON COLUMN wkf_inventory_item.location_id IS '库房标识';
COMMENT ON COLUMN wkf_inventory_item.trace_no IS '追溯码';
COMMENT ON COLUMN wkf_inventory_item.packaging_levels IS '追溯码包装层级';

-- seed: wkf_inventory_item（现网库存批次，46 条）
-- ----- seed sync from DB (wkf_inventory_item, 3 rows) -----

-- from V58__wkf_schedule.sql
-- wkf_schedule（建表）
-- Flyway V65

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
    "source_schedule_template_id" int8,
    "slot_provision_template_id" int8,
    CONSTRAINT "wkf_schedule_pkey" PRIMARY KEY ("id")
);
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

-- from V59__wkf_schedule_actor.sql
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

-- from V60__wkf_schedule_identifier.sql
-- wkf_schedule_identifier（建表）
-- Flyway V67

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

-- from V61__wkf_service_request.sql
-- wkf_service_request（建表）
-- Flyway V68

CREATE TABLE IF NOT EXISTS "public"."wkf_service_request" (
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
    "based_on_table" varchar(255),
    "based_on_id" int8,
    "requisition_id" int8,
    "status_enum" varchar(255) NOT NULL,
    "intent_enum" varchar(255),
    "category_enum" varchar(255) NOT NULL,
    "group_id" int8,
    "priority_enum" varchar(255),
    "perform_flag" bool,
    "activity_id" int8 NOT NULL,
    "quantity" numeric(18,2) NOT NULL,
    "unit_code" varchar(255) NOT NULL,
    "patient_id" int8 NOT NULL,
    "encounter_id" int8 NOT NULL,
    "req_opened_time" timestamptz(6),
    "req_authored_time" timestamptz(6),
    "requester_id" int8 NOT NULL,
    "performer_type_code" varchar(255),
    "performer_id" int8,
    "performer_check_id" int8,
    "check_time" timestamptz(6),
    "stop_performer_check_id" int8,
    "stop_check_time" timestamptz(6),
    "location_id" int8,
    "org_id" int8,
    "reason_text" varchar(255),
    "contract_id" int8,
    "occurrence_start_time" timestamptz(6),
    "occurrence_end_time" timestamptz(6),
    "stop_time" timestamptz(6),
    "stopper_id" int8,
    "relevant_history_id" int8,
    "refund_service_id" int8,
    "content_json" varchar(255),
    "yb_class_enum" varchar(255),
    "print_count" int4,
    "encounter_diagnosis_ids" varchar(2000),
    "therapy_enum" varchar(255),
    "rate_code" varchar(255),
    "parent_id" int8,
    "generate_source_enum" varchar(255),
    "remark" varchar(255),
    "entrust_content" varchar(255),
    "sort_number" int4,
    "execution_count" int4,
    CONSTRAINT "wkf_service_request_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE wkf_service_request IS '工作流 service request';
COMMENT ON COLUMN wkf_service_request.id IS '主键';
COMMENT ON COLUMN wkf_service_request.tenant_id IS '租户标识';
COMMENT ON COLUMN wkf_service_request.created_at IS '创建时间';
COMMENT ON COLUMN wkf_service_request.updated_at IS '最后更新时间';
COMMENT ON COLUMN wkf_service_request.created_by IS '创建人';
COMMENT ON COLUMN wkf_service_request.updated_by IS '最后更新人';
COMMENT ON COLUMN wkf_service_request.is_deleted IS '软删标记';
COMMENT ON COLUMN wkf_service_request.deleted_at IS '删除时间';
COMMENT ON COLUMN wkf_service_request.deleted_by IS '删除人';
COMMENT ON COLUMN wkf_service_request.version IS '乐观锁版本号';
COMMENT ON COLUMN wkf_service_request.bus_no IS '业务编号';
COMMENT ON COLUMN wkf_service_request.prescription_no IS '处方编号';
COMMENT ON COLUMN wkf_service_request.based_on_table IS '依据表名';
COMMENT ON COLUMN wkf_service_request.based_on_id IS 'basedon标识';
COMMENT ON COLUMN wkf_service_request.requisition_id IS 'requisition标识';
COMMENT ON COLUMN wkf_service_request.status_enum IS '状态枚举值';
COMMENT ON COLUMN wkf_service_request.intent_enum IS 'intent枚举值';
COMMENT ON COLUMN wkf_service_request.category_enum IS 'category枚举值';
COMMENT ON COLUMN wkf_service_request.group_id IS '分组标识';
COMMENT ON COLUMN wkf_service_request.priority_enum IS 'priority枚举值';
COMMENT ON COLUMN wkf_service_request.perform_flag IS 'perform标志位';
COMMENT ON COLUMN wkf_service_request.activity_id IS '活动标识';
COMMENT ON COLUMN wkf_service_request.quantity IS '数量';
COMMENT ON COLUMN wkf_service_request.unit_code IS '单元编码';
COMMENT ON COLUMN wkf_service_request.patient_id IS '患者标识';
COMMENT ON COLUMN wkf_service_request.encounter_id IS '就诊标识';
COMMENT ON COLUMN wkf_service_request.req_opened_time IS '开立时间（首次建草稿）';
COMMENT ON COLUMN wkf_service_request.req_authored_time IS '签发时间';
COMMENT ON COLUMN wkf_service_request.requester_id IS 'requester标识';
COMMENT ON COLUMN wkf_service_request.performer_type_code IS 'performer类型编码';
COMMENT ON COLUMN wkf_service_request.performer_id IS 'performer标识';
COMMENT ON COLUMN wkf_service_request.performer_check_id IS 'performercheck标识';
COMMENT ON COLUMN wkf_service_request.check_time IS 'check时间';
COMMENT ON COLUMN wkf_service_request.stop_performer_check_id IS '停嘱校对人（护士 Practitioner）；空表示停嘱待校或未停嘱';
COMMENT ON COLUMN wkf_service_request.stop_check_time IS '停嘱校对时间；与 stop_performer_check_id 成对';
COMMENT ON COLUMN wkf_service_request.location_id IS '位置/科室标识';
COMMENT ON COLUMN wkf_service_request.org_id IS '机构标识';
COMMENT ON COLUMN wkf_service_request.reason_text IS '原因文本';
COMMENT ON COLUMN wkf_service_request.contract_id IS '合同标识';
COMMENT ON COLUMN wkf_service_request.occurrence_start_time IS 'occurrence开始时间';
COMMENT ON COLUMN wkf_service_request.occurrence_end_time IS '计划结束时间';
COMMENT ON COLUMN wkf_service_request.stop_time IS '停嘱时间';
COMMENT ON COLUMN wkf_service_request.stopper_id IS '停嘱人（医务人员主键）';
COMMENT ON COLUMN wkf_service_request.relevant_history_id IS 'relevanthistory标识';
COMMENT ON COLUMN wkf_service_request.refund_service_id IS 'refund服务标识';
COMMENT ON COLUMN wkf_service_request.content_json IS '内容JSON';
COMMENT ON COLUMN wkf_service_request.yb_class_enum IS '医保class枚举值';
COMMENT ON COLUMN wkf_service_request.print_count IS '打印count';
COMMENT ON COLUMN wkf_service_request.encounter_diagnosis_ids IS '绑定就诊诊断主键 CSV（有序，首位为主诊断 adm_encounter_diagnosis.id）';
COMMENT ON COLUMN wkf_service_request.therapy_enum IS 'therapy枚举值';
COMMENT ON COLUMN wkf_service_request.rate_code IS 'rate编码';
COMMENT ON COLUMN wkf_service_request.parent_id IS 'parent标识';
COMMENT ON COLUMN wkf_service_request.generate_source_enum IS 'generate来源枚举值';
COMMENT ON COLUMN wkf_service_request.remark IS '备注';
COMMENT ON COLUMN wkf_service_request.entrust_content IS '嘱托内容';
COMMENT ON COLUMN wkf_service_request.sort_number IS '排序号';
COMMENT ON COLUMN wkf_service_request.execution_count IS '服务申请聚合根（非药品医嘱）';

-- from V62__wkf_service_request_detail.sql
-- wkf_service_request_detail（建表）
-- Flyway V69

CREATE TABLE IF NOT EXISTS "public"."wkf_service_request_detail" (
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
    "service_req_id" int8 NOT NULL,
    "target_table" varchar(64),
    "target_id" int8,
    "remark" varchar(255),
    CONSTRAINT "wkf_service_request_detail_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE wkf_service_request_detail IS '工作流 service request detail';
COMMENT ON COLUMN wkf_service_request_detail.id IS '主键';
COMMENT ON COLUMN wkf_service_request_detail.tenant_id IS '租户标识';
COMMENT ON COLUMN wkf_service_request_detail.created_at IS '创建时间';
COMMENT ON COLUMN wkf_service_request_detail.updated_at IS '最后更新时间';
COMMENT ON COLUMN wkf_service_request_detail.created_by IS '创建人';
COMMENT ON COLUMN wkf_service_request_detail.updated_by IS '最后更新人';
COMMENT ON COLUMN wkf_service_request_detail.is_deleted IS '软删标记';
COMMENT ON COLUMN wkf_service_request_detail.deleted_at IS '删除时间';
COMMENT ON COLUMN wkf_service_request_detail.deleted_by IS '删除人';
COMMENT ON COLUMN wkf_service_request_detail.version IS '乐观锁版本号';
COMMENT ON COLUMN wkf_service_request_detail.service_req_id IS '服务申请明细子实体';
COMMENT ON COLUMN wkf_service_request_detail.target_table IS '明细目标表名（如 dia_body_structure、adm_specimen_definition）';
COMMENT ON COLUMN wkf_service_request_detail.target_id IS '明细目标表主键';
COMMENT ON COLUMN wkf_service_request_detail.remark IS '明细备注';

-- from V63__wkf_slot.sql
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

-- from V64__wkf_slot_identifier.sql
-- wkf_slot_identifier（建表）
-- Flyway V71

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

-- from V65__wkf_supply_delivery.sql
-- wkf_supply_delivery（建表）
-- Flyway V72

CREATE TABLE IF NOT EXISTS "public"."wkf_supply_delivery" (
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
    "request_id" int8,
    "summary_id" int8,
    "status_enum" varchar(255) NOT NULL,
    "patient_id" int8,
    "type_enum" varchar(255) NOT NULL,
    "item_table" varchar(255),
    "item_id" int8,
    "based_on_table" varchar(255),
    "based_on_ids" varchar(255),
    "unit_code" varchar(255),
    "quantity" numeric(18,2),
    "condition" varchar(255),
    "lot_number" varchar(255),
    "trace_no" varchar(50000),
    "supplier_id" int8,
    "practitioner_id" int8,
    "occurrence_time" timestamptz(6),
    "receiver_id" int8,
    "receive_time" timestamptz(6),
    CONSTRAINT "wkf_supply_delivery_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE wkf_supply_delivery IS '工作流 supply delivery';
COMMENT ON COLUMN wkf_supply_delivery.id IS '主键';
COMMENT ON COLUMN wkf_supply_delivery.tenant_id IS '租户标识';
COMMENT ON COLUMN wkf_supply_delivery.created_at IS '创建时间';
COMMENT ON COLUMN wkf_supply_delivery.updated_at IS '最后更新时间';
COMMENT ON COLUMN wkf_supply_delivery.created_by IS '创建人';
COMMENT ON COLUMN wkf_supply_delivery.updated_by IS '最后更新人';
COMMENT ON COLUMN wkf_supply_delivery.is_deleted IS '软删标记';
COMMENT ON COLUMN wkf_supply_delivery.deleted_at IS '删除时间';
COMMENT ON COLUMN wkf_supply_delivery.deleted_by IS '删除人';
COMMENT ON COLUMN wkf_supply_delivery.version IS '乐观锁版本号';
COMMENT ON COLUMN wkf_supply_delivery.request_id IS '供应发放聚合根';
COMMENT ON COLUMN wkf_supply_delivery.summary_id IS 'summary标识';
COMMENT ON COLUMN wkf_supply_delivery.status_enum IS '发放状态持久化码（如 { DispenseStatus#getCode()}，B.10.8.1）';
COMMENT ON COLUMN wkf_supply_delivery.patient_id IS '患者标识';
COMMENT ON COLUMN wkf_supply_delivery.type_enum IS '发放类型/业务子类型持久化码（与命令及事件载荷一致，B.10.8.1）';
COMMENT ON COLUMN wkf_supply_delivery.item_table IS '项table';
COMMENT ON COLUMN wkf_supply_delivery.item_id IS '项标识';
COMMENT ON COLUMN wkf_supply_delivery.based_on_table IS '依据表名';
COMMENT ON COLUMN wkf_supply_delivery.based_on_ids IS '依据ID列表';
COMMENT ON COLUMN wkf_supply_delivery.unit_code IS '单元编码';
COMMENT ON COLUMN wkf_supply_delivery.quantity IS '数量';
COMMENT ON COLUMN wkf_supply_delivery.condition IS '病情';
COMMENT ON COLUMN wkf_supply_delivery.lot_number IS '批号';
COMMENT ON COLUMN wkf_supply_delivery.trace_no IS '追踪编号';
COMMENT ON COLUMN wkf_supply_delivery.supplier_id IS '供应商标识';
COMMENT ON COLUMN wkf_supply_delivery.practitioner_id IS '医护人员标识';
COMMENT ON COLUMN wkf_supply_delivery.occurrence_time IS 'occurrence时间';
COMMENT ON COLUMN wkf_supply_delivery.receiver_id IS 'receiver标识';
COMMENT ON COLUMN wkf_supply_delivery.receive_time IS 'receive时间';

-- from V66__wkf_supply_request.sql
-- wkf_supply_request（建表）
-- Flyway V73

CREATE TABLE IF NOT EXISTS "public"."wkf_supply_request" (
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
    "bus_no" varchar(255) NOT NULL,
    "original_bus_no" varchar(255),
    "type_enum" varchar(255) NOT NULL,
    "status_enum" varchar(255) NOT NULL,
    "intent_enum" varchar(255),
    "category_enum" varchar(255) NOT NULL,
    "priority_enum" varchar(255),
    "summary_id" int8,
    "deliver_id" int8,
    "patient_id" int8,
    "item_table" varchar(255) NOT NULL,
    "item_quantity" numeric(18,2) NOT NULL,
    "total_quantity" numeric(18,2),
    "item_id" int8 NOT NULL,
    "unit_code" varchar(255) NOT NULL,
    "occurrence_time" timestamptz(6),
    "practitioner_id" int8,
    "supplier_id" int8,
    "reason" varchar(255),
    "source_type_enum" varchar(255),
    "source_location_id" int8,
    "purpose_type_enum" varchar(255),
    "purpose_location_id" int8,
    "approver_id" int8,
    "approval_time" timestamptz(6),
    "applicant_id" int8 NOT NULL,
    "apply_time" timestamptz(6),
    "start_time" timestamptz(6),
    "end_time" timestamptz(6),
    "lot_number" varchar(255),
    "trace_no" varchar(50000),
    "price" numeric(19,6),
    "total_price" numeric(19,6),
    "remake" varchar(255),
    "profit_loss_price" numeric(19, 6),
    "invoice_no" varchar(255) DEFAULT '0',
    "consuming_organization_id" int8,
    CONSTRAINT "wkf_supply_request_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE wkf_supply_request IS '工作流 supply request';
COMMENT ON COLUMN wkf_supply_request.id IS '主键';
COMMENT ON COLUMN wkf_supply_request.tenant_id IS '租户标识';
COMMENT ON COLUMN wkf_supply_request.created_at IS '创建时间';
COMMENT ON COLUMN wkf_supply_request.updated_at IS '最后更新时间';
COMMENT ON COLUMN wkf_supply_request.created_by IS '创建人';
COMMENT ON COLUMN wkf_supply_request.updated_by IS '最后更新人';
COMMENT ON COLUMN wkf_supply_request.is_deleted IS '软删标记';
COMMENT ON COLUMN wkf_supply_request.deleted_at IS '删除时间';
COMMENT ON COLUMN wkf_supply_request.deleted_by IS '删除人';
COMMENT ON COLUMN wkf_supply_request.version IS '乐观锁版本号';
COMMENT ON COLUMN wkf_supply_request.bus_no IS '供应请求聚合根（同表多行：同一 { bus_no} 聚合多行明细，共享审批状态）';
COMMENT ON COLUMN wkf_supply_request.original_bus_no IS '来源业务单号（如进货关联订货）；可空';
COMMENT ON COLUMN wkf_supply_request.type_enum IS '供应类型持久化码（{ SupplyType#getCode()}，B.10.8.1）';
COMMENT ON COLUMN wkf_supply_request.status_enum IS '供应状态持久化码（{ SupplyStatus#getCode()}，B.10.8.1）';
COMMENT ON COLUMN wkf_supply_request.intent_enum IS 'intent枚举值';
COMMENT ON COLUMN wkf_supply_request.category_enum IS '供应类别持久化码（与 { com.openhis.health.domain.share.enums.SupplyCategory#getCode()} 对齐，B.10.8.1）';
COMMENT ON COLUMN wkf_supply_request.priority_enum IS 'priority枚举值';
COMMENT ON COLUMN wkf_supply_request.summary_id IS 'summary标识';
COMMENT ON COLUMN wkf_supply_request.deliver_id IS 'deliver标识';
COMMENT ON COLUMN wkf_supply_request.patient_id IS '患者标识';
COMMENT ON COLUMN wkf_supply_request.item_table IS '项table';
COMMENT ON COLUMN wkf_supply_request.item_quantity IS '行数量：入出库等为执行数量（拆零、正数）；盘点/损益等为盈亏数量（拆零，可正可负可零）';
COMMENT ON COLUMN wkf_supply_request.total_quantity IS '合计/实盘数量：盘点场景存实盘数量（拆零、≥0）；其它单据按业务口径使用';
COMMENT ON COLUMN wkf_supply_request.item_id IS '项标识';
COMMENT ON COLUMN wkf_supply_request.unit_code IS '单元编码';
COMMENT ON COLUMN wkf_supply_request.occurrence_time IS 'occurrence时间';
COMMENT ON COLUMN wkf_supply_request.practitioner_id IS '医护人员标识';
COMMENT ON COLUMN wkf_supply_request.supplier_id IS '供应商标识';
COMMENT ON COLUMN wkf_supply_request.reason IS '原因';
COMMENT ON COLUMN wkf_supply_request.source_type_enum IS '单库房单据为空；双端点单据必填（出方）';
COMMENT ON COLUMN wkf_supply_request.source_location_id IS '单库房单据为空；双端点单据必填（出方）';
COMMENT ON COLUMN wkf_supply_request.purpose_type_enum IS '单库房单据必填；双端点单据必填';
COMMENT ON COLUMN wkf_supply_request.purpose_location_id IS '单库房就是目的；双端点为进方';
COMMENT ON COLUMN wkf_supply_request.approver_id IS 'approver标识';
COMMENT ON COLUMN wkf_supply_request.approval_time IS 'approval时间';
COMMENT ON COLUMN wkf_supply_request.applicant_id IS 'applicant标识';
COMMENT ON COLUMN wkf_supply_request.apply_time IS '提交审批时的申请时刻；草稿阶段可为空，提交时由服务端写入当前时间';
COMMENT ON COLUMN wkf_supply_request.start_time IS '效期区间起点（与 { end_time} 成对；可空表示未维护）';
COMMENT ON COLUMN wkf_supply_request.end_time IS '效期区间止点（入库入账时可用于库存项有效期等编排）';
COMMENT ON COLUMN wkf_supply_request.lot_number IS '批号';
COMMENT ON COLUMN wkf_supply_request.trace_no IS '追踪编号';
COMMENT ON COLUMN wkf_supply_request.price IS '价格';
COMMENT ON COLUMN wkf_supply_request.total_price IS '合计价格';
COMMENT ON COLUMN wkf_supply_request.remake IS '备注';
COMMENT ON COLUMN wkf_supply_request.profit_loss_price IS '盘点盈亏金额；盘盈为正、盘亏为负；非盘点类型可空';
COMMENT ON COLUMN wkf_supply_request.invoice_no IS '采购发票号（进销存 3503/3504 purc_invo_no；未填默认 0；同号多行共享）';
COMMENT ON COLUMN wkf_supply_request.consuming_organization_id IS '领用科室（行政 Organization.id）；DEPT_ISSUE 客户端选择落库；DEPT_ISSUE_RETURN 自源领用单带出；历史单可空';
-- booking V10 · 门诊同日同患者同服务防重（GAP-BKG-001）
COMMENT ON COLUMN wkf_appointment_queue.healthcare_service_id IS '冗余：门诊防重（同 patient+service+booking_date）';
COMMENT ON COLUMN wkf_appointment_queue.booking_date IS '冗余：预约自然日（Asia/Shanghai）';
CREATE UNIQUE INDEX IF NOT EXISTS uk_wkf_appt_patient_service_day
    ON wkf_appointment_queue (patient_id, healthcare_service_id, booking_date)
    WHERE is_deleted = false
      AND appointment_kind = 'OUTPATIENT_BOOKING'
      AND healthcare_service_id IS NOT NULL
      AND booking_date IS NOT NULL;
-- booking V12 · Schedule 来源 T2 / T1 快照
COMMENT ON COLUMN wkf_schedule.source_schedule_template_id IS '来源 T2 日排班模板主键（模板生成时写入）';
COMMENT ON COLUMN wkf_schedule.slot_provision_template_id IS '关联 T1 放号时段模板主键（生成或手动创建时写入）';
CREATE INDEX IF NOT EXISTS idx_wkf_schedule_source_tpl
    ON wkf_schedule (organization_id, source_schedule_template_id)
    WHERE is_deleted = false;
-- booking V11 · 虚拟号源 lazy 物化唯一（GAP-BKG-004）
CREATE UNIQUE INDEX IF NOT EXISTS uk_wkf_slot_org_time_window
    ON wkf_slot (organization_id, start_time, end_time)
    WHERE is_deleted = false;
