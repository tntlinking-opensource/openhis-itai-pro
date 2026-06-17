-- =============================================================================
-- V4 · 渠道、叫号与资源画像
-- =============================================================================

-- ---------------------------------------------------------------------------
-- bkg_channel_config · 预约渠道配置
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bkg_channel_config (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    organization_id BIGINT,
    channel_code VARCHAR(32) NOT NULL,
    channel_name VARCHAR(64) NOT NULL,
    enabled_flag BOOLEAN NOT NULL DEFAULT TRUE,
    quota_percent INT NOT NULL DEFAULT 100,
    notify_template_code VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    version INT NOT NULL DEFAULT 0
);

COMMENT ON TABLE bkg_channel_config IS '预约渠道：窗口、APP、第三方等配额与通知模板';
COMMENT ON COLUMN bkg_channel_config.id IS '主键';
COMMENT ON COLUMN bkg_channel_config.tenant_id IS '租户标识';
COMMENT ON COLUMN bkg_channel_config.organization_id IS '机构标识；空=租户默认';
COMMENT ON COLUMN bkg_channel_config.channel_code IS '渠道编码：WINDOW / APP / WECHAT 等';
COMMENT ON COLUMN bkg_channel_config.channel_name IS '渠道名称';
COMMENT ON COLUMN bkg_channel_config.enabled_flag IS '是否启用';
COMMENT ON COLUMN bkg_channel_config.quota_percent IS '号源配额百分比（0-100）';
COMMENT ON COLUMN bkg_channel_config.notify_template_code IS '通知模板编码';
COMMENT ON COLUMN bkg_channel_config.created_at IS '创建时间';
COMMENT ON COLUMN bkg_channel_config.updated_at IS '最后更新时间';
COMMENT ON COLUMN bkg_channel_config.created_by IS '创建人';
COMMENT ON COLUMN bkg_channel_config.updated_by IS '最后更新人';
COMMENT ON COLUMN bkg_channel_config.is_deleted IS '软删标记';
COMMENT ON COLUMN bkg_channel_config.deleted_at IS '删除时间';
COMMENT ON COLUMN bkg_channel_config.deleted_by IS '删除人';
COMMENT ON COLUMN bkg_channel_config.version IS '乐观锁版本号';

-- ---------------------------------------------------------------------------
-- bkg_queue_display_config · 叫号大屏配置
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bkg_queue_display_config (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    organization_id BIGINT NOT NULL,
    location_id BIGINT,
    display_title VARCHAR(128),
    refresh_seconds INT NOT NULL DEFAULT 5,
    voice_enabled_flag BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    version INT NOT NULL DEFAULT 0
);

COMMENT ON TABLE bkg_queue_display_config IS '候诊叫号大屏：刷新间隔、语音播报';
COMMENT ON COLUMN bkg_queue_display_config.id IS '主键';
COMMENT ON COLUMN bkg_queue_display_config.tenant_id IS '租户标识';
COMMENT ON COLUMN bkg_queue_display_config.organization_id IS '机构标识';
COMMENT ON COLUMN bkg_queue_display_config.location_id IS '候诊区 Location 主键';
COMMENT ON COLUMN bkg_queue_display_config.display_title IS '大屏标题';
COMMENT ON COLUMN bkg_queue_display_config.refresh_seconds IS '数据刷新间隔（秒）';
COMMENT ON COLUMN bkg_queue_display_config.voice_enabled_flag IS '是否启用语音叫号';
COMMENT ON COLUMN bkg_queue_display_config.created_at IS '创建时间';
COMMENT ON COLUMN bkg_queue_display_config.updated_at IS '最后更新时间';
COMMENT ON COLUMN bkg_queue_display_config.created_by IS '创建人';
COMMENT ON COLUMN bkg_queue_display_config.updated_by IS '最后更新人';
COMMENT ON COLUMN bkg_queue_display_config.is_deleted IS '软删标记';
COMMENT ON COLUMN bkg_queue_display_config.deleted_at IS '删除时间';
COMMENT ON COLUMN bkg_queue_display_config.deleted_by IS '删除人';
COMMENT ON COLUMN bkg_queue_display_config.version IS '乐观锁版本号';

-- ---------------------------------------------------------------------------
-- bkg_resource_profile · 资源画像
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bkg_resource_profile (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    organization_id BIGINT NOT NULL,
    resource_type_code VARCHAR(32) NOT NULL,
    resource_id BIGINT NOT NULL,
    profile_name VARCHAR(128),
    profile_json TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    version INT NOT NULL DEFAULT 0
);

COMMENT ON TABLE bkg_resource_profile IS '预约资源扩展画像（医师/设备/诊室等 JSON 属性）';
COMMENT ON COLUMN bkg_resource_profile.id IS '主键';
COMMENT ON COLUMN bkg_resource_profile.tenant_id IS '租户标识';
COMMENT ON COLUMN bkg_resource_profile.organization_id IS '机构标识';
COMMENT ON COLUMN bkg_resource_profile.resource_type_code IS '资源类型：PRACTITIONER / DEVICE / LOCATION 等';
COMMENT ON COLUMN bkg_resource_profile.resource_id IS '资源主键';
COMMENT ON COLUMN bkg_resource_profile.profile_name IS '画像名称';
COMMENT ON COLUMN bkg_resource_profile.profile_json IS '扩展属性 JSON';
COMMENT ON COLUMN bkg_resource_profile.created_at IS '创建时间';
COMMENT ON COLUMN bkg_resource_profile.updated_at IS '最后更新时间';
COMMENT ON COLUMN bkg_resource_profile.created_by IS '创建人';
COMMENT ON COLUMN bkg_resource_profile.updated_by IS '最后更新人';
COMMENT ON COLUMN bkg_resource_profile.is_deleted IS '软删标记';
COMMENT ON COLUMN bkg_resource_profile.deleted_at IS '删除时间';
COMMENT ON COLUMN bkg_resource_profile.deleted_by IS '删除人';
COMMENT ON COLUMN bkg_resource_profile.version IS '乐观锁版本号';
