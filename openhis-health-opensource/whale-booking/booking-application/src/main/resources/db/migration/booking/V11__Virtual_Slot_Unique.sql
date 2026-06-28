-- =============================================================================
-- V11 · 虚拟号源 lazy 物化唯一（GAP-BKG-004）
-- =============================================================================

CREATE UNIQUE INDEX IF NOT EXISTS uk_wkf_slot_org_time_window
    ON wkf_slot (organization_id, start_time, end_time)
    WHERE is_deleted = false;
