-- peis_service_request_ext（建表）
-- Flyway V79 · DM-PEIS-001

CREATE TABLE IF NOT EXISTS "public"."peis_service_request_ext" (
    "service_request_id" int8 NOT NULL,
    "tenant_id" int8,
    "org_id" int8,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    "version" int4 NOT NULL DEFAULT 0,
    "exec_status_enum" varchar(64) NOT NULL DEFAULT 'pending',
    "lis_dispatch_enum" varchar(64) NOT NULL DEFAULT 'notSent',
    "pacs_dispatch_enum" varchar(64) NOT NULL DEFAULT 'notSent',
    CONSTRAINT "peis_service_request_ext_pkey" PRIMARY KEY ("service_request_id")
);

COMMENT ON TABLE peis_service_request_ext IS '体检 ServiceRequest 执行/外发扩展（无 barcode，D6）';
COMMENT ON COLUMN peis_service_request_ext.service_request_id IS '服务请求标识（主键）';
COMMENT ON COLUMN peis_service_request_ext.tenant_id IS '租户标识';
COMMENT ON COLUMN peis_service_request_ext.org_id IS '机构标识';
COMMENT ON COLUMN peis_service_request_ext.created_at IS '创建时间';
COMMENT ON COLUMN peis_service_request_ext.updated_at IS '最后更新时间';
COMMENT ON COLUMN peis_service_request_ext.created_by IS '创建人';
COMMENT ON COLUMN peis_service_request_ext.updated_by IS '最后更新人';
COMMENT ON COLUMN peis_service_request_ext.is_deleted IS '软删标记';
COMMENT ON COLUMN peis_service_request_ext.deleted_at IS '删除时间';
COMMENT ON COLUMN peis_service_request_ext.deleted_by IS '删除人';
COMMENT ON COLUMN peis_service_request_ext.version IS '乐观锁版本号';
COMMENT ON COLUMN peis_service_request_ext.exec_status_enum IS '执行状态枚举';
COMMENT ON COLUMN peis_service_request_ext.lis_dispatch_enum IS 'LIS 外发状态枚举';
COMMENT ON COLUMN peis_service_request_ext.pacs_dispatch_enum IS 'PACS 外发状态枚举';
