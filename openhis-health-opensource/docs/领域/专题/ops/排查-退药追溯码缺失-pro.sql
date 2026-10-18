-- =============================================================================
-- whale-pro 只读诊断：已 REFUNDED 且行上有码，但发药 OUT 尚未被退药 IN 回冲
-- 口径：按码展开退药行 trace_no，比较 HOSPITAL_IO 发药 OUT 合计 vs MEDICATION_RETURN_IN 合计
-- 排除：原发未扫（无 OUT）——按设计退药不写追溯，不算孤儿
-- =============================================================================

-- 汇总
WITH refund_codes AS (
  SELECT trim(code) AS trace_no
  FROM med_medication_dispense d
  CROSS JOIN LATERAL unnest(
    string_to_array(replace(COALESCE(d.trace_no, ''), ' ', ''), ',')
  ) AS code
  WHERE COALESCE(d.is_deleted, FALSE) = FALSE
    AND d.dispense_enum = 'REFUND'
    AND d.status_enum = 'REFUNDED'
    AND NULLIF(trim(code), '') IS NOT NULL
),
agg AS (
  SELECT
    rc.trace_no,
    COALESCE((
      SELECT SUM(r.split_quantity)
      FROM cus_trace_number_record r
      WHERE r.trace_no = rc.trace_no
        AND COALESCE(r.is_deleted, FALSE) = FALSE
        AND r.record_category_enum = 'HOSPITAL_IO'
        AND r.record_direction_enum = 'OUT'
        AND r.operation_type IN ('MEDICATION_DISPENSE_OUT', 'MEDICATION_EXTRA_DISPENSE_OUT')
    ), 0) AS out_split_sum,
    COALESCE((
      SELECT SUM(r.split_quantity)
      FROM cus_trace_number_record r
      WHERE r.trace_no = rc.trace_no
        AND COALESCE(r.is_deleted, FALSE) = FALSE
        AND r.record_category_enum = 'HOSPITAL_IO'
        AND r.record_direction_enum = 'IN'
        AND r.operation_type = 'MEDICATION_RETURN_IN'
    ), 0) AS return_in_split_sum
  FROM refund_codes rc
)
SELECT
  COUNT(*)::int AS need_return_in_rows,
  COUNT(DISTINCT trace_no)::int AS need_return_in_codes
FROM agg
WHERE out_split_sum > return_in_split_sum;

-- 明细（有则继续补；无则干净）
WITH refund_codes AS (
  SELECT
    d.id AS refund_dispense_id,
    d.bus_no AS refund_bus_no,
    d.dispense_time,
    d.medication_id,
    d.patient_id,
    d.lot_number,
    trim(code) AS trace_no
  FROM med_medication_dispense d
  CROSS JOIN LATERAL unnest(
    string_to_array(replace(COALESCE(d.trace_no, ''), ' ', ''), ',')
  ) AS code
  WHERE COALESCE(d.is_deleted, FALSE) = FALSE
    AND d.dispense_enum = 'REFUND'
    AND d.status_enum = 'REFUNDED'
    AND NULLIF(trim(code), '') IS NOT NULL
)
SELECT
  rc.refund_dispense_id,
  rc.refund_bus_no,
  rc.dispense_time AS refund_time,
  rc.trace_no,
  md.name AS medication_name,
  p.name AS patient_name,
  rc.lot_number,
  m.in_hospital_status_enum,
  m.remaining_split_quantity,
  COALESCE(out_q.s, 0) AS out_split_sum,
  COALESCE(in_q.s, 0) AS return_in_split_sum,
  COALESCE(out_q.s, 0) - COALESCE(in_q.s, 0) AS unmatched_out_split
FROM refund_codes rc
LEFT JOIN cus_trace_number m
  ON m.trace_no = rc.trace_no
 AND COALESCE(m.is_deleted, FALSE) = FALSE
LEFT JOIN med_medication_definition md
  ON md.id = rc.medication_id
 AND COALESCE(md.is_deleted, FALSE) = FALSE
LEFT JOIN adm_patient p
  ON p.id = rc.patient_id
 AND COALESCE(p.is_deleted, FALSE) = FALSE
LEFT JOIN LATERAL (
  SELECT SUM(r.split_quantity) AS s
  FROM cus_trace_number_record r
  WHERE r.trace_no = rc.trace_no
    AND COALESCE(r.is_deleted, FALSE) = FALSE
    AND r.record_category_enum = 'HOSPITAL_IO'
    AND r.record_direction_enum = 'OUT'
    AND r.operation_type IN ('MEDICATION_DISPENSE_OUT', 'MEDICATION_EXTRA_DISPENSE_OUT')
) out_q ON TRUE
LEFT JOIN LATERAL (
  SELECT SUM(r.split_quantity) AS s
  FROM cus_trace_number_record r
  WHERE r.trace_no = rc.trace_no
    AND COALESCE(r.is_deleted, FALSE) = FALSE
    AND r.record_category_enum = 'HOSPITAL_IO'
    AND r.record_direction_enum = 'IN'
    AND r.operation_type = 'MEDICATION_RETURN_IN'
) in_q ON TRUE
WHERE COALESCE(out_q.s, 0) > COALESCE(in_q.s, 0)
ORDER BY rc.dispense_time DESC NULLS LAST, rc.refund_dispense_id DESC;
