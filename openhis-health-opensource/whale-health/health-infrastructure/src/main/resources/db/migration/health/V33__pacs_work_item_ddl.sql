-- pacs_work_item / pacs_work_item_order / pacs_modality_capability
-- Flyway V11

CREATE TABLE IF NOT EXISTS "public"."pacs_work_item" (
    "id" int8 NOT NULL,
    "tenant_id" int8,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    "accession_number" varchar(64),
    "patient_id" int8 NOT NULL,
    "encounter_id" int8 NOT NULL,
    "organization_id" int8 NOT NULL,
    "status_enum" varchar(32) NOT NULL,
    "modality_code" varchar(32),
    "body_site_code" varchar(64),
    "device_id" int8,
    "performer_practitioner_id" int8,
    "scheduled_time" timestamptz(6),
    "performed_time" timestamptz(6),
    "has_media" bool NOT NULL DEFAULT false,
    CONSTRAINT "pacs_work_item_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE pacs_work_item IS '检查工单聚合根';
COMMENT ON COLUMN pacs_work_item.id IS '主键';
COMMENT ON COLUMN pacs_work_item.tenant_id IS '租户标识';
COMMENT ON COLUMN pacs_work_item.created_at IS '创建时间';
COMMENT ON COLUMN pacs_work_item.updated_at IS '最后更新时间';
COMMENT ON COLUMN pacs_work_item.created_by IS '创建人';
COMMENT ON COLUMN pacs_work_item.updated_by IS '最后更新人';
COMMENT ON COLUMN pacs_work_item.is_deleted IS '软删标记';
COMMENT ON COLUMN pacs_work_item.deleted_at IS '删除时间';
COMMENT ON COLUMN pacs_work_item.deleted_by IS '删除人';
COMMENT ON COLUMN pacs_work_item.accession_number IS '检查号；登记确认后非空';
COMMENT ON COLUMN pacs_work_item.patient_id IS '患者标识';
COMMENT ON COLUMN pacs_work_item.encounter_id IS '就诊标识';
COMMENT ON COLUMN pacs_work_item.organization_id IS '登记/执行科室';
COMMENT ON COLUMN pacs_work_item.status_enum IS '检查执行态枚举 code';
COMMENT ON COLUMN pacs_work_item.modality_code IS '模态码';
COMMENT ON COLUMN pacs_work_item.body_site_code IS '检查部位码';
COMMENT ON COLUMN pacs_work_item.device_id IS '执行设备标识';
COMMENT ON COLUMN pacs_work_item.performer_practitioner_id IS '执行人标识';
COMMENT ON COLUMN pacs_work_item.scheduled_time IS '计划检查时间';
COMMENT ON COLUMN pacs_work_item.performed_time IS '实际完成时间';
COMMENT ON COLUMN pacs_work_item.has_media IS '是否已有影像/关键图';

CREATE UNIQUE INDEX IF NOT EXISTS uk_pacs_work_item_tenant_accession
    ON pacs_work_item (tenant_id, accession_number)
    WHERE accession_number IS NOT NULL AND accession_number <> '';

CREATE INDEX IF NOT EXISTS idx_pacs_work_item_patient_id ON pacs_work_item (patient_id);
CREATE INDEX IF NOT EXISTS idx_pacs_work_item_encounter_id ON pacs_work_item (encounter_id);
CREATE INDEX IF NOT EXISTS idx_pacs_work_item_organization_id ON pacs_work_item (organization_id);
CREATE INDEX IF NOT EXISTS idx_pacs_work_item_status_enum ON pacs_work_item (status_enum);

CREATE TABLE IF NOT EXISTS "public"."pacs_work_item_order" (
    "id" int8 NOT NULL,
    "tenant_id" int8,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    "work_item_id" int8 NOT NULL,
    "service_request_id" int8 NOT NULL,
    "activity_id" int8 NOT NULL,
    "link_status_enum" varchar(32) NOT NULL,
    "group_code" varchar(64),
    "sort_number" int4,
    CONSTRAINT "pacs_work_item_order_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE pacs_work_item_order IS '检查工单医嘱关联';
COMMENT ON COLUMN pacs_work_item_order.id IS '主键';
COMMENT ON COLUMN pacs_work_item_order.tenant_id IS '租户标识';
COMMENT ON COLUMN pacs_work_item_order.created_at IS '创建时间';
COMMENT ON COLUMN pacs_work_item_order.updated_at IS '最后更新时间';
COMMENT ON COLUMN pacs_work_item_order.created_by IS '创建人';
COMMENT ON COLUMN pacs_work_item_order.updated_by IS '最后更新人';
COMMENT ON COLUMN pacs_work_item_order.is_deleted IS '软删标记';
COMMENT ON COLUMN pacs_work_item_order.deleted_at IS '删除时间';
COMMENT ON COLUMN pacs_work_item_order.deleted_by IS '删除人';
COMMENT ON COLUMN pacs_work_item_order.work_item_id IS '工单标识';
COMMENT ON COLUMN pacs_work_item_order.service_request_id IS 'ServiceRequest 标识';
COMMENT ON COLUMN pacs_work_item_order.activity_id IS 'ActivityDefinition 标识';
COMMENT ON COLUMN pacs_work_item_order.link_status_enum IS '关联态枚举 code';
COMMENT ON COLUMN pacs_work_item_order.group_code IS '合并组号';
COMMENT ON COLUMN pacs_work_item_order.sort_number IS '组内排序';

CREATE UNIQUE INDEX IF NOT EXISTS uk_pacs_work_item_order_work_item_sr
    ON pacs_work_item_order (work_item_id, service_request_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_pacs_work_item_order_service_request
    ON pacs_work_item_order (service_request_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_pacs_work_item_order_work_item_id ON pacs_work_item_order (work_item_id);

CREATE TABLE IF NOT EXISTS "public"."pacs_modality_capability" (
    "id" int8 NOT NULL,
    "tenant_id" int8,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    "activity_id" int8 NOT NULL,
    "modality_code" varchar(32) NOT NULL,
    "enabled_flag" bool NOT NULL DEFAULT true,
    "organization_id" int8,
    CONSTRAINT "pacs_modality_capability_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE pacs_modality_capability IS '模态能力配置';
COMMENT ON COLUMN pacs_modality_capability.id IS '主键';
COMMENT ON COLUMN pacs_modality_capability.tenant_id IS '租户标识';
COMMENT ON COLUMN pacs_modality_capability.created_at IS '创建时间';
COMMENT ON COLUMN pacs_modality_capability.updated_at IS '最后更新时间';
COMMENT ON COLUMN pacs_modality_capability.created_by IS '创建人';
COMMENT ON COLUMN pacs_modality_capability.updated_by IS '最后更新人';
COMMENT ON COLUMN pacs_modality_capability.is_deleted IS '软删标记';
COMMENT ON COLUMN pacs_modality_capability.deleted_at IS '删除时间';
COMMENT ON COLUMN pacs_modality_capability.deleted_by IS '删除人';
COMMENT ON COLUMN pacs_modality_capability.activity_id IS 'ActivityDefinition 标识';
COMMENT ON COLUMN pacs_modality_capability.modality_code IS '模态码';
COMMENT ON COLUMN pacs_modality_capability.enabled_flag IS '是否启用';
COMMENT ON COLUMN pacs_modality_capability.organization_id IS '可选科室范围';

CREATE UNIQUE INDEX IF NOT EXISTS uk_pacs_modality_capability_tenant_activity
    ON pacs_modality_capability (tenant_id, activity_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_pacs_modality_capability_activity_id ON pacs_modality_capability (activity_id);
