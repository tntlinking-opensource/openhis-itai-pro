-- =============================================================================
-- whale-pro：撤回「发放批改回贵批」——改回实扣便宜批
-- 库：whale-pro @ 172.16.101.2
--
-- 背景：2026-08-20 曾执行 fix-device-dispense-lot-to-bill-lot-pro-80.sql，
--       把 80 行发放批号从便宜批改成账单贵批（使用跟收入表面上对齐）。
-- 现口径：承认使用≠收入；收贵钱、用便宜耗材；发放批号改回实扣便宜批。
--
-- 改：wkf_device_dispense.lot_number / wkf_device_request.lot_number
-- 不改：账单金额、库存数量
--
-- 映射（与事故时一致）：
--   251009（黄管贵）→ 260509（便宜）
--   20260120（针贵）→ 20260312（便宜）
-- 定位：location=采血室 且 updated_at 落在 2026-08-20 那次运维写入（恰 80 行）
-- =============================================================================

-- ---------- A. 预览（须 80 行 / 28.35） ----------
SELECT
    def.lot_no AS bill_lot_stay,
    d.lot_number AS dispense_lot_now,
    CASE def.lot_no
        WHEN '251009' THEN '260509'
        WHEN '20260120' THEN '20260312'
    END AS restore_actual_lot,
    COUNT(*)::int AS cnt,
    ROUND(SUM(ci.total_price), 6) AS sum_bill_price
FROM adm_charge_item ci
JOIN wkf_device_dispense d
  ON d.id = ci.dispense_id
 AND COALESCE(d.is_deleted, FALSE) = FALSE
JOIN adm_charge_item_definition def
  ON def.id = ci.definition_id
WHERE COALESCE(ci.is_deleted, FALSE) = FALSE
  AND ci.dispense_table = 'wkf_device_dispense'
  AND d.status_enum IN ('COMPLETED', 'PART_COMPLETED')
  AND d.location_id = 340426713309646848
  AND def.lot_no IN ('251009', '20260120')
  AND d.lot_number = def.lot_no
  AND d.updated_at >= TIMESTAMPTZ '2026-08-20 08:12:00+00'
  AND d.updated_at <  TIMESTAMPTZ '2026-08-20 08:13:00+00'
GROUP BY def.lot_no, d.lot_number
ORDER BY def.lot_no;

SELECT COUNT(*)::int AS will_restore_cnt
FROM adm_charge_item ci
JOIN wkf_device_dispense d
  ON d.id = ci.dispense_id
 AND COALESCE(d.is_deleted, FALSE) = FALSE
JOIN adm_charge_item_definition def
  ON def.id = ci.definition_id
WHERE COALESCE(ci.is_deleted, FALSE) = FALSE
  AND ci.dispense_table = 'wkf_device_dispense'
  AND d.status_enum IN ('COMPLETED', 'PART_COMPLETED')
  AND d.location_id = 340426713309646848
  AND def.lot_no IN ('251009', '20260120')
  AND d.lot_number = def.lot_no
  AND d.updated_at >= TIMESTAMPTZ '2026-08-20 08:12:00+00'
  AND d.updated_at <  TIMESTAMPTZ '2026-08-20 08:13:00+00';


-- ---------- B. 确认 will_restore_cnt=80 后执行 ----------
BEGIN;

WITH target AS (
    SELECT
        d.id AS dispense_id,
        d.device_req_id,
        CASE def.lot_no
            WHEN '251009' THEN '260509'
            WHEN '20260120' THEN '20260312'
        END AS actual_lot
    FROM adm_charge_item ci
    JOIN wkf_device_dispense d
      ON d.id = ci.dispense_id
     AND COALESCE(d.is_deleted, FALSE) = FALSE
    JOIN adm_charge_item_definition def
      ON def.id = ci.definition_id
    WHERE COALESCE(ci.is_deleted, FALSE) = FALSE
      AND ci.dispense_table = 'wkf_device_dispense'
      AND d.status_enum IN ('COMPLETED', 'PART_COMPLETED')
      AND d.location_id = 340426713309646848
      AND def.lot_no IN ('251009', '20260120')
      AND d.lot_number = def.lot_no
      AND d.updated_at >= TIMESTAMPTZ '2026-08-20 08:12:00+00'
      AND d.updated_at <  TIMESTAMPTZ '2026-08-20 08:13:00+00'
),
upd_d AS (
    UPDATE wkf_device_dispense d
    SET lot_number = t.actual_lot,
        updated_at = NOW()
    FROM target t
    WHERE d.id = t.dispense_id
      AND t.actual_lot IS NOT NULL
    RETURNING d.id
),
upd_r AS (
    UPDATE wkf_device_request r
    SET lot_number = t.actual_lot,
        updated_at = NOW()
    FROM target t
    WHERE r.id = t.device_req_id
      AND t.actual_lot IS NOT NULL
      AND r.lot_number IS DISTINCT FROM t.actual_lot
    RETURNING r.id
)
SELECT
    (SELECT COUNT(*)::int FROM upd_d) AS updated_dispense_cnt,
    (SELECT COUNT(*)::int FROM upd_r) AS updated_request_cnt;

-- 复核：异批应回到 80
SELECT COUNT(*)::int AS lot_mismatch_should_be_80
FROM adm_charge_item ci
JOIN wkf_device_dispense d
  ON d.id = ci.dispense_id
 AND COALESCE(d.is_deleted, FALSE) = FALSE
JOIN adm_charge_item_definition def
  ON def.id = ci.definition_id
WHERE COALESCE(ci.is_deleted, FALSE) = FALSE
  AND ci.dispense_table = 'wkf_device_dispense'
  AND d.status_enum IN ('COMPLETED', 'PART_COMPLETED')
  AND def.lot_no IS DISTINCT FROM d.lot_number;

-- 复核：账单金额仍约 28.35（未改价）
SELECT ROUND(SUM(ci.total_price), 6) AS bill_total_should_be_28_35
FROM adm_charge_item ci
JOIN wkf_device_dispense d
  ON d.id = ci.dispense_id
 AND COALESCE(d.is_deleted, FALSE) = FALSE
JOIN adm_charge_item_definition def
  ON def.id = ci.definition_id
WHERE COALESCE(ci.is_deleted, FALSE) = FALSE
  AND ci.dispense_table = 'wkf_device_dispense'
  AND d.status_enum IN ('COMPLETED', 'PART_COMPLETED')
  AND def.lot_no IN ('251009', '20260120')
  AND d.lot_number IN ('260509', '20260312')
  AND d.location_id = 340426713309646848;

-- updated_dispense_cnt=80 且 lot_mismatch_should_be_80=80
-- 且 bill_total_should_be_28_35≈28.35 →
COMMIT;

-- 否则：
-- ROLLBACK;
