-- Squashed V5 med_ddl

-- from V47__med_medication_definition.sql
-- med_medication_definition（建表 + 种子数据）
-- Flyway V54

CREATE TABLE IF NOT EXISTS "public"."med_medication_definition" (
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
    "name" varchar(255) NOT NULL,
    "domain_enum" varchar(255),
    "med_version" varchar(255),
    "name_en" varchar(255),
    "py_str" varchar(255),
    "wb_str" varchar(255),
    "category_code" varchar(255),
    "merchandise_name" varchar(255),
    "merchandise_py_str" varchar(255),
    "merchandise_wb_str" varchar(255),
    "purchase_unit_code" varchar(255),
    "split_unit_code" varchar(255),
    "comprised_text" varchar(255),
    "ingredient" varchar(255),
    "dose_from" int4,
    "approval_number" varchar(255),
    "yb_match_flag" bool,
    "yb_no" varchar(255),
    "pharmacology_category_code" varchar(255),
    "skin_test_flag" bool,
    "inject_flag" bool,
    "manufacturer_id" int8,
    "manufacturer_text" varchar(255),
    "supply_id" int8,
    "restricted_flag" bool,
    "restricted_scope" varchar(255),
    "active_flag" bool,
    "children_flag" bool,
    "characteristic" int4,
    "item_min_quantity" numeric(18,2),
    "item_max_quantity" numeric(18,2),
    "def_encounter_unit_code" varchar(255),
    "def_inhospital_unit_code" varchar(255),
    "national_drug_code" varchar(255),
    "part_attribute_enum" varchar(255),
    "antibiotic_code" varchar(255),
    "restricted_enum" varchar(255),
    "self_flag" bool,
    "antibiotic_flag" bool,
    "basic_flag" bool,
    "tho_part_attribute_enum" varchar(255),
    "chrgitm_lv" int4,
    "mr_settlement_type_code" varchar(255),
    "rx_flag" bool,
    "dosage_instruction" varchar(255),
    "drug69_code" varchar(255),
    "total_volume" varchar(255),
    "dose_form_code" varchar(255),
    "dose_unit_code" varchar(255),
    "package_unit_code" varchar(255),
    "stock_unit_code" varchar(255),
    "method_code" varchar(255),
    "rate_code" varchar(255),
    "dose" numeric(18,2),
    "max_unit" numeric(18,2),
    "usage_limit" numeric(18,2),
    "location_id" int8,
    "status_enum" varchar(255) NOT NULL,
    "org_id" int8 NOT NULL,
    "ingredient_item" varchar(255),
    "medicationdefinition" varchar(255),
    "ddd_code" varchar(255),
    "ddd_unit_code" varchar(255),
    "min_rate_code" varchar(255),
    "max_rate_code" varchar(255),
    "dose_unit_ratio" numeric(18,6),
    "purchase_unit_ratio" numeric(18,6),
    "stock_unit_ratio" numeric(18,6),
    "package_unit_ratio" numeric(18,6),
    CONSTRAINT "med_medication_definition_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE med_medication_definition IS '药品 medication definition';
COMMENT ON COLUMN med_medication_definition.id IS '主键';
COMMENT ON COLUMN med_medication_definition.tenant_id IS '租户标识';
COMMENT ON COLUMN med_medication_definition.created_at IS '创建时间';
COMMENT ON COLUMN med_medication_definition.updated_at IS '最后更新时间';
COMMENT ON COLUMN med_medication_definition.created_by IS '创建人';
COMMENT ON COLUMN med_medication_definition.updated_by IS '最后更新人';
COMMENT ON COLUMN med_medication_definition.is_deleted IS '软删标记';
COMMENT ON COLUMN med_medication_definition.deleted_at IS '删除时间';
COMMENT ON COLUMN med_medication_definition.deleted_by IS '删除人';
COMMENT ON COLUMN med_medication_definition.version IS '乐观锁版本号';
COMMENT ON COLUMN med_medication_definition.bus_no IS '药品定义聚合根';
COMMENT ON COLUMN med_medication_definition.name IS '药品名称';
COMMENT ON COLUMN med_medication_definition.domain_enum IS '适用范围编码';
COMMENT ON COLUMN med_medication_definition.med_version IS '药品版本';
COMMENT ON COLUMN med_medication_definition.name_en IS '英文药名';
COMMENT ON COLUMN med_medication_definition.py_str IS '药品名称拼音码';
COMMENT ON COLUMN med_medication_definition.wb_str IS '药品名称五笔码';
COMMENT ON COLUMN med_medication_definition.category_code IS '药品分类编码';
COMMENT ON COLUMN med_medication_definition.merchandise_name IS '商品名称';
COMMENT ON COLUMN med_medication_definition.merchandise_py_str IS '商品名拼音码';
COMMENT ON COLUMN med_medication_definition.merchandise_wb_str IS '商品名五笔码';
COMMENT ON COLUMN med_medication_definition.purchase_unit_code IS '采购单位（5类单位之一）';
COMMENT ON COLUMN med_medication_definition.split_unit_code IS '拆零单位（5类单位之一）';
COMMENT ON COLUMN med_medication_definition.comprised_text IS '所含耗材描述';
COMMENT ON COLUMN med_medication_definition.ingredient IS '成分';
COMMENT ON COLUMN med_medication_definition.dose_from IS '剂量形式编码';
COMMENT ON COLUMN med_medication_definition.approval_number IS '批准文号';
COMMENT ON COLUMN med_medication_definition.yb_match_flag IS '医保是否已对码';
COMMENT ON COLUMN med_medication_definition.yb_no IS '医保编码';
COMMENT ON COLUMN med_medication_definition.pharmacology_category_code IS '药理作用分类编码';
COMMENT ON COLUMN med_medication_definition.skin_test_flag IS '是否皮试药';
COMMENT ON COLUMN med_medication_definition.inject_flag IS '是否注射药物';
COMMENT ON COLUMN med_medication_definition.manufacturer_id IS '生产厂家标识';
COMMENT ON COLUMN med_medication_definition.manufacturer_text IS '生产厂商文本';
COMMENT ON COLUMN med_medication_definition.supply_id IS '供应商标识';
COMMENT ON COLUMN med_medication_definition.restricted_flag IS '是否限制使用';
COMMENT ON COLUMN med_medication_definition.restricted_scope IS '限制使用范围';
COMMENT ON COLUMN med_medication_definition.active_flag IS '启用标记（true=启用，false=停用）';
COMMENT ON COLUMN med_medication_definition.children_flag IS '儿童用药标志';
COMMENT ON COLUMN med_medication_definition.characteristic IS '产品特性编码';
COMMENT ON COLUMN med_medication_definition.item_min_quantity IS '最小库存警戒数量';
COMMENT ON COLUMN med_medication_definition.item_max_quantity IS '最大库存警戒数量';
COMMENT ON COLUMN med_medication_definition.def_encounter_unit_code IS '默认门诊单位编码';
COMMENT ON COLUMN med_medication_definition.def_inhospital_unit_code IS '默认住院单位编码';
COMMENT ON COLUMN med_medication_definition.national_drug_code IS '国家药品贯标编码';
COMMENT ON COLUMN med_medication_definition.part_attribute_enum IS '拆分属性编码';
COMMENT ON COLUMN med_medication_definition.antibiotic_code IS '抗生素分类编码';
COMMENT ON COLUMN med_medication_definition.restricted_enum IS '权限限制枚举编码';
COMMENT ON COLUMN med_medication_definition.self_flag IS '是否自制';
COMMENT ON COLUMN med_medication_definition.antibiotic_flag IS '是否抗生素';
COMMENT ON COLUMN med_medication_definition.basic_flag IS '是否基药';
COMMENT ON COLUMN med_medication_definition.tho_part_attribute_enum IS '住院临时医嘱拆分属性编码';
COMMENT ON COLUMN med_medication_definition.chrgitm_lv IS '医保等级';
COMMENT ON COLUMN med_medication_definition.mr_settlement_type_code IS '病案结算类型（术语 mr_settlement_type_code）';
COMMENT ON COLUMN med_medication_definition.rx_flag IS '处方药标志';
COMMENT ON COLUMN med_medication_definition.dosage_instruction IS '用药说明';
COMMENT ON COLUMN med_medication_definition.drug69_code IS '药品 69 码';
COMMENT ON COLUMN med_medication_definition.total_volume IS '规格';
COMMENT ON COLUMN med_medication_definition.dose_form_code IS '剂型编码';
COMMENT ON COLUMN med_medication_definition.dose_unit_code IS '剂量单位（5类单位之一）';
COMMENT ON COLUMN med_medication_definition.package_unit_code IS '销售单位（5类单位之一）';
COMMENT ON COLUMN med_medication_definition.stock_unit_code IS '库存单位（5类单位之一）';
COMMENT ON COLUMN med_medication_definition.method_code IS '默认用法编码';
COMMENT ON COLUMN med_medication_definition.rate_code IS '默认频次编码';
COMMENT ON COLUMN med_medication_definition.dose IS '单次剂量';
COMMENT ON COLUMN med_medication_definition.max_unit IS '单次最大剂量';
COMMENT ON COLUMN med_medication_definition.usage_limit IS '用量限定';
COMMENT ON COLUMN med_medication_definition.location_id IS '采购入库位置（他域事实列）';
COMMENT ON COLUMN med_medication_definition.status_enum IS '生命周期状态编码（DRAFT/ACTIVE/INACTIVE/DISCARDED）';
COMMENT ON COLUMN med_medication_definition.org_id IS '机构标识';
COMMENT ON COLUMN med_medication_definition.ingredient_item IS '成分项';
COMMENT ON COLUMN med_medication_definition.medicationdefinition IS '药品说明';
COMMENT ON COLUMN med_medication_definition.ddd_code IS 'DDD（日限定剂量）编码';
COMMENT ON COLUMN med_medication_definition.ddd_unit_code IS 'DDD（日限定剂量）单位编码';
COMMENT ON COLUMN med_medication_definition.min_rate_code IS '最小频次编码';
COMMENT ON COLUMN med_medication_definition.max_rate_code IS '最大频次编码';
COMMENT ON COLUMN med_medication_definition.dose_unit_ratio IS '剂量单位→拆零单位换算倍数（拆零单位为1）';
COMMENT ON COLUMN med_medication_definition.purchase_unit_ratio IS '采购单位→拆零单位换算倍数（拆零单位为1）';
COMMENT ON COLUMN med_medication_definition.stock_unit_ratio IS '库存单位→拆零单位换算倍数（拆零单位为1）';
COMMENT ON COLUMN med_medication_definition.package_unit_ratio IS '销售单位→拆零单位换算倍数（拆零单位为1）';

-- seed: med_medication_definition

-- from V48__med_medication_dispense.sql
-- med_medication_dispense（建表）
-- Flyway V55

CREATE TABLE IF NOT EXISTS "public"."med_medication_dispense" (
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
    "status_enum" varchar(255) NOT NULL,
    "not_performed_reason_enum" varchar(255),
    "status_changed_time" timestamptz(6),
    "dispense_enum" varchar(255) NOT NULL,
    "medication_id" int8 NOT NULL,
    "patient_id" int8 NOT NULL,
    "encounter_id" int8 NOT NULL,
    "location_id" int8 NOT NULL,
    "med_req_id" int8 NOT NULL,
    "quantity" numeric(18,2) NOT NULL,
    "unit_code" varchar(255) NOT NULL,
    "dispense_quantity" numeric(18,2),
    "lot_number" varchar(255),
    "trace_no" varchar(50000),
    "preparer_id" int8,
    "prepare_time" timestamptz(6),
    "practitioner_id" int8,
    "dispense_time" timestamptz(6),
    "limit_time" timestamptz(6),
    "planned_dispense_time" timestamptz(6),
    "procedure_id" int8,
    "summary_no" varchar(255),
    "des_location_id" int8,
    "rec_practitioner_id" int8,
    "dosage_instruction" varchar(255),
    "method_code" varchar(255),
    "frequency_code" varchar(255),
    "dose" numeric(18,2),
    "dose_unit_code" varchar(255),
    "max_unit" numeric(18,2),
    "support_info" varchar(255),
    "part_type_code" varchar(255),
    "dispense_frequency_code" varchar(255),
    CONSTRAINT "med_medication_dispense_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE med_medication_dispense IS '药品 medication dispense';
COMMENT ON COLUMN med_medication_dispense.id IS '主键';
COMMENT ON COLUMN med_medication_dispense.tenant_id IS '租户标识';
COMMENT ON COLUMN med_medication_dispense.created_at IS '创建时间';
COMMENT ON COLUMN med_medication_dispense.updated_at IS '最后更新时间';
COMMENT ON COLUMN med_medication_dispense.created_by IS '创建人';
COMMENT ON COLUMN med_medication_dispense.updated_by IS '最后更新人';
COMMENT ON COLUMN med_medication_dispense.is_deleted IS '软删标记';
COMMENT ON COLUMN med_medication_dispense.deleted_at IS '删除时间';
COMMENT ON COLUMN med_medication_dispense.deleted_by IS '删除人';
COMMENT ON COLUMN med_medication_dispense.version IS '乐观锁版本号';
COMMENT ON COLUMN med_medication_dispense.bus_no IS '药品发放聚合根';
COMMENT ON COLUMN med_medication_dispense.status_enum IS 'status 枚举值';
COMMENT ON COLUMN med_medication_dispense.not_performed_reason_enum IS 'notPerformedReason 枚举值';
COMMENT ON COLUMN med_medication_dispense.status_changed_time IS 'statusChanged 时间';
COMMENT ON COLUMN med_medication_dispense.dispense_enum IS 'dispense 枚举值';
COMMENT ON COLUMN med_medication_dispense.medication_id IS 'medication 标识';
COMMENT ON COLUMN med_medication_dispense.patient_id IS 'patient 标识';
COMMENT ON COLUMN med_medication_dispense.encounter_id IS 'encounter 标识';
COMMENT ON COLUMN med_medication_dispense.location_id IS 'location 标识';
COMMENT ON COLUMN med_medication_dispense.med_req_id IS 'medReq 标识';
COMMENT ON COLUMN med_medication_dispense.quantity IS '数量';
COMMENT ON COLUMN med_medication_dispense.unit_code IS 'unit 代码';
COMMENT ON COLUMN med_medication_dispense.dispense_quantity IS '发药数量';
COMMENT ON COLUMN med_medication_dispense.lot_number IS '批号';
COMMENT ON COLUMN med_medication_dispense.trace_no IS 'trace 编号';
COMMENT ON COLUMN med_medication_dispense.preparer_id IS 'preparer 标识';
COMMENT ON COLUMN med_medication_dispense.prepare_time IS 'prepare 时间';
COMMENT ON COLUMN med_medication_dispense.practitioner_id IS 'practitioner 标识';
COMMENT ON COLUMN med_medication_dispense.dispense_time IS 'dispense 时间';
COMMENT ON COLUMN med_medication_dispense.limit_time IS 'limit 时间';
COMMENT ON COLUMN med_medication_dispense.planned_dispense_time IS 'plannedDispense 时间';
COMMENT ON COLUMN med_medication_dispense.procedure_id IS 'procedure 标识';
COMMENT ON COLUMN med_medication_dispense.summary_no IS 'summary 编号';
COMMENT ON COLUMN med_medication_dispense.des_location_id IS 'desLocation 标识';
COMMENT ON COLUMN med_medication_dispense.rec_practitioner_id IS 'recPractitioner 标识';
COMMENT ON COLUMN med_medication_dispense.dosage_instruction IS '用法说明';
COMMENT ON COLUMN med_medication_dispense.method_code IS 'method 代码';
COMMENT ON COLUMN med_medication_dispense.frequency_code IS 'frequency 代码';
COMMENT ON COLUMN med_medication_dispense.dose IS '剂量';
COMMENT ON COLUMN med_medication_dispense.dose_unit_code IS 'doseUnit 代码';
COMMENT ON COLUMN med_medication_dispense.max_unit IS 'max单元';
COMMENT ON COLUMN med_medication_dispense.support_info IS '辅助信息';
COMMENT ON COLUMN med_medication_dispense.part_type_code IS 'partType 代码';
COMMENT ON COLUMN med_medication_dispense.dispense_frequency_code IS 'dispenseFrequency 代码';

-- from V49__med_medication_request.sql
-- med_medication_request（建表）
-- Flyway V56

CREATE TABLE IF NOT EXISTS "public"."med_medication_request" (
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
    "prescription_no" varchar(255),
    "group_id" int8,
    "package_id" int8,
    "status_enum" varchar(255) NOT NULL,
    "status_reason" int4,
    "status_changed_time" timestamptz(6),
    "intent_enum" varchar(255),
    "category_enum" varchar(255),
    "priority_enum" varchar(255),
    "perform_flag" bool,
    "medication_id" int8 NOT NULL,
    "patient_id" int8 NOT NULL,
    "encounter_id" int8 NOT NULL,
    "requester_id" int8,
    "location_id" int8,
    "org_id" int8,
    "perform_location" int8,
    "perform_org" int8,
    "performer_enum" varchar(255),
    "performer_id" int8,
    "device_def_id" int8,
    "recorder_id" int8,
    "req_authored_time" timestamptz(6),
    "report_flag" bool,
    "support_info" varchar(255),
    "reason_json" varchar(255),
    "therapy_enum" varchar(255) NOT NULL,
    "effective_dose_start" timestamptz(6),
    "quantity" numeric(18,2) NOT NULL,
    "execute_num" int4,
    "unit_code" varchar(255) NOT NULL,
    "method_code" varchar(255) NOT NULL,
    "rate_code" varchar(255) NOT NULL,
    "dose" numeric(18,2),
    "dose_unit_code" varchar(255),
    "max_dose" numeric(18,2),
    "first_dose" numeric(18,2),
    "first_dose_unit_code" varchar(255),
    "first_duration" varchar(255),
    "dispense_interval" varchar(255),
    "dispense_per_quantity" int4,
    "dispense_per_duration" int4,
    "lot_number" varchar(255),
    "trace_no" varchar(50000),
    "skin_test_flag" bool,
    "infusion_flag" bool,
    "speed_code" varchar(255),
    "contract_id" int8,
    "condition_definition_id" int8,
    "encounter_diagnosis_id" int8,
    "performer_check_id" int8,
    "check_time" timestamptz(6),
    "effective_dose_end" timestamptz(6),
    "refund_medicine_id" int8,
    "rx_type_code" varchar(255),
    "yb_class_enum" varchar(255),
    "dosage_instruction" varchar(255),
    "based_on_table" varchar(255),
    "based_on_id" int8,
    "tcm_flag" bool,
    "content_json" varchar(255),
    "chinese_herbs_dose_quantity" numeric(18,2),
    "suffering_flag" bool,
    "sort_number" int4,
    "generate_source_enum" varchar(255),
    "remark" varchar(255),
    CONSTRAINT "med_medication_request_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE med_medication_request IS '药品 medication request';
COMMENT ON COLUMN med_medication_request.id IS '主键';
COMMENT ON COLUMN med_medication_request.tenant_id IS '租户标识';
COMMENT ON COLUMN med_medication_request.created_at IS '创建时间';
COMMENT ON COLUMN med_medication_request.updated_at IS '最后更新时间';
COMMENT ON COLUMN med_medication_request.created_by IS '创建人';
COMMENT ON COLUMN med_medication_request.updated_by IS '最后更新人';
COMMENT ON COLUMN med_medication_request.is_deleted IS '软删标记';
COMMENT ON COLUMN med_medication_request.deleted_at IS '删除时间';
COMMENT ON COLUMN med_medication_request.deleted_by IS '删除人';
COMMENT ON COLUMN med_medication_request.version IS '乐观锁版本号';
COMMENT ON COLUMN med_medication_request.bus_no IS '业务编号';
COMMENT ON COLUMN med_medication_request.prescription_no IS '处方编号';
COMMENT ON COLUMN med_medication_request.group_id IS '分组标识';
COMMENT ON COLUMN med_medication_request.package_id IS 'package标识';
COMMENT ON COLUMN med_medication_request.status_enum IS '状态枚举值';
COMMENT ON COLUMN med_medication_request.status_reason IS '状态原因';
COMMENT ON COLUMN med_medication_request.status_changed_time IS '状态changed时间';
COMMENT ON COLUMN med_medication_request.intent_enum IS 'intent枚举值';
COMMENT ON COLUMN med_medication_request.category_enum IS 'category枚举值';
COMMENT ON COLUMN med_medication_request.priority_enum IS 'priority枚举值';
COMMENT ON COLUMN med_medication_request.perform_flag IS 'perform标志位';
COMMENT ON COLUMN med_medication_request.medication_id IS '药品标识';
COMMENT ON COLUMN med_medication_request.patient_id IS '患者标识';
COMMENT ON COLUMN med_medication_request.encounter_id IS '就诊标识';
COMMENT ON COLUMN med_medication_request.requester_id IS '药品申请聚合根';
COMMENT ON COLUMN med_medication_request.location_id IS '位置/科室标识';
COMMENT ON COLUMN med_medication_request.org_id IS '开立科室组织主键（{ adm_organization.id}）';
COMMENT ON COLUMN med_medication_request.perform_location IS '发放药房库位主键（{ adm_location.id}）；与 { #orgId} 分列，禁止混存';
COMMENT ON COLUMN med_medication_request.perform_org IS '执行机构';
COMMENT ON COLUMN med_medication_request.performer_enum IS 'performer枚举值';
COMMENT ON COLUMN med_medication_request.performer_id IS 'performer标识';
COMMENT ON COLUMN med_medication_request.device_def_id IS '设备def标识';
COMMENT ON COLUMN med_medication_request.recorder_id IS '记录人标识';
COMMENT ON COLUMN med_medication_request.req_authored_time IS 'reqauthored时间';
COMMENT ON COLUMN med_medication_request.report_flag IS '报表标志位';
COMMENT ON COLUMN med_medication_request.support_info IS '辅助信息';
COMMENT ON COLUMN med_medication_request.reason_json IS '原因JSON';
COMMENT ON COLUMN med_medication_request.therapy_enum IS 'therapy枚举值';
COMMENT ON COLUMN med_medication_request.effective_dose_start IS 'effectivedose开始';
COMMENT ON COLUMN med_medication_request.quantity IS '数量';
COMMENT ON COLUMN med_medication_request.execute_num IS '执行次数';
COMMENT ON COLUMN med_medication_request.unit_code IS '单元编码';
COMMENT ON COLUMN med_medication_request.method_code IS 'method编码';
COMMENT ON COLUMN med_medication_request.rate_code IS 'rate编码';
COMMENT ON COLUMN med_medication_request.dose IS '剂量';
COMMENT ON COLUMN med_medication_request.dose_unit_code IS 'dose单元编码';
COMMENT ON COLUMN med_medication_request.max_dose IS '最大剂量';
COMMENT ON COLUMN med_medication_request.first_dose IS '字段：first dose';
COMMENT ON COLUMN med_medication_request.first_dose_unit_code IS 'firstdose单元编码';
COMMENT ON COLUMN med_medication_request.first_duration IS 'first耗时';
COMMENT ON COLUMN med_medication_request.dispense_interval IS '发药interval';
COMMENT ON COLUMN med_medication_request.dispense_per_quantity IS '发药per数量';
COMMENT ON COLUMN med_medication_request.dispense_per_duration IS '发药per耗时';
COMMENT ON COLUMN med_medication_request.lot_number IS '批号';
COMMENT ON COLUMN med_medication_request.trace_no IS '追踪编号';
COMMENT ON COLUMN med_medication_request.skin_test_flag IS 'skintest标志位';
COMMENT ON COLUMN med_medication_request.infusion_flag IS 'infusion标志位';
COMMENT ON COLUMN med_medication_request.speed_code IS 'speed编码';
COMMENT ON COLUMN med_medication_request.contract_id IS '合同标识';
COMMENT ON COLUMN med_medication_request.condition_definition_id IS '诊断定义主键（列 { condition_definition_id}，即 { cln_condition_definition.id}）';
COMMENT ON COLUMN med_medication_request.encounter_diagnosis_id IS '就诊诊断标识';
COMMENT ON COLUMN med_medication_request.performer_check_id IS 'performercheck标识';
COMMENT ON COLUMN med_medication_request.check_time IS 'check时间';
COMMENT ON COLUMN med_medication_request.effective_dose_end IS 'effectivedose结束';
COMMENT ON COLUMN med_medication_request.refund_medicine_id IS 'refundmedicine标识';
COMMENT ON COLUMN med_medication_request.rx_type_code IS 'rx类型编码';
COMMENT ON COLUMN med_medication_request.yb_class_enum IS '医保class枚举值';
COMMENT ON COLUMN med_medication_request.dosage_instruction IS '用法说明';
COMMENT ON COLUMN med_medication_request.based_on_table IS '依据表名';
COMMENT ON COLUMN med_medication_request.based_on_id IS 'basedon标识';
COMMENT ON COLUMN med_medication_request.tcm_flag IS 'tcm标志位';
COMMENT ON COLUMN med_medication_request.content_json IS '内容JSON';
COMMENT ON COLUMN med_medication_request.chinese_herbs_dose_quantity IS 'chineseherbsdose数量';
COMMENT ON COLUMN med_medication_request.suffering_flag IS 'suffering标志位';
COMMENT ON COLUMN med_medication_request.sort_number IS '排序号';
COMMENT ON COLUMN med_medication_request.generate_source_enum IS 'generate来源枚举值';
COMMENT ON COLUMN med_medication_request.remark IS '备注';
