-- =============================================================================
-- whale-pro 只读诊断：门诊已收费、护士站尚未执行的耗材
-- 口径：就诊 AMB；DeviceRequest 已完成且非退单；收费 SETTLED；
--       发放缺失或仍为 DRAFT / PREPARATION / IN_PROGRESS（对齐 BR-NUR-017）
-- 用途：收入报表有数、使用明细没有 → 先核未执行
-- =============================================================================

-- 1) 按开立科室：收入 vs 已执行 vs 未执行
WITH op_enc AS (
  SELECT id, organization_id
    FROM adm_encounter
   WHERE COALESCE(is_deleted, false) = false
     AND class_enum = 'AMB'
)
SELECT
    COALESCE(org.name, '（无科室）') AS dept_name,
    COALESCE(SUM(ci.total_price) FILTER (
        WHERE ci.status_enum = 'SETTLED' AND ci.refund_id IS NULL
    ), 0) AS income_amt,
    COALESCE(SUM(ci.total_price) FILTER (
        WHERE ci.status_enum = 'SETTLED'
          AND d.status_enum IN ('COMPLETED', 'PART_COMPLETED')
    ), 0) AS executed_amt,
    COALESCE(SUM(ci.total_price) FILTER (
        WHERE ci.status_enum = 'SETTLED'
          AND d.status_enum IN ('DRAFT', 'PREPARATION', 'IN_PROGRESS')
    ), 0) AS pending_amt,
    COUNT(*) FILTER (
        WHERE ci.status_enum = 'SETTLED'
          AND d.status_enum IN ('DRAFT', 'PREPARATION', 'IN_PROGRESS')
    ) AS pending_rows
  FROM adm_charge_item ci
  JOIN op_enc e ON e.id = ci.encounter_id
  LEFT JOIN wkf_device_dispense d
    ON d.id = ci.dispense_id AND COALESCE(d.is_deleted, false) = false
  LEFT JOIN wkf_device_request dr
    ON dr.id = COALESCE(
        d.device_req_id,
        CASE WHEN ci.service_table = 'wkf_device_request' THEN ci.service_id END)
   AND COALESCE(dr.is_deleted, false) = false
  LEFT JOIN adm_organization org
    ON org.id = COALESCE(dr.org_id, e.organization_id)
 WHERE COALESCE(ci.is_deleted, false) = false
   AND ci.product_table = 'adm_device_definition'
 GROUP BY COALESCE(org.name, '（无科室）')
 ORDER BY pending_amt DESC, income_amt DESC;

-- 2) 未执行明细（护士站「仅待发耗材」同源）
WITH op_enc AS (
  SELECT e.id, e.bus_no, e.organization_id, e.start_time, e.patient_id
    FROM adm_encounter e
   WHERE COALESCE(e.is_deleted, false) = false
     AND e.class_enum = 'AMB'
),
req AS (
  SELECT dr.*
    FROM wkf_device_request dr
    JOIN op_enc e ON e.id = dr.encounter_id
   WHERE COALESCE(dr.is_deleted, false) = false
     AND dr.status_enum = 'COMPLETED'
     AND dr.refund_device_id IS NULL
)
SELECT
    COALESCE(ord_org.name, enc_org.name, '（无科室）') AS dept_name,
    loc.name AS location_name,
    p.name AS patient_name,
    e.bus_no AS encounter_no,
    dd.name AS device_name,
    dd.bus_no AS device_code,
    d.quantity,
    d.lot_number,
    d.status_enum AS disp_status,
    ci.total_price AS settled_amt,
    ci.entered_date AS charge_time,
    prac.name AS doctor_name
  FROM req r
  JOIN op_enc e ON e.id = r.encounter_id
  JOIN wkf_device_dispense d
    ON d.device_req_id = r.id AND COALESCE(d.is_deleted, false) = false
  JOIN adm_charge_item ci
    ON ci.dispense_id = d.id
   AND ci.dispense_table = 'wkf_device_dispense'
   AND COALESCE(ci.is_deleted, false) = false
   AND ci.status_enum = 'SETTLED'
  LEFT JOIN adm_organization ord_org ON ord_org.id = r.org_id
  LEFT JOIN adm_organization enc_org ON enc_org.id = e.organization_id
  LEFT JOIN adm_location loc ON loc.id = d.location_id
  LEFT JOIN adm_patient p ON p.id = r.patient_id
  LEFT JOIN adm_device_definition dd ON dd.id = r.device_def_id
  LEFT JOIN adm_practitioner prac ON prac.id = r.requester_id
 WHERE d.status_enum IN ('DRAFT', 'PREPARATION', 'IN_PROGRESS')
 ORDER BY loc.name, ord_org.name, ci.entered_date, p.name, dd.name;
