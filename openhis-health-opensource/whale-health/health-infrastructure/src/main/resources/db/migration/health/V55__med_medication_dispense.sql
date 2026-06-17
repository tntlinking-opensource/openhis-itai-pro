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
COMMENT ON COLUMN med_medication_dispense.quantity IS 'quantity';
COMMENT ON COLUMN med_medication_dispense.unit_code IS 'unit 代码';
COMMENT ON COLUMN med_medication_dispense.dispense_quantity IS 'dispenseQuantity';
COMMENT ON COLUMN med_medication_dispense.lot_number IS 'lotNumber';
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
COMMENT ON COLUMN med_medication_dispense.dosage_instruction IS 'dosageInstruction';
COMMENT ON COLUMN med_medication_dispense.method_code IS 'method 代码';
COMMENT ON COLUMN med_medication_dispense.frequency_code IS 'frequency 代码';
COMMENT ON COLUMN med_medication_dispense.dose IS 'dose';
COMMENT ON COLUMN med_medication_dispense.dose_unit_code IS 'doseUnit 代码';
COMMENT ON COLUMN med_medication_dispense.max_unit IS 'maxUnit';
COMMENT ON COLUMN med_medication_dispense.support_info IS 'supportInfo';
COMMENT ON COLUMN med_medication_dispense.part_type_code IS 'partType 代码';
COMMENT ON COLUMN med_medication_dispense.dispense_frequency_code IS 'dispenseFrequency 代码';

