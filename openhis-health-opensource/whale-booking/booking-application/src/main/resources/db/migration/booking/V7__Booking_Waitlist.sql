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
COMMENT ON COLUMN bkg_waitlist_entry.queue_position IS '同 slot 下排队序号，从 1 递增';
COMMENT ON COLUMN bkg_waitlist_entry.status IS 'WAITING / PROMOTED / CANCELLED';
COMMENT ON COLUMN bkg_waitlist_entry.promoted_appointment_id IS '提升为正式预约后关联 appointmentId';
