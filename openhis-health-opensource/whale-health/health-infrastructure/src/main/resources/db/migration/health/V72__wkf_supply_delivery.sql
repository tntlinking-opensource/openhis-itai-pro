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
COMMENT ON COLUMN wkf_supply_delivery.summary_id IS 'summary id';
COMMENT ON COLUMN wkf_supply_delivery.status_enum IS '发放状态持久化码（如 { DispenseStatus#getCode()}，B.10.8.1）';
COMMENT ON COLUMN wkf_supply_delivery.patient_id IS 'patient id';
COMMENT ON COLUMN wkf_supply_delivery.type_enum IS '发放类型/业务子类型持久化码（与命令及事件载荷一致，B.10.8.1）';
COMMENT ON COLUMN wkf_supply_delivery.item_table IS 'item table';
COMMENT ON COLUMN wkf_supply_delivery.item_id IS 'item id';
COMMENT ON COLUMN wkf_supply_delivery.based_on_table IS 'based on table';
COMMENT ON COLUMN wkf_supply_delivery.based_on_ids IS 'based on ids';
COMMENT ON COLUMN wkf_supply_delivery.unit_code IS 'unit code';
COMMENT ON COLUMN wkf_supply_delivery.quantity IS 'quantity';
COMMENT ON COLUMN wkf_supply_delivery.condition IS 'condition';
COMMENT ON COLUMN wkf_supply_delivery.lot_number IS 'lot number';
COMMENT ON COLUMN wkf_supply_delivery.trace_no IS 'trace no';
COMMENT ON COLUMN wkf_supply_delivery.supplier_id IS 'supplier id';
COMMENT ON COLUMN wkf_supply_delivery.practitioner_id IS 'practitioner id';
COMMENT ON COLUMN wkf_supply_delivery.occurrence_time IS 'occurrence time';
COMMENT ON COLUMN wkf_supply_delivery.receiver_id IS 'receiver id';
COMMENT ON COLUMN wkf_supply_delivery.receive_time IS 'receive time';

