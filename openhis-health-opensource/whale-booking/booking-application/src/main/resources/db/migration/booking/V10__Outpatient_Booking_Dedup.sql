-- =============================================================================
-- V10 · 门诊同日同患者同服务防重（GAP-BKG-001）
-- =============================================================================

ALTER TABLE wkf_appointment_queue
    ADD COLUMN IF NOT EXISTS healthcare_service_id BIGINT,
    ADD COLUMN IF NOT EXISTS booking_date DATE;

UPDATE wkf_appointment_queue a
SET healthcare_service_id = s.healthcare_service_id,
    booking_date = (a.start_instant AT TIME ZONE 'Asia/Shanghai')::date
FROM wkf_slot sl
JOIN wkf_schedule s ON s.id = sl.schedule_id
WHERE a.slot_id = sl.id
  AND a.appointment_kind = 'OUTPATIENT_BOOKING'
  AND a.is_deleted = false
  AND a.healthcare_service_id IS NULL
  AND a.start_instant IS NOT NULL;

WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY patient_id, healthcare_service_id, booking_date
               ORDER BY id ASC
           ) AS rn
    FROM wkf_appointment_queue
    WHERE is_deleted = false
      AND appointment_kind = 'OUTPATIENT_BOOKING'
      AND healthcare_service_id IS NOT NULL
      AND booking_date IS NOT NULL
)
UPDATE wkf_appointment_queue a
SET is_deleted = true,
    cancel_reason = '存量重复预约清洗（保留最早一条）',
    cancellation_at = NOW()
FROM ranked r
WHERE a.id = r.id
  AND r.rn > 1;

CREATE UNIQUE INDEX IF NOT EXISTS uk_wkf_appt_patient_service_day
    ON wkf_appointment_queue (patient_id, healthcare_service_id, booking_date)
    WHERE is_deleted = false
      AND appointment_kind = 'OUTPATIENT_BOOKING'
      AND healthcare_service_id IS NOT NULL
      AND booking_date IS NOT NULL;

COMMENT ON COLUMN wkf_appointment_queue.healthcare_service_id IS '冗余：门诊防重（同 patient+service+booking_date）';
COMMENT ON COLUMN wkf_appointment_queue.booking_date IS '冗余：预约自然日（Asia/Shanghai）';
