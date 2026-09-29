-- pacs_imaging_study / pacs_media / pacs_viewer_endpoint
-- Flyway V1 (imaging)

CREATE TABLE IF NOT EXISTS "public"."pacs_imaging_study" (
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
    "study_instance_uid" varchar(128) NOT NULL,
    "series_instance_uid" varchar(128),
    "accession_number" varchar(64),
    "modality_code" varchar(32),
    CONSTRAINT "pacs_imaging_study_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE pacs_imaging_study IS 'DICOM Study 索引聚合根';
COMMENT ON COLUMN pacs_imaging_study.id IS '主键';
COMMENT ON COLUMN pacs_imaging_study.tenant_id IS '租户标识';
COMMENT ON COLUMN pacs_imaging_study.created_at IS '创建时间';
COMMENT ON COLUMN pacs_imaging_study.updated_at IS '最后更新时间';
COMMENT ON COLUMN pacs_imaging_study.created_by IS '创建人';
COMMENT ON COLUMN pacs_imaging_study.updated_by IS '最后更新人';
COMMENT ON COLUMN pacs_imaging_study.is_deleted IS '软删标记';
COMMENT ON COLUMN pacs_imaging_study.deleted_at IS '删除时间';
COMMENT ON COLUMN pacs_imaging_study.deleted_by IS '删除人';
COMMENT ON COLUMN pacs_imaging_study.work_item_id IS '检查工单标识；租户内唯一';
COMMENT ON COLUMN pacs_imaging_study.study_instance_uid IS 'DICOM Study Instance UID；租户内唯一';
COMMENT ON COLUMN pacs_imaging_study.series_instance_uid IS '主 Series Instance UID；可后补';
COMMENT ON COLUMN pacs_imaging_study.accession_number IS '冗余检查号';
COMMENT ON COLUMN pacs_imaging_study.modality_code IS '模态码';

CREATE UNIQUE INDEX IF NOT EXISTS uk_pacs_imaging_study_tenant_work_item
    ON pacs_imaging_study (tenant_id, work_item_id)
    WHERE is_deleted = false;

CREATE UNIQUE INDEX IF NOT EXISTS uk_pacs_imaging_study_tenant_study_uid
    ON pacs_imaging_study (tenant_id, study_instance_uid)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_pacs_imaging_study_work_item_id ON pacs_imaging_study (work_item_id);

CREATE TABLE IF NOT EXISTS "public"."pacs_media" (
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
    "object_key" varchar(512) NOT NULL,
    "content_type" varchar(64) NOT NULL,
    "source_enum" varchar(32) NOT NULL,
    CONSTRAINT "pacs_media_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE pacs_media IS '关键图/截图对象键索引';
COMMENT ON COLUMN pacs_media.id IS '主键';
COMMENT ON COLUMN pacs_media.tenant_id IS '租户标识';
COMMENT ON COLUMN pacs_media.created_at IS '创建时间';
COMMENT ON COLUMN pacs_media.updated_at IS '最后更新时间';
COMMENT ON COLUMN pacs_media.created_by IS '创建人';
COMMENT ON COLUMN pacs_media.updated_by IS '最后更新人';
COMMENT ON COLUMN pacs_media.is_deleted IS '软删标记';
COMMENT ON COLUMN pacs_media.deleted_at IS '删除时间';
COMMENT ON COLUMN pacs_media.deleted_by IS '删除人';
COMMENT ON COLUMN pacs_media.work_item_id IS '所属检查工单';
COMMENT ON COLUMN pacs_media.object_key IS '对象存储键；租户内唯一';
COMMENT ON COLUMN pacs_media.content_type IS '内容类型，仅 image/jpeg 或 image/png';
COMMENT ON COLUMN pacs_media.source_enum IS '来源枚举 code：KEY_IMAGE / SCREENSHOT';

CREATE UNIQUE INDEX IF NOT EXISTS uk_pacs_media_tenant_object_key
    ON pacs_media (tenant_id, object_key)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_pacs_media_work_item_id ON pacs_media (work_item_id);

CREATE TABLE IF NOT EXISTS "public"."pacs_viewer_endpoint" (
    "id" int8 NOT NULL,
    "tenant_id" int8,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    "organization_id" int8,
    "modality_code" varchar(32),
    "base_url" varchar(512) NOT NULL,
    "enabled" bool NOT NULL DEFAULT true,
    CONSTRAINT "pacs_viewer_endpoint_pkey" PRIMARY KEY ("id")
);
COMMENT ON TABLE pacs_viewer_endpoint IS '阅片根地址配置';
COMMENT ON COLUMN pacs_viewer_endpoint.id IS '主键';
COMMENT ON COLUMN pacs_viewer_endpoint.tenant_id IS '租户标识';
COMMENT ON COLUMN pacs_viewer_endpoint.created_at IS '创建时间';
COMMENT ON COLUMN pacs_viewer_endpoint.updated_at IS '最后更新时间';
COMMENT ON COLUMN pacs_viewer_endpoint.created_by IS '创建人';
COMMENT ON COLUMN pacs_viewer_endpoint.updated_by IS '最后更新人';
COMMENT ON COLUMN pacs_viewer_endpoint.is_deleted IS '软删标记';
COMMENT ON COLUMN pacs_viewer_endpoint.deleted_at IS '删除时间';
COMMENT ON COLUMN pacs_viewer_endpoint.deleted_by IS '删除人';
COMMENT ON COLUMN pacs_viewer_endpoint.organization_id IS '机构标识；空表示全局默认';
COMMENT ON COLUMN pacs_viewer_endpoint.modality_code IS '模态码；空表示该范围默认';
COMMENT ON COLUMN pacs_viewer_endpoint.base_url IS 'OHIF http(s) 根地址，不含 StudyUID';
COMMENT ON COLUMN pacs_viewer_endpoint.enabled IS '是否启用';

-- 空与空相等：organization_id / modality_code 用哨兵参与唯一约束（仅启用行）
CREATE UNIQUE INDEX IF NOT EXISTS uk_pacs_viewer_endpoint_tenant_org_modality_enabled
    ON pacs_viewer_endpoint (
        tenant_id,
        COALESCE(organization_id, -1),
        COALESCE(modality_code, '')
    )
    WHERE enabled = true AND is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_pacs_viewer_endpoint_tenant_id ON pacs_viewer_endpoint (tenant_id);
CREATE INDEX IF NOT EXISTS idx_pacs_viewer_endpoint_organization_id ON pacs_viewer_endpoint (organization_id);
