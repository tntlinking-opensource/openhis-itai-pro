-- inventory_lock（建表）
-- Flyway V53

CREATE TABLE IF NOT EXISTS "public"."inventory_lock" (
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
    "inventory_id" int8 NOT NULL,
    "lock_flag" bool NOT NULL,
    "status" varchar(255) NOT NULL,
    "bus_no" varchar(255) NOT NULL,
    "order_flag" int4 NOT NULL,
    CONSTRAINT "inventory_lock_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE inventory_lock IS '库存锁定';
COMMENT ON COLUMN inventory_lock.id IS '主键';
COMMENT ON COLUMN inventory_lock.tenant_id IS '租户标识';
COMMENT ON COLUMN inventory_lock.created_at IS '创建时间';
COMMENT ON COLUMN inventory_lock.updated_at IS '最后更新时间';
COMMENT ON COLUMN inventory_lock.created_by IS '创建人';
COMMENT ON COLUMN inventory_lock.updated_by IS '最后更新人';
COMMENT ON COLUMN inventory_lock.is_deleted IS '软删标记';
COMMENT ON COLUMN inventory_lock.deleted_at IS '删除时间';
COMMENT ON COLUMN inventory_lock.deleted_by IS '删除人';
COMMENT ON COLUMN inventory_lock.version IS '乐观锁版本号';
COMMENT ON COLUMN inventory_lock.inventory_id IS '库存锁定记录（DM-WKF-008 §3.2）';
COMMENT ON COLUMN inventory_lock.lock_flag IS '锁定标识：true 已锁定、false 未锁定';
COMMENT ON COLUMN inventory_lock.status IS '锁定状态：见 LockStatus';
COMMENT ON COLUMN inventory_lock.bus_no IS '业务单号';
COMMENT ON COLUMN inventory_lock.order_flag IS '同一业务单内的顺序号';
