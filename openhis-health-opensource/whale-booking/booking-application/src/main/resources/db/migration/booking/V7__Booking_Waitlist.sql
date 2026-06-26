-- =============================================================================
-- V7 · 候补队列（BR-EXT-011）
-- 号源满时入候补；释放号源后按 queue_position 自动提升为正式预约
-- =============================================================================

CREATE TABLE IF NOT EXISTS bkg_waitlist_entry (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    organization_id BIGINT NOT NULL,
    patient_id BIGINT NOT NULL,
    slot_id BIGINT NOT NULL,
    schedule_id BIGINT NOT NULL,
    queue_position INT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'WAITING',
    promoted_appointment_id BIGINT,
    cancel_reason VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    version INT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_waitlist_slot_status ON bkg_waitlist_entry (slot_id, status)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_waitlist_patient ON bkg_waitlist_entry (patient_id, status)
    WHERE is_deleted = FALSE;
COMMENT ON TABLE bkg_waitlist_entry IS '号源候补队列：满号时不占 slot，释放后按序号提升';
COMMENT ON COLUMN bkg_waitlist_entry.id IS '主键';
COMMENT ON COLUMN bkg_waitlist_entry.tenant_id IS '租户标识';
COMMENT ON COLUMN bkg_waitlist_entry.organization_id IS '机构标识';
COMMENT ON COLUMN bkg_waitlist_entry.patient_id IS '患者主键';
COMMENT ON COLUMN bkg_waitlist_entry.slot_id IS '候补号源主键';
COMMENT ON COLUMN bkg_waitlist_entry.schedule_id IS '所属日程主键';
COMMENT ON COLUMN bkg_waitlist_entry.queue_position IS '同 slot 下排队序号，从 1 递增';
COMMENT ON COLUMN bkg_waitlist_entry.status IS '状态';
COMMENT ON COLUMN bkg_waitlist_entry.promoted_appointment_id IS '提升为正式预约后关联 appointmentId';
COMMENT ON COLUMN bkg_waitlist_entry.cancel_reason IS '取消原因';
COMMENT ON COLUMN bkg_waitlist_entry.created_at IS '创建时间';
COMMENT ON COLUMN bkg_waitlist_entry.updated_at IS '最后更新时间';
COMMENT ON COLUMN bkg_waitlist_entry.created_by IS '创建人';
COMMENT ON COLUMN bkg_waitlist_entry.updated_by IS '最后更新人';
COMMENT ON COLUMN bkg_waitlist_entry.is_deleted IS '软删标记';
COMMENT ON COLUMN bkg_waitlist_entry.deleted_at IS '删除时间';
COMMENT ON COLUMN bkg_waitlist_entry.deleted_by IS '删除人';
COMMENT ON COLUMN bkg_waitlist_entry.version IS '乐观锁版本号';
