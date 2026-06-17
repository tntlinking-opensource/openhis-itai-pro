-- =============================================================================
-- V2 · 院点策略
-- 放号策略、挂号衔接策略、停诊与预约处置策略（07 §4.2）
-- =============================================================================

-- ---------------------------------------------------------------------------
-- bkg_release_policy · 放号策略
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bkg_release_policy (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    organization_id BIGINT,
    policy_code VARCHAR(64) NOT NULL,
    policy_name VARCHAR(128) NOT NULL,
    advance_days INT NOT NULL DEFAULT 7,
    slot_minutes INT NOT NULL DEFAULT 30,
    capacity_per_slot INT NOT NULL DEFAULT 10,
    channel_quota_json TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    version INT NOT NULL DEFAULT 0
);

COMMENT ON TABLE bkg_release_policy IS '放号策略：提前天数、时段粒度、每段容量、渠道配额';
COMMENT ON COLUMN bkg_release_policy.id IS '主键';
COMMENT ON COLUMN bkg_release_policy.tenant_id IS '租户标识';
COMMENT ON COLUMN bkg_release_policy.organization_id IS '机构标识；空=租户默认';
COMMENT ON COLUMN bkg_release_policy.policy_code IS '策略编码（机构内唯一）';
COMMENT ON COLUMN bkg_release_policy.policy_name IS '策略名称';
COMMENT ON COLUMN bkg_release_policy.advance_days IS '可预约提前天数';
COMMENT ON COLUMN bkg_release_policy.slot_minutes IS '默认时段粒度（分钟）';
COMMENT ON COLUMN bkg_release_policy.capacity_per_slot IS '默认每时段容量';
COMMENT ON COLUMN bkg_release_policy.channel_quota_json IS '渠道配额 JSON，如 {"WINDOW":60,"APP":40}';
COMMENT ON COLUMN bkg_release_policy.created_at IS '创建时间';
COMMENT ON COLUMN bkg_release_policy.updated_at IS '最后更新时间';
COMMENT ON COLUMN bkg_release_policy.created_by IS '创建人';
COMMENT ON COLUMN bkg_release_policy.updated_by IS '最后更新人';
COMMENT ON COLUMN bkg_release_policy.is_deleted IS '软删标记';
COMMENT ON COLUMN bkg_release_policy.deleted_at IS '删除时间';
COMMENT ON COLUMN bkg_release_policy.deleted_by IS '删除人';
COMMENT ON COLUMN bkg_release_policy.version IS '乐观锁版本号';

-- ---------------------------------------------------------------------------
-- bkg_registration_policy · 挂号衔接策略
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bkg_registration_policy (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    organization_id BIGINT,
    walk_in_slot_mode VARCHAR(32) NOT NULL DEFAULT 'APPOINTMENT_FIRST',
    auto_check_in_on_register BOOLEAN NOT NULL DEFAULT FALSE,
    on_cancel_policy VARCHAR(32) NOT NULL DEFAULT 'RELEASE_SLOT_ONLY',
    allow_edit_practitioner_from_appointment BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    version INT NOT NULL DEFAULT 0
);

COMMENT ON TABLE bkg_registration_policy IS '挂号衔接策略：现场取号、取消处置、医师变更等';
COMMENT ON COLUMN bkg_registration_policy.id IS '主键';
COMMENT ON COLUMN bkg_registration_policy.tenant_id IS '租户标识';
COMMENT ON COLUMN bkg_registration_policy.organization_id IS '机构标识；空=租户默认';
COMMENT ON COLUMN bkg_registration_policy.walk_in_slot_mode IS '现场取号模式：APPOINTMENT_FIRST / SLOT_FIRST 等';
COMMENT ON COLUMN bkg_registration_policy.auto_check_in_on_register IS '挂号时是否自动签到';
COMMENT ON COLUMN bkg_registration_policy.on_cancel_policy IS '取消预约处置：RELEASE_SLOT_ONLY / KEEP_SLOT 等';
COMMENT ON COLUMN bkg_registration_policy.allow_edit_practitioner_from_appointment IS '是否允许从预约修改接诊医师';
COMMENT ON COLUMN bkg_registration_policy.created_at IS '创建时间';
COMMENT ON COLUMN bkg_registration_policy.updated_at IS '最后更新时间';
COMMENT ON COLUMN bkg_registration_policy.created_by IS '创建人';
COMMENT ON COLUMN bkg_registration_policy.updated_by IS '最后更新人';
COMMENT ON COLUMN bkg_registration_policy.is_deleted IS '软删标记';
COMMENT ON COLUMN bkg_registration_policy.deleted_at IS '删除时间';
COMMENT ON COLUMN bkg_registration_policy.deleted_by IS '删除人';
COMMENT ON COLUMN bkg_registration_policy.version IS '乐观锁版本号';

