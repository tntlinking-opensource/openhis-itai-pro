-- V101：库存明细快照静表（DM-DOC-001 · doc_inventory_item_snapshot）

CREATE TABLE IF NOT EXISTS "public"."doc_inventory_item_snapshot" (
    "id" int8 NOT NULL,
    "tenant_id" int8 NOT NULL,
    "bus_no" varchar(32) NOT NULL,
    "snapshot_time" timestamptz(6) NOT NULL,
    "inventory_item_id" int8 NOT NULL,
    "item_table" varchar(64) NOT NULL,
    "item_id" int8 NOT NULL,
    "charge_item_definition_id" int8,
    "warehouse_id" int8 NOT NULL,
    "supplier_id" int8 NOT NULL,
    "product_bus_no" varchar(64),
    "name" varchar(255) NOT NULL,
    "merchandise_name" varchar(255),
    "total_volume" varchar(255),
    "manufacturer" varchar(255),
    "approval_number" varchar(128),
    "yb_no" varchar(64),
    "trace_no" varchar(50000),
    "py_str" varchar(255),
    "wb_str" varchar(255),
    "lot_number" varchar(255) NOT NULL,
    "quantity" numeric(18, 2) NOT NULL,
    "package_unit_ratio" numeric(18, 4),
    "package_unit_code" varchar(64),
    "lot_number_cost_amount" numeric(18, 4),
    "lot_number_price_amount" numeric(18, 4),
    "line_cost_total" numeric(18, 4),
    "line_price_total" numeric(18, 4),
    "production_date" timestamptz(6),
    "expiration_date" timestamptz(6),
    "remaining_days" int4,
    "inventory_status_enum" varchar(32) NOT NULL,
    "chrgitm_lv" int4,
    "medication_category_code" varchar(64),
    "device_category_code" varchar(64),
    "dose_form_code" varchar(64),
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    CONSTRAINT "doc_inventory_item_snapshot_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE doc_inventory_item_snapshot IS '库存明细快照静表（DM-DOC-001）';
COMMENT ON COLUMN doc_inventory_item_snapshot.id IS '主键';
COMMENT ON COLUMN doc_inventory_item_snapshot.tenant_id IS '租户标识';
COMMENT ON COLUMN doc_inventory_item_snapshot.bus_no IS '批次单号（ABU+yyyyMMdd+2位序号）';
COMMENT ON COLUMN doc_inventory_item_snapshot.snapshot_time IS '快照时刻';
COMMENT ON COLUMN doc_inventory_item_snapshot.inventory_item_id IS '源库存项标识（写入时刻 wkf_inventory_item.id）';
COMMENT ON COLUMN doc_inventory_item_snapshot.item_table IS '物品定义表名';
COMMENT ON COLUMN doc_inventory_item_snapshot.item_id IS '物品定义标识';
COMMENT ON COLUMN doc_inventory_item_snapshot.charge_item_definition_id IS '收费项定义标识';
COMMENT ON COLUMN doc_inventory_item_snapshot.warehouse_id IS '库房标识（不含货位）';
COMMENT ON COLUMN doc_inventory_item_snapshot.supplier_id IS '供应商标识';
COMMENT ON COLUMN doc_inventory_item_snapshot.product_bus_no IS '项目编码';
COMMENT ON COLUMN doc_inventory_item_snapshot.name IS '项目名称';
COMMENT ON COLUMN doc_inventory_item_snapshot.merchandise_name IS '商品名称';
COMMENT ON COLUMN doc_inventory_item_snapshot.total_volume IS '规格';
COMMENT ON COLUMN doc_inventory_item_snapshot.manufacturer IS '厂家/产地';
COMMENT ON COLUMN doc_inventory_item_snapshot.approval_number IS '批准文号';
COMMENT ON COLUMN doc_inventory_item_snapshot.yb_no IS '医保码';
COMMENT ON COLUMN doc_inventory_item_snapshot.trace_no IS '追溯码';
COMMENT ON COLUMN doc_inventory_item_snapshot.py_str IS '拼音码（备份关键字/排序）';
COMMENT ON COLUMN doc_inventory_item_snapshot.wb_str IS '五笔码（备份关键字）';
COMMENT ON COLUMN doc_inventory_item_snapshot.lot_number IS '生产批号';
COMMENT ON COLUMN doc_inventory_item_snapshot.quantity IS '账面数量（拆零最小单位）';
COMMENT ON COLUMN doc_inventory_item_snapshot.package_unit_ratio IS '拆零↔包装倍数（1包装=ratio拆零）';
COMMENT ON COLUMN doc_inventory_item_snapshot.package_unit_code IS '包装单位编码';
COMMENT ON COLUMN doc_inventory_item_snapshot.lot_number_cost_amount IS '批号进价（包装整包）';
COMMENT ON COLUMN doc_inventory_item_snapshot.lot_number_price_amount IS '批号售价（包装整包）';
COMMENT ON COLUMN doc_inventory_item_snapshot.line_cost_total IS '进价行总额（写入时刻冻结）';
COMMENT ON COLUMN doc_inventory_item_snapshot.line_price_total IS '售价行总额（写入时刻冻结）';
COMMENT ON COLUMN doc_inventory_item_snapshot.production_date IS '生产日期';
COMMENT ON COLUMN doc_inventory_item_snapshot.expiration_date IS '效期';
COMMENT ON COLUMN doc_inventory_item_snapshot.remaining_days IS '剩余过期天数（写入时刻冻结）';
COMMENT ON COLUMN doc_inventory_item_snapshot.inventory_status_enum IS '供应状态（PublicationStatus code）';
COMMENT ON COLUMN doc_inventory_item_snapshot.chrgitm_lv IS '医保等级码';
COMMENT ON COLUMN doc_inventory_item_snapshot.medication_category_code IS '药品分类编码';
COMMENT ON COLUMN doc_inventory_item_snapshot.device_category_code IS '耗材分类编码';
COMMENT ON COLUMN doc_inventory_item_snapshot.dose_form_code IS '剂型编码';
COMMENT ON COLUMN doc_inventory_item_snapshot.created_at IS '创建时间';
COMMENT ON COLUMN doc_inventory_item_snapshot.updated_at IS '最后更新时间';
COMMENT ON COLUMN doc_inventory_item_snapshot.created_by IS '创建人';
COMMENT ON COLUMN doc_inventory_item_snapshot.updated_by IS '最后更新人';
COMMENT ON COLUMN doc_inventory_item_snapshot.is_deleted IS '软删标记';
COMMENT ON COLUMN doc_inventory_item_snapshot.deleted_at IS '删除时间';
COMMENT ON COLUMN doc_inventory_item_snapshot.deleted_by IS '删除人';

CREATE UNIQUE INDEX IF NOT EXISTS "uk_doc_inv_snap_tenant_bus_item"
    ON "public"."doc_inventory_item_snapshot" ("tenant_id", "bus_no", "inventory_item_id")
    WHERE "is_deleted" = false;

CREATE INDEX IF NOT EXISTS "idx_doc_inv_snap_tenant_bus"
    ON "public"."doc_inventory_item_snapshot" ("tenant_id", "bus_no")
    WHERE "is_deleted" = false;

CREATE INDEX IF NOT EXISTS "idx_doc_inv_snap_tenant_bus_py"
    ON "public"."doc_inventory_item_snapshot" ("tenant_id", "bus_no", "py_str")
    WHERE "is_deleted" = false;
