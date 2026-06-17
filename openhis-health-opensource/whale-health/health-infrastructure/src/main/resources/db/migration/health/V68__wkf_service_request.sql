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
    "authored_time" timestamptz(6),
    "requester_id" int8 NOT NULL,
    "performer_type_code" varchar(255),
    "performer_id" int8,
    "performer_check_id" int8,
    "check_time" timestamptz(6),
    "location_id" int8,
    "org_id" int8,
    "reason_text" varchar(255),
    "contract_id" int8,
    "occurrence_start_time" timestamptz(6),
    "occurrence_end_time" timestamptz(6),
    "relevant_history_id" int8,
    "refund_service_id" int8,
    "content_json" varchar(255),
    "yb_class_enum" varchar(255),
    "print_count" int4,
    "condition_definition_id" int8,
    "encounter_diagnosis_id" int8,
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
COMMENT ON COLUMN wkf_service_request.bus_no IS 'bus no';
COMMENT ON COLUMN wkf_service_request.prescription_no IS 'prescription no';
COMMENT ON COLUMN wkf_service_request.based_on_table IS 'based on table';
COMMENT ON COLUMN wkf_service_request.based_on_id IS 'based on id';
COMMENT ON COLUMN wkf_service_request.requisition_id IS 'requisition id';
COMMENT ON COLUMN wkf_service_request.status_enum IS 'status enum';
COMMENT ON COLUMN wkf_service_request.intent_enum IS 'intent enum';
COMMENT ON COLUMN wkf_service_request.category_enum IS 'category enum';
COMMENT ON COLUMN wkf_service_request.group_id IS 'group id';
COMMENT ON COLUMN wkf_service_request.priority_enum IS 'priority enum';
COMMENT ON COLUMN wkf_service_request.perform_flag IS 'perform flag';
COMMENT ON COLUMN wkf_service_request.activity_id IS 'activity id';
COMMENT ON COLUMN wkf_service_request.quantity IS 'quantity';
COMMENT ON COLUMN wkf_service_request.unit_code IS 'unit code';
COMMENT ON COLUMN wkf_service_request.patient_id IS 'patient id';
COMMENT ON COLUMN wkf_service_request.encounter_id IS 'encounter id';
COMMENT ON COLUMN wkf_service_request.authored_time IS 'authored time';
COMMENT ON COLUMN wkf_service_request.requester_id IS 'requester id';
COMMENT ON COLUMN wkf_service_request.performer_type_code IS 'performer type code';
COMMENT ON COLUMN wkf_service_request.performer_id IS 'performer id';
COMMENT ON COLUMN wkf_service_request.performer_check_id IS 'performer check id';
COMMENT ON COLUMN wkf_service_request.check_time IS 'check time';
COMMENT ON COLUMN wkf_service_request.location_id IS 'location id';
COMMENT ON COLUMN wkf_service_request.org_id IS 'org id';
COMMENT ON COLUMN wkf_service_request.reason_text IS 'reason text';
COMMENT ON COLUMN wkf_service_request.contract_id IS 'contract id';
COMMENT ON COLUMN wkf_service_request.occurrence_start_time IS 'occurrence start time';
COMMENT ON COLUMN wkf_service_request.occurrence_end_time IS 'occurrence end time';
COMMENT ON COLUMN wkf_service_request.relevant_history_id IS 'relevant history id';
COMMENT ON COLUMN wkf_service_request.refund_service_id IS 'refund service id';
COMMENT ON COLUMN wkf_service_request.content_json IS 'content json';
COMMENT ON COLUMN wkf_service_request.yb_class_enum IS 'yb class enum';
COMMENT ON COLUMN wkf_service_request.print_count IS 'print count';
COMMENT ON COLUMN wkf_service_request.condition_definition_id IS '诊断定义主键（列 { condition_definition_id}，即 { cln_condition_definition.id}）';
COMMENT ON COLUMN wkf_service_request.encounter_diagnosis_id IS 'encounter diagnosis id';
COMMENT ON COLUMN wkf_service_request.therapy_enum IS 'therapy enum';
COMMENT ON COLUMN wkf_service_request.rate_code IS 'rate code';
COMMENT ON COLUMN wkf_service_request.parent_id IS 'parent id';
COMMENT ON COLUMN wkf_service_request.generate_source_enum IS 'generate source enum';
COMMENT ON COLUMN wkf_service_request.remark IS 'remark';
COMMENT ON COLUMN wkf_service_request.entrust_content IS 'entrust content';
COMMENT ON COLUMN wkf_service_request.sort_number IS 'sort number';
COMMENT ON COLUMN wkf_service_request.execution_count IS '服务申请聚合根（非药品医嘱）';

