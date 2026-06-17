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
COMMENT ON COLUMN wkf_supply_request.intent_enum IS 'intent enum';
COMMENT ON COLUMN wkf_supply_request.category_enum IS '供应类别持久化码（与 { com.openhis.health.domain.share.enums.SupplyCategory#getCode()} 对齐，B.10.8.1）';
COMMENT ON COLUMN wkf_supply_request.priority_enum IS 'priority enum';
COMMENT ON COLUMN wkf_supply_request.summary_id IS 'summary id';
COMMENT ON COLUMN wkf_supply_request.deliver_id IS 'deliver id';
COMMENT ON COLUMN wkf_supply_request.patient_id IS 'patient id';
COMMENT ON COLUMN wkf_supply_request.item_table IS 'item table';
COMMENT ON COLUMN wkf_supply_request.item_quantity IS '行数量：入出库等为执行数量（拆零、正数）；盘点/损益等为盈亏数量（拆零，可正可负可零）';
COMMENT ON COLUMN wkf_supply_request.total_quantity IS '合计/实盘数量：盘点场景存实盘数量（拆零、≥0）；其它单据按业务口径使用';
COMMENT ON COLUMN wkf_supply_request.item_id IS 'item id';
COMMENT ON COLUMN wkf_supply_request.unit_code IS 'unit code';
COMMENT ON COLUMN wkf_supply_request.occurrence_time IS 'occurrence time';
COMMENT ON COLUMN wkf_supply_request.practitioner_id IS 'practitioner id';
COMMENT ON COLUMN wkf_supply_request.supplier_id IS 'supplier id';
COMMENT ON COLUMN wkf_supply_request.reason IS 'reason';
COMMENT ON COLUMN wkf_supply_request.source_type_enum IS '单库房单据为空；双端点单据必填（出方）';
COMMENT ON COLUMN wkf_supply_request.source_location_id IS '单库房单据为空；双端点单据必填（出方）';
COMMENT ON COLUMN wkf_supply_request.purpose_type_enum IS '单库房单据必填；双端点单据必填';
COMMENT ON COLUMN wkf_supply_request.purpose_location_id IS '单库房就是目的；双端点为进方';
COMMENT ON COLUMN wkf_supply_request.approver_id IS 'approver id';
COMMENT ON COLUMN wkf_supply_request.approval_time IS 'approval time';
COMMENT ON COLUMN wkf_supply_request.applicant_id IS 'applicant id';
COMMENT ON COLUMN wkf_supply_request.apply_time IS '提交审批时的申请时刻；草稿阶段可为空，提交时由服务端写入当前时间';
COMMENT ON COLUMN wkf_supply_request.start_time IS '效期区间起点（与 { end_time} 成对；可空表示未维护）';
COMMENT ON COLUMN wkf_supply_request.end_time IS '效期区间止点（入库入账时可用于库存项有效期等编排）';
COMMENT ON COLUMN wkf_supply_request.lot_number IS 'lot number';
COMMENT ON COLUMN wkf_supply_request.trace_no IS 'trace no';
COMMENT ON COLUMN wkf_supply_request.price IS 'price';
COMMENT ON COLUMN wkf_supply_request.total_price IS 'total price';
COMMENT ON COLUMN wkf_supply_request.remake IS 'remake';
COMMENT ON COLUMN wkf_supply_request.profit_loss_price IS '盘点盈亏金额：盘盈为正、盘亏为负；非盘点类型可空';

COMMENT ON COLUMN "public"."wkf_supply_request"."profit_loss_price" IS '盘点盈亏金额；盘盈为正、盘亏为负；非盘点类型可空';