-- ---------------------------------------------------------------------------
-- bkg_schedule_change_policy · 停诊与预约处置策略
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bkg_schedule_change_policy (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    organization_id BIGINT,
    on_practitioner_stop_mode VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
    transfer_match_rule VARCHAR(32) NOT NULL DEFAULT 'SAME_SERVICE_SAME_SLOT',
    transfer_require_same_service BOOLEAN NOT NULL DEFAULT TRUE,
    transfer_allow_cross_practitioner BOOLEAN NOT NULL DEFAULT TRUE,
    transfer_patient_confirm_mode VARCHAR(32) NOT NULL DEFAULT 'NONE',
    block_deactivate_if_unhandled BOOLEAN NOT NULL DEFAULT TRUE,
    notify_on_transfer BOOLEAN NOT NULL DEFAULT TRUE,
    notify_on_cancel_stop BOOLEAN NOT NULL DEFAULT TRUE,
    freeze_source_slot_on_preview BOOLEAN NOT NULL DEFAULT TRUE,
    freeze_ttl_minutes INT NOT NULL DEFAULT 15,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    version INT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_bkg_schedule_change_policy_org
    ON bkg_schedule_change_policy (organization_id)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE bkg_schedule_change_policy IS '停诊与预约处置院点策略（07 §4.2）';
COMMENT ON COLUMN bkg_schedule_change_policy.id IS '主键';
COMMENT ON COLUMN bkg_schedule_change_policy.tenant_id IS '租户标识';
COMMENT ON COLUMN bkg_schedule_change_policy.organization_id IS '机构标识；空=租户默认';
COMMENT ON COLUMN bkg_schedule_change_policy.on_practitioner_stop_mode IS '停诊默认处置：TRANSFER / RESCHEDULE / CANCEL / MANUAL';
COMMENT ON COLUMN bkg_schedule_change_policy.transfer_match_rule IS '转交匹配规则';
COMMENT ON COLUMN bkg_schedule_change_policy.transfer_require_same_service IS '转交是否要求同号源类型';
COMMENT ON COLUMN bkg_schedule_change_policy.transfer_allow_cross_practitioner IS '是否允许跨医师转交';
COMMENT ON COLUMN bkg_schedule_change_policy.transfer_patient_confirm_mode IS '转交患者确认：NONE / SMS_CONFIRM / WINDOW_CONFIRM';
COMMENT ON COLUMN bkg_schedule_change_policy.block_deactivate_if_unhandled IS '存在未处置预约时禁止完成停诊';
COMMENT ON COLUMN bkg_schedule_change_policy.notify_on_transfer IS '转交后是否通知患者';
COMMENT ON COLUMN bkg_schedule_change_policy.notify_on_cancel_stop IS '停诊取消预约时是否通知';
COMMENT ON COLUMN bkg_schedule_change_policy.freeze_source_slot_on_preview IS '停诊预览后冻结源号源，防并发新约';
COMMENT ON COLUMN bkg_schedule_change_policy.freeze_ttl_minutes IS '冻结超时分钟数（TTL 自动释放）';
COMMENT ON COLUMN bkg_schedule_change_policy.created_at IS '创建时间';
COMMENT ON COLUMN bkg_schedule_change_policy.updated_at IS '最后更新时间';
COMMENT ON COLUMN bkg_schedule_change_policy.created_by IS '创建人';
COMMENT ON COLUMN bkg_schedule_change_policy.updated_by IS '最后更新人';
COMMENT ON COLUMN bkg_schedule_change_policy.is_deleted IS '软删标记';
COMMENT ON COLUMN bkg_schedule_change_policy.deleted_at IS '删除时间';
COMMENT ON COLUMN bkg_schedule_change_policy.deleted_by IS '删除人';
COMMENT ON COLUMN bkg_schedule_change_policy.version IS '乐观锁版本号';
