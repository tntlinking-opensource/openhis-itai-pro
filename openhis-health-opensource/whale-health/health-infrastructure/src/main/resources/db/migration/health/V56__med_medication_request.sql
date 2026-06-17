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
COMMENT ON COLUMN med_medication_request.bus_no IS 'bus no';
COMMENT ON COLUMN med_medication_request.prescription_no IS 'prescription no';
COMMENT ON COLUMN med_medication_request.group_id IS 'group id';
COMMENT ON COLUMN med_medication_request.package_id IS 'package id';
COMMENT ON COLUMN med_medication_request.status_enum IS 'status enum';
COMMENT ON COLUMN med_medication_request.status_reason IS 'status reason';
COMMENT ON COLUMN med_medication_request.status_changed_time IS 'status changed time';
COMMENT ON COLUMN med_medication_request.intent_enum IS 'intent enum';
COMMENT ON COLUMN med_medication_request.category_enum IS 'category enum';
COMMENT ON COLUMN med_medication_request.priority_enum IS 'priority enum';
COMMENT ON COLUMN med_medication_request.perform_flag IS 'perform flag';
COMMENT ON COLUMN med_medication_request.medication_id IS 'medication id';
COMMENT ON COLUMN med_medication_request.patient_id IS 'patient id';
COMMENT ON COLUMN med_medication_request.encounter_id IS 'encounter id';
COMMENT ON COLUMN med_medication_request.requester_id IS '药品申请聚合根';
COMMENT ON COLUMN med_medication_request.location_id IS 'location id';
COMMENT ON COLUMN med_medication_request.org_id IS '开立科室组织主键（{ adm_organization.id}）';
COMMENT ON COLUMN med_medication_request.perform_location IS '发放药房库位主键（{ adm_location.id}）；与 { #orgId} 分列，禁止混存';
COMMENT ON COLUMN med_medication_request.perform_org IS 'perform org';
COMMENT ON COLUMN med_medication_request.performer_enum IS 'performer enum';
COMMENT ON COLUMN med_medication_request.performer_id IS 'performer id';
COMMENT ON COLUMN med_medication_request.device_def_id IS 'device def id';
COMMENT ON COLUMN med_medication_request.recorder_id IS 'recorder id';
COMMENT ON COLUMN med_medication_request.req_authored_time IS 'req authored time';
COMMENT ON COLUMN med_medication_request.report_flag IS 'report flag';
COMMENT ON COLUMN med_medication_request.support_info IS 'support info';
COMMENT ON COLUMN med_medication_request.reason_json IS 'reason json';
COMMENT ON COLUMN med_medication_request.therapy_enum IS 'therapy enum';
COMMENT ON COLUMN med_medication_request.effective_dose_start IS 'effective dose start';
COMMENT ON COLUMN med_medication_request.quantity IS 'quantity';
COMMENT ON COLUMN med_medication_request.execute_num IS 'execute num';
COMMENT ON COLUMN med_medication_request.unit_code IS 'unit code';
COMMENT ON COLUMN med_medication_request.method_code IS 'method code';
COMMENT ON COLUMN med_medication_request.rate_code IS 'rate code';
COMMENT ON COLUMN med_medication_request.dose IS 'dose';
COMMENT ON COLUMN med_medication_request.dose_unit_code IS 'dose unit code';
COMMENT ON COLUMN med_medication_request.max_dose IS 'max dose';
COMMENT ON COLUMN med_medication_request.first_dose IS 'first dose';
COMMENT ON COLUMN med_medication_request.first_dose_unit_code IS 'first dose unit code';
COMMENT ON COLUMN med_medication_request.first_duration IS 'first duration';
COMMENT ON COLUMN med_medication_request.dispense_interval IS 'dispense interval';
COMMENT ON COLUMN med_medication_request.dispense_per_quantity IS 'dispense per quantity';
COMMENT ON COLUMN med_medication_request.dispense_per_duration IS 'dispense per duration';
COMMENT ON COLUMN med_medication_request.lot_number IS 'lot number';
COMMENT ON COLUMN med_medication_request.trace_no IS 'trace no';
COMMENT ON COLUMN med_medication_request.skin_test_flag IS 'skin test flag';
COMMENT ON COLUMN med_medication_request.infusion_flag IS 'infusion flag';
COMMENT ON COLUMN med_medication_request.speed_code IS 'speed code';
COMMENT ON COLUMN med_medication_request.contract_id IS 'contract id';
COMMENT ON COLUMN med_medication_request.condition_definition_id IS '诊断定义主键（列 { condition_definition_id}，即 { cln_condition_definition.id}）';
COMMENT ON COLUMN med_medication_request.encounter_diagnosis_id IS 'encounter diagnosis id';
COMMENT ON COLUMN med_medication_request.performer_check_id IS 'performer check id';
COMMENT ON COLUMN med_medication_request.check_time IS 'check time';
COMMENT ON COLUMN med_medication_request.effective_dose_end IS 'effective dose end';
COMMENT ON COLUMN med_medication_request.refund_medicine_id IS 'refund medicine id';
COMMENT ON COLUMN med_medication_request.rx_type_code IS 'rx type code';
COMMENT ON COLUMN med_medication_request.yb_class_enum IS 'yb class enum';
COMMENT ON COLUMN med_medication_request.dosage_instruction IS 'dosage instruction';
COMMENT ON COLUMN med_medication_request.based_on_table IS 'based on table';
COMMENT ON COLUMN med_medication_request.based_on_id IS 'based on id';
COMMENT ON COLUMN med_medication_request.tcm_flag IS 'tcm flag';
COMMENT ON COLUMN med_medication_request.content_json IS 'content json';
COMMENT ON COLUMN med_medication_request.chinese_herbs_dose_quantity IS 'chinese herbs dose quantity';
COMMENT ON COLUMN med_medication_request.suffering_flag IS 'suffering flag';
COMMENT ON COLUMN med_medication_request.sort_number IS 'sort number';
COMMENT ON COLUMN med_medication_request.generate_source_enum IS 'generate source enum';
COMMENT ON COLUMN med_medication_request.remark IS 'remark';

