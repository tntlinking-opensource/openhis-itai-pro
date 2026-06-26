-- wkf_service_request_detail（建表）
-- Flyway V69

CREATE TABLE IF NOT EXISTS "public"."wkf_service_request_detail" (
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
    "service_req_id" int8 NOT NULL,
    "target_table" varchar(64),
    "target_id" int8,
    "remark" varchar(255),
    CONSTRAINT "wkf_service_request_detail_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE wkf_service_request_detail IS '工作流 service request detail';
COMMENT ON COLUMN wkf_service_request_detail.id IS '主键';
COMMENT ON COLUMN wkf_service_request_detail.tenant_id IS '租户标识';
COMMENT ON COLUMN wkf_service_request_detail.created_at IS '创建时间';
COMMENT ON COLUMN wkf_service_request_detail.updated_at IS '最后更新时间';
COMMENT ON COLUMN wkf_service_request_detail.created_by IS '创建人';
COMMENT ON COLUMN wkf_service_request_detail.updated_by IS '最后更新人';
COMMENT ON COLUMN wkf_service_request_detail.is_deleted IS '软删标记';
COMMENT ON COLUMN wkf_service_request_detail.deleted_at IS '删除时间';
COMMENT ON COLUMN wkf_service_request_detail.deleted_by IS '删除人';
COMMENT ON COLUMN wkf_service_request_detail.version IS '乐观锁版本号';
COMMENT ON COLUMN wkf_service_request_detail.service_req_id IS '服务申请明细子实体';
COMMENT ON COLUMN wkf_service_request_detail.target_table IS '明细目标表名（如 dia_body_structure、adm_specimen_definition）';
COMMENT ON COLUMN wkf_service_request_detail.target_id IS '明细目标表主键';
COMMENT ON COLUMN wkf_service_request_detail.remark IS '明细备注';
