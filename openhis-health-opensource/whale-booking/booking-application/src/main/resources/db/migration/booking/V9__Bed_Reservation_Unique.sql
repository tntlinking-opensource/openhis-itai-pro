-- =============================================================================
-- V9 · 床位预留同日同床唯一（GAP-BKG-003 / GAP-ATD-007）
-- =============================================================================

CREATE UNIQUE INDEX IF NOT EXISTS uk_bkg_bed_reservation_ext_bed_date_active
    ON bkg_bed_reservation_ext (location_id, intended_admission_date)
    WHERE is_deleted = false;
