-- cus_trace_number_record（建表）
-- Flyway V41

CREATE TABLE IF NOT EXISTS "public"."cus_trace_number_record" (
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
    "item_table" varchar(255) NOT NULL,
    "item_id" int8 NOT NULL,
    "location_type_enum" varchar(255) NOT NULL,
    "location_id" int8 NOT NULL,
    "location_store_id" int8,
    "lot_number" varchar(255) NOT NULL,
    "trace_no" varchar(50000) NOT NULL,
    "status_enum" varchar(255) NOT NULL,
    "unit_code" varchar(255),
    "operation_type" varchar(64) NOT NULL,
    "bus_no" varchar(255),
    CONSTRAINT "cus_trace_number_record_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE cus_trace_number_record IS '自定义 trace number record';

COMMENT ON COLUMN cus_trace_number_record.id IS '主键';
COMMENT ON COLUMN cus_trace_number_record.tenant_id IS '租户标识';
COMMENT ON COLUMN cus_trace_number_record.created_at IS '创建时间';
COMMENT ON COLUMN cus_trace_number_record.updated_at IS '最后更新时间';
COMMENT ON COLUMN cus_trace_number_record.created_by IS '创建人';
COMMENT ON COLUMN cus_trace_number_record.updated_by IS '最后更新人';
COMMENT ON COLUMN cus_trace_number_record.is_deleted IS '软删标记';
COMMENT ON COLUMN cus_trace_number_record.deleted_at IS '删除时间';
COMMENT ON COLUMN cus_trace_number_record.deleted_by IS '删除人';
COMMENT ON COLUMN cus_trace_number_record.version IS '乐观锁版本号';
COMMENT ON COLUMN cus_trace_number_record.item_table IS 'item table';
COMMENT ON COLUMN cus_trace_number_record.item_id IS 'item id';
COMMENT ON COLUMN cus_trace_number_record.location_type_enum IS 'location type enum';
COMMENT ON COLUMN cus_trace_number_record.location_id IS 'location id';
COMMENT ON COLUMN cus_trace_number_record.location_store_id IS 'location store id';
COMMENT ON COLUMN cus_trace_number_record.lot_number IS 'lot number';
COMMENT ON COLUMN cus_trace_number_record.trace_no IS 'trace no';
COMMENT ON COLUMN cus_trace_number_record.status_enum IS 'status enum';
COMMENT ON COLUMN cus_trace_number_record.unit_code IS 'unit code';
COMMENT ON COLUMN cus_trace_number_record.operation_type IS '追溯码记录聚合根';
COMMENT ON COLUMN cus_trace_number_record.bus_no IS 'bus no';

