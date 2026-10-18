-- LIS ModularFlyway V1 — lis_work_item / lis_work_item_order（P0）
-- history: flyway_schema_history_lis
-- switch: zxplat.modules.lis.enabled

CREATE TABLE IF NOT EXISTS "public"."lis_work_item" (
    "id" int8 NOT NULL,
    "tenant_id" int8,
    "org_id" int8,
    "created_at" timestamp(6),
    "updated_at" timestamp(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamp(6),
    "deleted_by" int8,
    "version" int4 NOT NULL DEFAULT 0,
    "barcode" varchar(64),
    "sample_no" varchar(64),
    "patient_id" int8,
    "encounter_id" int8,
    "status_enum" varchar(32) NOT NULL,
    CONSTRAINT "lis_work_item_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE lis_work_item IS '检验工单（标本物理粒度）';

CREATE TABLE IF NOT EXISTS "public"."lis_work_item_order" (
    "id" int8 NOT NULL,
    "tenant_id" int8,
    "org_id" int8,
    "created_at" timestamp(6),
    "updated_at" timestamp(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamp(6),
    "deleted_by" int8,
    "version" int4 NOT NULL DEFAULT 0,
    "work_item_id" int8 NOT NULL,
    "service_request_id" int8 NOT NULL,
    "activity_id" int8,
    "link_status_enum" varchar(32),
    "sort_number" int4,
    CONSTRAINT "lis_work_item_order_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE lis_work_item_order IS '检验工单并管明细';

CREATE INDEX IF NOT EXISTS idx_lis_work_item_order_work
    ON lis_work_item_order (work_item_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_work_item_order_sr
    ON lis_work_item_order (work_item_id, service_request_id) WHERE is_deleted = false;