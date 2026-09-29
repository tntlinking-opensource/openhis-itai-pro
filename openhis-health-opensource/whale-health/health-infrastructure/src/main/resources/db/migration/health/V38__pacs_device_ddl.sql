-- pacs_device
-- Flyway V16

CREATE TABLE IF NOT EXISTS "public"."pacs_device" (
    "id" int8 NOT NULL,
    "tenant_id" int8,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    "device_code" varchar(64) NOT NULL,
    "name" varchar(128) NOT NULL,
    "modality_code" varchar(32) NOT NULL,
    "ae_title" varchar(64),
    "station_name" varchar(128),
    "enabled_flag" bool NOT NULL DEFAULT true,
    "sort_order" int4,
    "remark" varchar(512),
    CONSTRAINT "pacs_device_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE pacs_device IS '检查设备台账';
COMMENT ON COLUMN pacs_device.id IS '主键';
COMMENT ON COLUMN pacs_device.tenant_id IS '租户标识';
COMMENT ON COLUMN pacs_device.created_at IS '创建时间';
COMMENT ON COLUMN pacs_device.updated_at IS '最后更新时间';
COMMENT ON COLUMN pacs_device.created_by IS '创建人';
COMMENT ON COLUMN pacs_device.updated_by IS '最后更新人';
COMMENT ON COLUMN pacs_device.is_deleted IS '软删标记';
COMMENT ON COLUMN pacs_device.deleted_at IS '删除时间';
COMMENT ON COLUMN pacs_device.deleted_by IS '删除人';
COMMENT ON COLUMN pacs_device.device_code IS '业务编码；租户内未软删唯一（领域校验）';
COMMENT ON COLUMN pacs_device.name IS '设备名称';
COMMENT ON COLUMN pacs_device.modality_code IS '模态码';
COMMENT ON COLUMN pacs_device.ae_title IS 'DICOM AE Title';
COMMENT ON COLUMN pacs_device.station_name IS '机房/室名';
COMMENT ON COLUMN pacs_device.enabled_flag IS '是否启用';
COMMENT ON COLUMN pacs_device.sort_order IS '列表排序';
COMMENT ON COLUMN pacs_device.remark IS '备注';

CREATE INDEX IF NOT EXISTS idx_pacs_device_tenant_modality
    ON pacs_device (tenant_id, modality_code);

CREATE INDEX IF NOT EXISTS idx_pacs_device_tenant_device_code
    ON pacs_device (tenant_id, device_code);
