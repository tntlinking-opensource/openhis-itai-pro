-- Whale Report V3：SQL 报表目录种子（自 report/V4 迁入）
-- 已有 Loader 报表（V2）不在此重复插入

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000044, 0, 'outpatient_registration_summary', '门诊挂号量统计', 'reportSqlLoader', 'sql', 'draft',
       '[{"field":"dept_name","label":"科室","align":"left","width":160},{"field":"registration_count","label":"挂号人次","align":"right","width":100},{"field":"first_visit_count","label":"初诊人次","align":"right","width":100},{"field":"return_visit_count","label":"复诊人次","align":"right","width":100}]', TRUE, FALSE, NULL,
       '-- 门诊挂号量统计（Whale adm_encounter · 院点可改科室关联）
SELECT COALESCE(loc.name, ''未分配科室'') AS dept_name,
       COUNT(*) AS registration_count,
       SUM(CASE WHEN e.first_enum = ''FIRST'' THEN 1 ELSE 0 END) AS first_visit_count,
       SUM(CASE WHEN COALESCE(e.first_enum, '''') <> ''FIRST'' THEN 1 ELSE 0 END) AS return_visit_count
FROM adm_encounter e
LEFT JOIN adm_location loc ON loc.id = e.organization_id AND loc.is_deleted = FALSE
WHERE e.is_deleted = FALSE
  AND e.class_enum = ''AMB''
  AND e.status_enum NOT IN (''PLANNED'', ''CANCELLED'', ''ENTERED_IN_ERROR'')
  AND (:dateFrom IS NULL OR e.start_time >= CAST(:dateFrom AS timestamp))
  AND (:dateTo IS NULL OR e.start_time < CAST(:dateTo AS timestamp) + INTERVAL ''1 day'')AND (COALESCE(:deptId, '''') = '''' OR CAST(:deptId AS bigint) = e.organization_id)
GROUP BY loc.name
ORDER BY registration_count DESC', '{"title":"门诊挂号量统计","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §2 outpatient_registration_summary · P2 · Whale SQL 预制',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'outpatient_registration_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000045, 0, 'outpatient_revenue_by_category', '门诊收入分类汇总', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"门诊收入分类汇总","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §2 outpatient_revenue_by_category',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'outpatient_revenue_by_category' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000046, 0, 'outpatient_refund_detail', '门诊退费明细', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"门诊退费明细","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §2 outpatient_refund_detail',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'outpatient_refund_detail' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000047, 0, 'outpatient_arrears_list', '门诊欠费/挂账清单', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"门诊欠费/挂账清单","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §2 outpatient_arrears_list',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'outpatient_arrears_list' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000048, 0, 'outpatient_appointment_arrival_rate', '门诊预约到诊率', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"门诊预约到诊率","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §2 outpatient_appointment_arrival_rate',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'outpatient_appointment_arrival_rate' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000049, 0, 'inpatient_in_hospital_list', '在院患者清单', 'reportSqlLoader', 'sql', 'draft',
       '[{"field":"inpatient_no","label":"住院号","align":"left","width":120},{"field":"patient_name","label":"患者姓名","align":"left","width":100},{"field":"ward_name","label":"病区","align":"left","width":120},{"field":"bed_name","label":"床号","align":"center","width":80},{"field":"admit_time","label":"入院时间","align":"center","width":150},{"field":"deposit_balance","label":"预交金余额","align":"right","width":110}]', TRUE, FALSE, NULL,
       '-- 在院患者清单
SELECT e.bus_no AS inpatient_no,
       p.name AS patient_name,
       COALESCE(ward.name, ''-'' ) AS ward_name,
       COALESCE(bed.name, ''-'' ) AS bed_name,
       TO_CHAR(e.start_time, ''YYYY-MM-DD HH24:MI'') AS admit_time,
       COALESCE(acct.balance, 0)::text AS deposit_balance
FROM adm_encounter e
JOIN adm_patient p ON p.id = e.patient_id AND p.is_deleted = FALSE
LEFT JOIN adm_encounter_location el ON el.encounter_id = e.id AND el.is_deleted = FALSE AND el.end_time IS NULL
LEFT JOIN adm_location ward ON ward.id = el.location_id AND ward.is_deleted = FALSE AND ward.form_enum = ''WARD''
LEFT JOIN adm_encounter_location bel ON bel.encounter_id = e.id AND bel.is_deleted = FALSE AND bel.end_time IS NULL AND bel.form_enum = ''BED''
LEFT JOIN adm_location bed ON bed.id = bel.location_id AND bed.is_deleted = FALSE
LEFT JOIN adm_account acct ON acct.encounter_id = e.id AND acct.is_deleted = FALSE AND acct.type_code = ''DEPOSIT''
WHERE e.is_deleted = FALSE
  AND e.class_enum = ''IMP''
  AND e.status_enum IN (''IN_PROGRESS'', ''ON_HOLD'', ''PRE_ADMISSION'', ''PENDING'')AND (COALESCE(:wardId, '''') = '''' OR CAST(:wardId AS bigint) = el.location_id)AND (COALESCE(:keyword, '''') = '''' OR p.name ILIKE ''%'' || :keyword || ''%'' OR p.bus_no ILIKE ''%'' || :keyword || ''%'' OR e.bus_no ILIKE ''%'' || :keyword || ''%'')
ORDER BY ward.name NULLS LAST, bed.name NULLS LAST', '{"title":"在院患者清单","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §3 inpatient_in_hospital_list · P2 · Whale SQL 预制',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'inpatient_in_hospital_list' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000050, 0, 'inpatient_discharge_summary', '出院患者统计', 'reportSqlLoader', 'sql', 'draft',
       '[{"field":"dept_name","label":"科室","align":"left","width":140},{"field":"discharge_count","label":"出院人次","align":"right","width":90},{"field":"avg_stay_days","label":"平均住院日","align":"right","width":100}]', TRUE, FALSE, NULL,
       '-- 出院患者统计
SELECT COALESCE(loc.name, ''-'' ) AS dept_name,
       COUNT(*)::text AS discharge_count,
       CASE WHEN COUNT(*) = 0 THEN ''0''
            ELSE ROUND(AVG(EXTRACT(EPOCH FROM (e.end_time - e.start_time)) / 86400.0), 1)::text
       END AS avg_stay_days
FROM adm_encounter e
LEFT JOIN adm_location loc ON loc.id = e.organization_id AND loc.is_deleted = FALSE
WHERE e.is_deleted = FALSE
  AND e.class_enum = ''IMP''
  AND e.status_enum IN (''DISCHARGED'', ''COMPLETED'')
  AND e.end_time IS NOT NULL
  AND (:dateFrom IS NULL OR e.end_time >= CAST(:dateFrom AS timestamp))
  AND (:dateTo IS NULL OR e.end_time < CAST(:dateTo AS timestamp) + INTERVAL ''1 day'')AND (COALESCE(:deptId, '''') = '''' OR CAST(:deptId AS bigint) = e.organization_id)
GROUP BY loc.name
ORDER BY COUNT(*) DESC', '{"title":"出院患者统计","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §3 inpatient_discharge_summary · P2 · Whale SQL 预制',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'inpatient_discharge_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000051, 0, 'inpatient_deposit_balance', '预交金余额汇总', 'reportSqlLoader', 'sql', 'draft',
       '[{"field":"ward_name","label":"病区","align":"left","width":120},{"field":"inpatient_no","label":"住院号","align":"left","width":110},{"field":"patient_name","label":"患者","align":"left","width":90},{"field":"balance","label":"预交金余额","align":"right","width":110},{"field":"billing_status","label":"结算状态","align":"center","width":90}]', TRUE, FALSE, NULL,
       '-- 预交金余额汇总
SELECT COALESCE(loc.name, ''-'' ) AS ward_name,
       e.bus_no AS inpatient_no,
       p.name AS patient_name,
       COALESCE(a.balance, 0)::text AS balance,
       COALESCE(a.billing_status_enum, ''-'' ) AS billing_status
FROM adm_account a
JOIN adm_encounter e ON e.id = a.encounter_id AND e.is_deleted = FALSE
JOIN adm_patient p ON p.id = e.patient_id AND p.is_deleted = FALSE
LEFT JOIN adm_encounter_location el ON el.encounter_id = e.id AND el.is_deleted = FALSE AND el.end_time IS NULL AND el.form_enum = ''WARD''
LEFT JOIN adm_location loc ON loc.id = el.location_id AND loc.is_deleted = FALSE
WHERE a.is_deleted = FALSE
  AND a.type_code = ''DEPOSIT''
  AND e.class_enum = ''IMP''
  AND e.status_enum IN (''IN_PROGRESS'', ''ON_HOLD'', ''PENDING'')AND (COALESCE(:wardId, '''') = '''' OR CAST(:wardId AS bigint) = el.location_id)AND (COALESCE(:keyword, '''') = '''' OR p.name ILIKE ''%'' || :keyword || ''%'' OR p.bus_no ILIKE ''%'' || :keyword || ''%'' OR e.bus_no ILIKE ''%'' || :keyword || ''%'')
ORDER BY loc.name NULLS LAST, a.balance ASC', '{"title":"预交金余额汇总","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §3 inpatient_deposit_balance · P2 · Whale SQL 预制',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'inpatient_deposit_balance' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000052, 0, 'inpatient_charge_detail_query', '住院费用明细查询', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"住院费用明细查询","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §3 inpatient_charge_detail_query',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'inpatient_charge_detail_query' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000053, 0, 'inpatient_bed_occupancy_rate', '床位使用率统计', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"床位使用率统计","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §3 inpatient_bed_occupancy_rate',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'inpatient_bed_occupancy_rate' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000054, 0, 'inpatient_insurance_settlement_summary', '住院医保结算汇总', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"住院医保结算汇总","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §3 inpatient_insurance_settlement_summary',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'inpatient_insurance_settlement_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000055, 0, 'physician_workload_summary', '医生工作量统计', 'reportSqlLoader', 'sql', 'draft',
       '[{"field":"physician_code","label":"医生工号","align":"left","width":100},{"field":"physician_name","label":"医生姓名","align":"left","width":100},{"field":"dept_name","label":"科室","align":"left","width":140},{"field":"encounter_count","label":"接诊人次","align":"right","width":90},{"field":"charge_item_count","label":"收费项目数","align":"right","width":100},{"field":"revenue_amount","label":"开单金额","align":"right","width":110}]', TRUE, FALSE, NULL,
       '-- 医生工作量统计
SELECT pr.bus_no AS physician_code,
       pr.name AS physician_name,
       COALESCE(loc.name, ''-'' ) AS dept_name,
       COUNT(DISTINCT e.id)::text AS encounter_count,
       COUNT(ci.id)::text AS charge_item_count,
       COALESCE(SUM(ci.total_price), 0)::text AS revenue_amount
FROM adm_encounter e
JOIN adm_encounter_participant ep ON ep.encounter_id = e.id AND ep.is_deleted = FALSE AND ep.role_code = ''ATND''
JOIN adm_practitioner pr ON pr.id = ep.practitioner_id AND pr.is_deleted = FALSE
LEFT JOIN adm_location loc ON loc.id = e.organization_id AND loc.is_deleted = FALSE
LEFT JOIN adm_charge_item ci ON ci.encounter_id = e.id AND ci.is_deleted = FALSE
WHERE e.is_deleted = FALSE
  AND e.status_enum NOT IN (''CANCELLED'', ''ENTERED_IN_ERROR'')
  AND (:dateFrom IS NULL OR e.start_time >= CAST(:dateFrom AS timestamp))
  AND (:dateTo IS NULL OR e.start_time < CAST(:dateTo AS timestamp) + INTERVAL ''1 day'')AND (COALESCE(:deptId, '''') = '''' OR CAST(:deptId AS bigint) = e.organization_id)
  AND (COALESCE(:keyword, '''') = '''' OR pr.name ILIKE ''%'' || :keyword || ''%'' OR pr.bus_no ILIKE ''%'' || :keyword || ''%'')
GROUP BY pr.bus_no, pr.name, loc.name
ORDER BY CAST(COALESCE(SUM(ci.total_price), 0) AS numeric) DESC', '{"title":"医生工作量统计","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §4 physician_workload_summary · P2 · Whale SQL 预制',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'physician_workload_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000056, 0, 'charge_item_revenue_ranking', '收费项目收入排名', 'reportSqlLoader', 'sql', 'draft',
       '[{"field":"rank_no","label":"排名","align":"center","width":60},{"field":"item_name","label":"收费项目","align":"left","width":200},{"field":"total_amount","label":"金额","align":"right","width":120},{"field":"item_count","label":"人次/笔数","align":"right","width":100},{"field":"amount_share","label":"占比(%)","align":"right","width":90}]', TRUE, FALSE, NULL,
       '-- 收费项目收入 TOP（默认前 50）
WITH item_rev AS (
  SELECT COALESCE(def.name, ci.bus_no, ''未命名项目'') AS item_name,
         SUM(ci.total_price) AS total_amount,
         COUNT(*) AS item_count
  FROM adm_charge_item ci
  LEFT JOIN adm_charge_item_definition def ON def.id = ci.definition_id AND def.is_deleted = FALSE
  WHERE ci.is_deleted = FALSE
  AND (:dateFrom IS NULL OR ci.occurrence_time >= CAST(:dateFrom AS timestamp))
  AND (:dateTo IS NULL OR ci.occurrence_time < CAST(:dateTo AS timestamp) + INTERVAL ''1 day'')
  GROUP BY def.name, ci.bus_no
),
ranked AS (
  SELECT *, SUM(total_amount) OVER () AS grand_total
  FROM item_rev
)
SELECT ROW_NUMBER() OVER (ORDER BY total_amount DESC)::text AS rank_no,
       item_name,
       total_amount::text,
       item_count::text,
       CASE WHEN grand_total = 0 THEN ''0''
            ELSE ROUND(100.0 * total_amount / grand_total, 2)::text
       END AS amount_share
FROM ranked
ORDER BY total_amount DESC
LIMIT 50', '{"title":"收费项目收入排名","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §4 charge_item_revenue_ranking · P2 · Whale SQL 预制',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'charge_item_revenue_ranking' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000057, 0, 'payment_method_summary', '支付方式汇总', 'reportSqlLoader', 'sql', 'draft',
       '[{"field":"payment_method","label":"支付方式","align":"left","width":140},{"field":"payment_count","label":"笔数","align":"right","width":80},{"field":"net_amount","label":"净额","align":"right","width":120},{"field":"amount_share","label":"占比(%)","align":"right","width":90}]', TRUE, FALSE, NULL,
       '-- 支付方式汇总
WITH pay AS (
  SELECT p.payment_enum AS payment_method,
         COUNT(*) AS payment_count,
         SUM(p.tendered_amount - COALESCE(p.returned_amount, 0)) AS net_amount
  FROM fin_payment_reconciliation p
  WHERE p.is_deleted = FALSE
    AND p.status_enum = ''SUCCESS''
  AND (:dateFrom IS NULL OR p.bill_date >= CAST(:dateFrom AS timestamp))
  AND (:dateTo IS NULL OR p.bill_date < CAST(:dateTo AS timestamp) + INTERVAL ''1 day'')
  GROUP BY p.payment_enum
),
total AS (
  SELECT COALESCE(SUM(net_amount), 0) AS grand_total FROM pay
)
SELECT pay.payment_method,
       pay.payment_count::text,
       pay.net_amount::text AS net_amount,
       CASE WHEN t.grand_total = 0 THEN ''0''
            ELSE ROUND(100.0 * pay.net_amount / t.grand_total, 2)::text
       END AS amount_share
FROM pay
CROSS JOIN total t
ORDER BY pay.net_amount DESC', '{"title":"支付方式汇总","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §4 payment_method_summary · P2 · Whale SQL 预制',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'payment_method_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000058, 0, 'dept_drug_ratio_report', '科室药占比统计', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"科室药占比统计","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"month","label":"月份","type":"month"},{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"}]', '08 §4 dept_drug_ratio_report',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'dept_drug_ratio_report' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000059, 0, 'arrears_patient_summary', '欠费患者汇总', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"欠费患者汇总","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §4 arrears_patient_summary',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'arrears_patient_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000060, 0, 'daily_revenue_trend', '日收入趋势（按日）', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"日收入趋势（按日）","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"month","label":"月份","type":"month"},{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"}]', '08 §4 daily_revenue_trend',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'daily_revenue_trend' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000061, 0, 'inventory_near_expiry', '近效期药品', 'reportSqlLoader', 'sql', 'draft',
       '[{"field":"item_name","label":"药品名称","align":"left","width":180},{"field":"lot_number","label":"批号","align":"left","width":100},{"field":"quantity","label":"库存数量","align":"right","width":90},{"field":"unit_code","label":"单位","align":"center","width":60},{"field":"expiration_date","label":"效期","align":"center","width":110},{"field":"days_to_expire","label":"距效期(天)","align":"right","width":90},{"field":"warehouse_name","label":"库房","align":"left","width":120}]', TRUE, FALSE, NULL,
       '-- 近效期药品（默认 90 天内；可在设计器加 :days 参数）
SELECT inv.name AS item_name,
       inv.lot_number,
       inv.quantity::text AS quantity,
       inv.unit_code,
       TO_CHAR(inv.expiration_date, ''YYYY-MM-DD'') AS expiration_date,
       GREATEST(0, (inv.expiration_date::date - CURRENT_DATE))::text AS days_to_expire,
       COALESCE(loc.name, ''-'' ) AS warehouse_name
FROM wkf_inventory_item inv
LEFT JOIN adm_location loc ON loc.id = inv.location_id AND loc.is_deleted = FALSE
WHERE inv.is_deleted = FALSE
  AND inv.inventory_status_enum = ''ACTIVE''
  AND inv.quantity > 0
  AND inv.expiration_date IS NOT NULL
  AND inv.expiration_date <= CURRENT_DATE + INTERVAL ''90 days''AND (COALESCE(:locationId, '''') = '''' OR CAST(:locationId AS bigint) = inv.location_id)
  AND (COALESCE(:keyword, '''') = '''' OR inv.name ILIKE ''%'' || :keyword || ''%'')
ORDER BY inv.expiration_date ASC, inv.name', '{"title":"近效期药品","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"locationId","label":"库房","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §5.3 inventory_near_expiry · P2 · Whale SQL 预制',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'inventory_near_expiry' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000062, 0, 'inventory_low_stock_alert', '低库存预警', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"低库存预警","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"locationId","label":"库房","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §5.3 inventory_low_stock_alert',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'inventory_low_stock_alert' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000063, 0, 'medication_consumption_ranking', '药品消耗排名', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"药品消耗排名","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"locationId","label":"库房","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §5.3 medication_consumption_ranking',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'medication_consumption_ranking' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000064, 0, 'antibiotic_usage_ddd', '抗菌药物使用强度', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"抗菌药物使用强度","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"month","label":"月份","type":"month"},{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"}]', '08 §5.3 antibiotic_usage_ddd',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'antibiotic_usage_ddd' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000065, 0, 'controlled_drug_register', '麻精药品专册', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"麻精药品专册","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §5.3 controlled_drug_register',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'controlled_drug_register' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000066, 0, 'purchase_inbound_summary', '采购入库汇总', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"采购入库汇总","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §5.3 purchase_inbound_summary',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'purchase_inbound_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000067, 0, 'ward_requisition_summary', '病区领药汇总', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"病区领药汇总","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §5.3 ward_requisition_summary',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'ward_requisition_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000068, 0, 'nurse_workload_summary', '护士工作量统计', 'reportSqlLoader', 'sql', 'draft',
       '[{"field":"nurse_code","label":"护士工号","align":"left","width":100},{"field":"nurse_name","label":"护士姓名","align":"left","width":100},{"field":"ward_name","label":"病区","align":"left","width":120},{"field":"execute_total","label":"执行总次数","align":"right","width":100},{"field":"iv_infusion_count","label":"输液","align":"right","width":70},{"field":"injection_count","label":"注射","align":"right","width":70},{"field":"oral_drug_count","label":"口服","align":"right","width":70},{"field":"specimen_count","label":"采血/标本","align":"right","width":90}]', TRUE, FALSE, NULL,
       '-- 护士工作量统计（med_medication_request 执行人维度）
SELECT pr.bus_no AS nurse_code,
       pr.name AS nurse_name,
       COALESCE(loc.name, ''-'' ) AS ward_name,
       COUNT(*)::text AS execute_total,
       SUM(CASE WHEN m.infusion_flag = TRUE OR m.method_code IN (''IV'', ''IVD'') THEN 1 ELSE 0 END)::text AS iv_infusion_count,
       SUM(CASE WHEN m.method_code IN (''IM'', ''SC'', ''IH'') THEN 1 ELSE 0 END)::text AS injection_count,
       SUM(CASE WHEN m.method_code = ''PO'' THEN 1 ELSE 0 END)::text AS oral_drug_count,
       SUM(CASE WHEN m.category_enum = ''LAB'' THEN 1 ELSE 0 END)::text AS specimen_count
FROM med_medication_request m
JOIN adm_practitioner pr ON pr.id = m.performer_id AND pr.is_deleted = FALSE
JOIN adm_encounter e ON e.id = m.encounter_id AND e.is_deleted = FALSE
LEFT JOIN adm_encounter_location el ON el.encounter_id = e.id AND el.is_deleted = FALSE AND el.end_time IS NULL AND el.form_enum = ''WARD''
LEFT JOIN adm_location loc ON loc.id = el.location_id AND loc.is_deleted = FALSE
WHERE m.is_deleted = FALSE
  AND m.performer_id IS NOT NULL
  AND m.status_enum = ''COMPLETED''
  AND (:dateFrom IS NULL OR m.status_changed_time >= CAST(:dateFrom AS timestamp))
  AND (:dateTo IS NULL OR m.status_changed_time < CAST(:dateTo AS timestamp) + INTERVAL ''1 day'')AND (COALESCE(:wardId, '''') = '''' OR CAST(:wardId AS bigint) = el.location_id)
  AND (COALESCE(:keyword, '''') = '''' OR pr.name ILIKE ''%'' || :keyword || ''%'' OR pr.bus_no ILIKE ''%'' || :keyword || ''%'')
GROUP BY pr.bus_no, pr.name, loc.name
ORDER BY COUNT(*) DESC', '{"title":"护士工作量统计","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §6.1 RPT-NRS-01 · P2 · Whale SQL 预制',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'nurse_workload_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000069, 0, 'nurse_workload_detail', '护士工作量明细', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"护士工作量明细","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §6.1 RPT-NRS-02',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'nurse_workload_detail' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000070, 0, 'nurse_execute_by_category', '护士执行分类统计', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"护士执行分类统计","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §6.1 RPT-NRS-03',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'nurse_execute_by_category' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000071, 0, 'nursing_ward_workload_summary', '护理工作量统计（病区）', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"护理工作量统计（病区）","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §6.1 RPT-NRS-04',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'nursing_ward_workload_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000072, 0, 'nurse_verify_workload_summary', '护士核对工作量', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"护士核对工作量","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §6.1 RPT-NRS-05',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'nurse_verify_workload_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000073, 0, 'outpatient_nurse_workload_summary', '门急诊护士工作量', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"门急诊护士工作量","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §6.1 RPT-NRS-06',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'outpatient_nurse_workload_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000074, 0, 'nursing_order_execute_query', '医嘱执行记录查询', 'reportSqlLoader', 'sql', 'draft',
       '[{"field":"execute_time","label":"执行时间","align":"center","width":150},{"field":"patient_name","label":"患者","align":"left","width":90},{"field":"inpatient_no","label":"住院号","align":"left","width":110},{"field":"order_name","label":"医嘱内容","align":"left","width":200},{"field":"method_code","label":"用法","align":"center","width":70},{"field":"nurse_name","label":"执行人","align":"left","width":90},{"field":"ward_name","label":"病区","align":"left","width":110}]', TRUE, FALSE, NULL,
       '-- 医嘱执行记录查询
SELECT TO_CHAR(m.status_changed_time, ''YYYY-MM-DD HH24:MI'') AS execute_time,
       p.name AS patient_name,
       e.bus_no AS inpatient_no,
       COALESCE(med.name, m.bus_no) AS order_name,
       COALESCE(m.method_code, ''-'' ) AS method_code,
       pr.name AS nurse_name,
       COALESCE(loc.name, ''-'' ) AS ward_name
FROM med_medication_request m
JOIN adm_encounter e ON e.id = m.encounter_id AND e.is_deleted = FALSE
JOIN adm_patient p ON p.id = e.patient_id AND p.is_deleted = FALSE
JOIN adm_practitioner pr ON pr.id = m.performer_id AND pr.is_deleted = FALSE
LEFT JOIN med_medication_definition med ON med.id = m.medication_id AND med.is_deleted = FALSE
LEFT JOIN adm_encounter_location el ON el.encounter_id = e.id AND el.is_deleted = FALSE AND el.end_time IS NULL AND el.form_enum = ''WARD''
LEFT JOIN adm_location loc ON loc.id = el.location_id AND loc.is_deleted = FALSE
WHERE m.is_deleted = FALSE
  AND m.status_enum = ''COMPLETED''
  AND m.performer_id IS NOT NULL
  AND (:dateFrom IS NULL OR m.status_changed_time >= CAST(:dateFrom AS timestamp))
  AND (:dateTo IS NULL OR m.status_changed_time < CAST(:dateTo AS timestamp) + INTERVAL ''1 day'')AND (COALESCE(:wardId, '''') = '''' OR CAST(:wardId AS bigint) = el.location_id)AND (COALESCE(:keyword, '''') = '''' OR p.name ILIKE ''%'' || :keyword || ''%'' OR p.bus_no ILIKE ''%'' || :keyword || ''%'' OR e.bus_no ILIKE ''%'' || :keyword || ''%'')
ORDER BY m.status_changed_time DESC', '{"title":"医嘱执行记录查询","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §6.2 RPT-NRS-10 · P2 · Whale SQL 预制',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'nursing_order_execute_query' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000075, 0, 'nursing_order_pending_list', '未执行医嘱清单', 'reportSqlLoader', 'sql', 'draft',
       '[{"field":"patient_name","label":"患者","align":"left","width":90},{"field":"inpatient_no","label":"住院号","align":"left","width":110},{"field":"bed_name","label":"床号","align":"center","width":70},{"field":"order_name","label":"医嘱内容","align":"left","width":200},{"field":"plan_time","label":"计划执行","align":"center","width":150},{"field":"status","label":"状态","align":"center","width":80},{"field":"ward_name","label":"病区","align":"left","width":110}]', TRUE, FALSE, NULL,
       '-- 未执行医嘱清单（已校对/已发送未执行）
SELECT p.name AS patient_name,
       e.bus_no AS inpatient_no,
       COALESCE(bed.name, ''-'' ) AS bed_name,
       COALESCE(med.name, m.bus_no) AS order_name,
       TO_CHAR(m.effective_dose_start, ''YYYY-MM-DD HH24:MI'') AS plan_time,
       m.status_enum AS status,
       COALESCE(loc.name, ''-'' ) AS ward_name
FROM med_medication_request m
JOIN adm_encounter e ON e.id = m.encounter_id AND e.is_deleted = FALSE
JOIN adm_patient p ON p.id = e.patient_id AND p.is_deleted = FALSE
LEFT JOIN med_medication_definition med ON med.id = m.medication_id AND med.is_deleted = FALSE
LEFT JOIN adm_encounter_location el ON el.encounter_id = e.id AND el.is_deleted = FALSE AND el.end_time IS NULL AND el.form_enum = ''WARD''
LEFT JOIN adm_location loc ON loc.id = el.location_id AND loc.is_deleted = FALSE
LEFT JOIN adm_encounter_location bel ON bel.encounter_id = e.id AND bel.is_deleted = FALSE AND bel.end_time IS NULL AND bel.form_enum = ''BED''
LEFT JOIN adm_location bed ON bed.id = bel.location_id AND bed.is_deleted = FALSE
WHERE m.is_deleted = FALSE
  AND m.status_enum IN (''ACTIVE'', ''VERIFIED'')
  AND e.class_enum = ''IMP''
  AND e.status_enum IN (''IN_PROGRESS'', ''ON_HOLD'', ''PENDING'')
  AND (:dateFrom IS NULL OR m.effective_dose_start >= CAST(:dateFrom AS timestamp))
  AND (:dateTo IS NULL OR m.effective_dose_start < CAST(:dateTo AS timestamp) + INTERVAL ''1 day'')AND (COALESCE(:wardId, '''') = '''' OR CAST(:wardId AS bigint) = el.location_id)AND (COALESCE(:keyword, '''') = '''' OR p.name ILIKE ''%'' || :keyword || ''%'' OR p.bus_no ILIKE ''%'' || :keyword || ''%'' OR e.bus_no ILIKE ''%'' || :keyword || ''%'')
ORDER BY m.effective_dose_start NULLS LAST, loc.name, bed.name', '{"title":"未执行医嘱清单","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §6.2 RPT-NRS-11 · P2 · Whale SQL 预制',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'nursing_order_pending_list' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000076, 0, 'iv_infusion_execute_summary', '输液执行统计', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"输液执行统计","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §6.2 RPT-NRS-12',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'iv_infusion_execute_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000077, 0, 'specimen_collect_summary', '采血执行统计', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"采血执行统计","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §6.2 RPT-NRS-13',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'specimen_collect_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000078, 0, 'skin_test_result_summary', '皮试结果统计', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"皮试结果统计","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §6.2 RPT-NRS-14',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'skin_test_result_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000079, 0, 'ward_dispense_receive_summary', '统领摆药/领药汇总', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"统领摆药/领药汇总","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §6.2 RPT-NRS-15',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'ward_dispense_receive_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000080, 0, 'temperature_chart_entry_summary', '体温单录入统计', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"体温单录入统计","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §6.3 RPT-NRS-20',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'temperature_chart_entry_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000081, 0, 'nursing_record_completion_rate', '护理记录完成率', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"护理记录完成率","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §6.3 RPT-NRS-21',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'nursing_record_completion_rate' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000082, 0, 'nursing_care_level_distribution', '护理等级分布', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"护理等级分布","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §6.3 RPT-NRS-22',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'nursing_care_level_distribution' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000083, 0, 'nursing_adverse_event_report', '压疮/跌倒不良事件', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"压疮/跌倒不良事件","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"month","label":"月份","type":"month"},{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"}]', '08 §6.3 RPT-NRS-23',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'nursing_adverse_event_report' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000084, 0, 'nurse_shift_handover_query', '交接班记录查询', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"交接班记录查询","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §6.3 RPT-NRS-24',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'nurse_shift_handover_query' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000085, 0, 'discharge_nursing_days_summary', '出院患者护理日数', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"出院患者护理日数","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §6.3 RPT-NRS-25',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'discharge_nursing_days_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000086, 0, 'outpatient_prescription_summary', '门诊处方统计', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"门诊处方统计","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §7 outpatient_prescription_summary',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'outpatient_prescription_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000087, 0, 'inpatient_order_detail', '住院医嘱明细', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"住院医嘱明细","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §7 inpatient_order_detail',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'inpatient_order_detail' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000088, 0, 'critical_value_report', '危急值处理统计', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"危急值处理统计","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §7 critical_value_report',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'critical_value_report' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000089, 0, 'surgery_case_summary', '手术台次统计', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"手术台次统计","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §7 surgery_case_summary',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'surgery_case_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000090, 0, 'blood_transfusion_detail', '输血记录明细', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"输血记录明细","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §7 blood_transfusion_detail',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'blood_transfusion_detail' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000091, 0, 'antibiotic_prescription_review', '抗菌药物处方点评', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"抗菌药物处方点评","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §7 antibiotic_prescription_review',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'antibiotic_prescription_review' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000092, 0, 'clinical_pathway_enrollment', '临床路径入径统计', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"临床路径入径统计","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"month","label":"月份","type":"month"},{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"}]', '08 §7 clinical_pathway_enrollment',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'clinical_pathway_enrollment' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000093, 0, 'lab_apply_summary', '检验申请统计', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"检验申请统计","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §8 lab_apply_summary',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'lab_apply_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000094, 0, 'exam_appointment_arrival', '检查预约到检统计', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"检查预约到检统计","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §8 exam_appointment_arrival',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'exam_appointment_arrival' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000095, 0, 'lab_positive_rate', '检验阳性率统计', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"检验阳性率统计","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §8 lab_positive_rate',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'lab_positive_rate' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000096, 0, 'exam_device_workload', '检查工作量（设备）', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"检查工作量（设备）","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §8 exam_device_workload',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'exam_device_workload' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000097, 0, 'lab_tat_report', 'TAT 周转时间', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"TAT 周转时间","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §8 lab_tat_report',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'lab_tat_report' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000098, 0, 'health_check_appointment_summary', '体检预约汇总', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"体检预约汇总","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §9 health_check_appointment_summary',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'health_check_appointment_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000099, 0, 'health_check_group_progress', '团检到检进度', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"团检到检进度","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §9 health_check_group_progress',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'health_check_group_progress' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000100, 0, 'emergency_triage_volume', '急诊分诊量统计', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"急诊分诊量统计","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §9 emergency_triage_volume',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'emergency_triage_volume' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000101, 0, 'emergency_ed_los', '急诊抢救滞留时间', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"急诊抢救滞留时间","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §9 emergency_ed_los',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'emergency_ed_los' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000102, 0, 'hospital_daily_revenue', '全院收入日报', 'reportSqlLoader', 'sql', 'draft',
       '[{"field":"stat_date","label":"日期","align":"center","width":110},{"field":"outpatient_revenue","label":"门诊收入","align":"right","width":120},{"field":"inpatient_revenue","label":"住院收入","align":"right","width":120},{"field":"total_revenue","label":"合计收入","align":"right","width":120},{"field":"payment_count","label":"支付笔数","align":"right","width":90}]', TRUE, FALSE, NULL,
       '-- 全院收入日报
SELECT TO_CHAR(p.bill_date, ''YYYY-MM-DD'') AS stat_date,
       COALESCE(SUM(CASE WHEN e.class_enum = ''AMB'' THEN p.tendered_amount - COALESCE(p.returned_amount, 0) ELSE 0 END), 0)::text AS outpatient_revenue,
       COALESCE(SUM(CASE WHEN e.class_enum = ''IMP'' THEN p.tendered_amount - COALESCE(p.returned_amount, 0) ELSE 0 END), 0)::text AS inpatient_revenue,
       COALESCE(SUM(p.tendered_amount - COALESCE(p.returned_amount, 0)), 0)::text AS total_revenue,
       COUNT(*)::text AS payment_count
FROM fin_payment_reconciliation p
JOIN adm_encounter e ON e.id = p.encounter_id AND e.is_deleted = FALSE
WHERE p.is_deleted = FALSE
  AND p.status_enum = ''SUCCESS''
  AND (:dateFrom IS NULL OR p.bill_date >= CAST(:dateFrom AS timestamp))
  AND (:dateTo IS NULL OR p.bill_date < CAST(:dateTo AS timestamp) + INTERVAL ''1 day'')
GROUP BY TO_CHAR(p.bill_date, ''YYYY-MM-DD'')
ORDER BY stat_date', '{"title":"全院收入日报","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.1 hospital_daily_revenue · P2 · Whale SQL 预制',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'hospital_daily_revenue' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000103, 0, 'hospital_monthly_revenue', '全院收入月报', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"全院收入月报","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"month","label":"月份","type":"month"},{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"}]', '08 §10.1 hospital_monthly_revenue',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'hospital_monthly_revenue' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000104, 0, 'dept_revenue_ranking', '科室收入排名', 'reportSqlLoader', 'sql', 'draft',
       '[{"field":"rank_no","label":"排名","align":"center","width":60},{"field":"dept_name","label":"科室","align":"left","width":160},{"field":"revenue_amount","label":"收入金额","align":"right","width":120},{"field":"payment_count","label":"支付笔数","align":"right","width":90},{"field":"revenue_share","label":"占比(%)","align":"right","width":90}]', TRUE, FALSE, NULL,
       '-- 科室收入排名
WITH dept_rev AS (
  SELECT COALESCE(loc.name, ''未分配'') AS dept_name,
         SUM(p.tendered_amount - COALESCE(p.returned_amount, 0)) AS revenue_amount,
         COUNT(*) AS payment_count
  FROM fin_payment_reconciliation p
  JOIN adm_encounter e ON e.id = p.encounter_id AND e.is_deleted = FALSE
  LEFT JOIN adm_location loc ON loc.id = e.organization_id AND loc.is_deleted = FALSE
  WHERE p.is_deleted = FALSE
    AND p.status_enum = ''SUCCESS''
  AND (:dateFrom IS NULL OR p.bill_date >= CAST(:dateFrom AS timestamp))
  AND (:dateTo IS NULL OR p.bill_date < CAST(:dateTo AS timestamp) + INTERVAL ''1 day'')AND (COALESCE(:deptId, '''') = '''' OR CAST(:deptId AS bigint) = e.organization_id)
  GROUP BY loc.name
),
total AS (
  SELECT COALESCE(SUM(revenue_amount), 0) AS grand_total FROM dept_rev
)
SELECT ROW_NUMBER() OVER (ORDER BY dr.revenue_amount DESC)::text AS rank_no,
       dr.dept_name,
       dr.revenue_amount::text AS revenue_amount,
       dr.payment_count::text AS payment_count,
       CASE WHEN t.grand_total = 0 THEN ''0''
            ELSE ROUND(100.0 * dr.revenue_amount / t.grand_total, 2)::text
       END AS revenue_share
FROM dept_rev dr
CROSS JOIN total t
ORDER BY dr.revenue_amount DESC', '{"title":"科室收入排名","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.1 dept_revenue_ranking · P2 · Whale SQL 预制',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'dept_revenue_ranking' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000105, 0, 'outpatient_avg_cost_per_visit', '门诊次均费用', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"门诊次均费用","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.1 outpatient_avg_cost_per_visit',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'outpatient_avg_cost_per_visit' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000106, 0, 'inpatient_avg_cost_per_case', '住院次均费用', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"住院次均费用","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.1 inpatient_avg_cost_per_case',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'inpatient_avg_cost_per_case' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000107, 0, 'revenue_structure_analysis', '收入结构分析', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"收入结构分析","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.1 revenue_structure_analysis',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'revenue_structure_analysis' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000108, 0, 'dept_performance_export', '科室绩效数据包', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"科室绩效数据包","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"month","label":"月份","type":"month"},{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"}]', '08 §10.1 dept_performance_export',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'dept_performance_export' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000109, 0, 'campus_comparison_report', '院区对比报表', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"院区对比报表","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"month","label":"月份","type":"month"},{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"}]', '08 §10.1 campus_comparison_report',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'campus_comparison_report' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000110, 0, 'outpatient_visit_volume_summary', '门诊人次统计', 'reportSqlLoader', 'sql', 'draft',
       '[{"field":"dept_name","label":"科室","align":"left","width":160},{"field":"visit_count","label":"门诊人次","align":"right","width":100},{"field":"first_visit_count","label":"初诊","align":"right","width":80},{"field":"return_visit_count","label":"复诊","align":"right","width":80}]', TRUE, FALSE, NULL,
       '-- 门诊人次统计
SELECT COALESCE(loc.name, ''未分配科室'') AS dept_name,
       COUNT(*)::text AS visit_count,
       SUM(CASE WHEN e.first_enum = ''FIRST'' THEN 1 ELSE 0 END)::text AS first_visit_count,
       SUM(CASE WHEN COALESCE(e.first_enum, '''') <> ''FIRST'' THEN 1 ELSE 0 END)::text AS return_visit_count
FROM adm_encounter e
LEFT JOIN adm_location loc ON loc.id = e.organization_id AND loc.is_deleted = FALSE
WHERE e.is_deleted = FALSE
  AND e.class_enum = ''AMB''
  AND e.status_enum NOT IN (''PLANNED'', ''CANCELLED'', ''ENTERED_IN_ERROR'')
  AND (:dateFrom IS NULL OR e.start_time >= CAST(:dateFrom AS timestamp))
  AND (:dateTo IS NULL OR e.start_time < CAST(:dateTo AS timestamp) + INTERVAL ''1 day'')AND (COALESCE(:deptId, '''') = '''' OR CAST(:deptId AS bigint) = e.organization_id)
GROUP BY loc.name
ORDER BY COUNT(*) DESC', '{"title":"门诊人次统计","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.2 outpatient_visit_volume_summary · P2 · Whale SQL 预制',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'outpatient_visit_volume_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000111, 0, 'registration_volume_operational', '挂号量统计（运营）', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"挂号量统计（运营）","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.2 registration_volume_operational',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'registration_volume_operational' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000112, 0, 'inpatient_admission_discharge_summary', '住院出入院统计', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"住院出入院统计","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.2 inpatient_admission_discharge_summary',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'inpatient_admission_discharge_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000113, 0, 'surgery_volume_operational', '手术量统计（运营）', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"手术量统计（运营）","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.2 surgery_volume_operational',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'surgery_volume_operational' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000114, 0, 'lab_exam_volume_hospital', '检验检查工作量（全院）', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"检验检查工作量（全院）","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.2 lab_exam_volume_hospital',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'lab_exam_volume_hospital' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000115, 0, 'health_check_volume_summary', '体检业务量统计', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"体检业务量统计","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.2 health_check_volume_summary',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'health_check_volume_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000116, 0, 'emergency_volume_operational', '急诊业务量统计', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"急诊业务量统计","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.2 emergency_volume_operational',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'emergency_volume_operational' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000117, 0, 'appointment_conversion_analysis', '预约转化分析', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"预约转化分析","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.2 appointment_conversion_analysis',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'appointment_conversion_analysis' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000118, 0, 'bed_occupancy_operational_report', '床位使用率', 'reportSqlLoader', 'sql', 'draft',
       '[{"field":"ward_name","label":"病区","align":"left","width":140},{"field":"total_beds","label":"开放床位","align":"right","width":90},{"field":"occupied_beds","label":"占用床位","align":"right","width":90},{"field":"occupancy_rate","label":"使用率(%)","align":"right","width":100}]', TRUE, FALSE, NULL,
       '-- 床位使用率（运营）
WITH ward_beds AS (
  SELECT bed.parent_id AS ward_id,
         COUNT(*) AS total_beds
  FROM adm_location bed
  WHERE bed.is_deleted = FALSE
    AND bed.form_enum = ''BED''
    AND bed.status_enum = ''ACTIVE''
  GROUP BY bed.parent_id
),
occupied AS (
  SELECT bed.parent_id AS ward_id,
         COUNT(DISTINCT el.encounter_id) AS occupied_beds
  FROM adm_encounter_location el
  JOIN adm_location bed ON bed.id = el.location_id AND bed.is_deleted = FALSE
  JOIN adm_encounter e ON e.id = el.encounter_id AND e.is_deleted = FALSE
  WHERE el.is_deleted = FALSE
    AND el.end_time IS NULL
    AND el.form_enum = ''BED''
    AND e.class_enum = ''IMP''
    AND e.status_enum IN (''IN_PROGRESS'', ''ON_HOLD'', ''PENDING'')
  GROUP BY bed.parent_id
)
SELECT COALESCE(w.name, ''-'' ) AS ward_name,
       COALESCE(wb.total_beds, 0)::text AS total_beds,
       COALESCE(oc.occupied_beds, 0)::text AS occupied_beds,
       CASE WHEN COALESCE(wb.total_beds, 0) = 0 THEN ''0''
            ELSE ROUND(100.0 * COALESCE(oc.occupied_beds, 0) / wb.total_beds, 1)::text
       END AS occupancy_rate
FROM adm_location w
LEFT JOIN ward_beds wb ON wb.ward_id = w.id
LEFT JOIN occupied oc ON oc.ward_id = w.id
WHERE w.is_deleted = FALSE
  AND w.form_enum = ''WARD''
  AND (COALESCE(:wardId, '''') = '''' OR CAST(:wardId AS bigint) = w.id)
ORDER BY w.name', '{"title":"床位使用率","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.3 bed_occupancy_operational_report · P2 · Whale SQL 预制',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'bed_occupancy_operational_report' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000119, 0, 'average_length_of_stay_report', '平均住院日', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"平均住院日","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.3 average_length_of_stay_report',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'average_length_of_stay_report' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000120, 0, 'bed_turnover_rate_report', '床位周转次数', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"床位周转次数","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.3 bed_turnover_rate_report',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'bed_turnover_rate_report' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000121, 0, 'inpatient_census_daily', '在院人数日报', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"在院人数日报","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.3 inpatient_census_daily',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'inpatient_census_daily' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000122, 0, 'prolonged_stay_patient_list', '超长住院患者清单', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"超长住院患者清单","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.3 prolonged_stay_patient_list',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'prolonged_stay_patient_list' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000123, 0, 'pending_discharge_settlement_summary', '预出院/待结算统计', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"预出院/待结算统计","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.3 pending_discharge_settlement_summary',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'pending_discharge_settlement_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000124, 0, 'drug_ratio_operational_report', '药占比报表', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"药占比报表","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.4 drug_ratio_operational_report',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'drug_ratio_operational_report' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000125, 0, 'material_ratio_operational_report', '耗占比报表', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"耗占比报表","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.4 material_ratio_operational_report',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'material_ratio_operational_report' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000126, 0, 'drug_material_ratio_report', '药占比耗占比合并', 'reportSqlLoader', 'sql', 'draft',
       '[{"field":"dept_name","label":"科室","align":"left","width":160},{"field":"total_revenue","label":"医疗收入","align":"right","width":120},{"field":"drug_revenue","label":"药品收入","align":"right","width":120},{"field":"material_revenue","label":"耗材收入","align":"right","width":120},{"field":"drug_ratio","label":"药占比(%)","align":"right","width":90},{"field":"material_ratio","label":"耗占比(%)","align":"right","width":90}]', TRUE, FALSE, NULL,
       '-- 药占比 / 耗占比（按 charge_item 大类粗分，院点可接术语表细化）
SELECT COALESCE(loc.name, ''-'' ) AS dept_name,
       COALESCE(SUM(ci.total_price), 0)::text AS total_revenue,
       COALESCE(SUM(CASE WHEN ci.product_table = ''med_medication_definition'' THEN ci.total_price ELSE 0 END), 0)::text AS drug_revenue,
       COALESCE(SUM(CASE WHEN ci.product_table = ''adm_device_definition'' THEN ci.total_price ELSE 0 END), 0)::text AS material_revenue,
       CASE WHEN COALESCE(SUM(ci.total_price), 0) = 0 THEN ''0''
            ELSE ROUND(100.0 * SUM(CASE WHEN ci.product_table = ''med_medication_definition'' THEN ci.total_price ELSE 0 END) / SUM(ci.total_price), 2)::text
       END AS drug_ratio,
       CASE WHEN COALESCE(SUM(ci.total_price), 0) = 0 THEN ''0''
            ELSE ROUND(100.0 * SUM(CASE WHEN ci.product_table = ''adm_device_definition'' THEN ci.total_price ELSE 0 END) / SUM(ci.total_price), 2)::text
       END AS material_ratio
FROM adm_charge_item ci
JOIN adm_encounter e ON e.id = ci.encounter_id AND e.is_deleted = FALSE
LEFT JOIN adm_location loc ON loc.id = e.organization_id AND loc.is_deleted = FALSE
WHERE ci.is_deleted = FALSE
  AND (:dateFrom IS NULL OR ci.occurrence_time >= CAST(:dateFrom AS timestamp))
  AND (:dateTo IS NULL OR ci.occurrence_time < CAST(:dateTo AS timestamp) + INTERVAL ''1 day'')AND (COALESCE(:deptId, '''') = '''' OR CAST(:deptId AS bigint) = e.organization_id)
GROUP BY loc.name
ORDER BY SUM(ci.total_price) DESC NULLS LAST', '{"title":"药占比耗占比合并","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.4 drug_material_ratio_report · P2 · Whale SQL 预制',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'drug_material_ratio_report' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000127, 0, 'insurance_fund_payment_summary', '医保基金支付统计', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"医保基金支付统计","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.4 insurance_fund_payment_summary',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'insurance_fund_payment_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000128, 0, 'drg_data_export', 'DRG 分组结果导出', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"DRG 分组结果导出","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"month","label":"月份","type":"month"},{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"}]', '08 §10.4 drg_data_export',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'drg_data_export' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000129, 0, 'drg_profit_loss_analysis', 'DRG 盈亏分析', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"DRG 盈亏分析","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"month","label":"月份","type":"month"},{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"}]', '08 §10.4 drg_profit_loss_analysis',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'drg_profit_loss_analysis' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000130, 0, 'high_value_consumable_usage', '高值耗材使用统计', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"高值耗材使用统计","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.4 high_value_consumable_usage',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'high_value_consumable_usage' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000131, 0, 'insurance_rejection_detail', '医保拒付/违规明细', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"医保拒付/违规明细","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.4 insurance_rejection_detail',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'insurance_rejection_detail' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000132, 0, 'mr_homepage_quality', '病案首页质量统计', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"病案首页质量统计","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"month","label":"月份","type":"month"},{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"}]', '08 §10.5 mr_homepage_quality',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'mr_homepage_quality' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000133, 0, 'readmission_rate_report', '再入院率统计', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"再入院率统计","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"month","label":"月份","type":"month"},{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"}]', '08 §10.5 readmission_rate_report',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'readmission_rate_report' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000134, 0, 'antibiotic_ddd_hospital', '抗菌药物使用强度（全院）', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"抗菌药物使用强度（全院）","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"month","label":"月份","type":"month"},{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"}]', '08 §10.5 antibiotic_ddd_hospital',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'antibiotic_ddd_hospital' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000135, 0, 'clinical_pathway_coverage_rate', '临床路径覆盖率', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"临床路径覆盖率","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"month","label":"月份","type":"month"},{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"}]', '08 §10.5 clinical_pathway_coverage_rate',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'clinical_pathway_coverage_rate' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000136, 0, 'critical_value_closure_rate', '危急值闭环率', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"危急值闭环率","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.5 critical_value_closure_rate',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'critical_value_closure_rate' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000137, 0, 'surgery_complication_summary', '手术并发症统计', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"手术并发症统计","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"month","label":"月份","type":"month"},{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"}]', '08 §10.5 surgery_complication_summary',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'surgery_complication_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000138, 0, 'adverse_event_report_summary', '不良事件上报统计', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"不良事件上报统计","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"month","label":"月份","type":"month"},{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"}]', '08 §10.5 adverse_event_report_summary',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'adverse_event_report_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000139, 0, 'slot_utilization_report', '号源利用率', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"号源利用率","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.6 slot_utilization_report',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'slot_utilization_report' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000140, 0, 'exam_device_utilization', '设备检查利用率', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"设备检查利用率","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.6 exam_device_utilization',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'exam_device_utilization' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000141, 0, 'print_event_daily_summary', '打印日志汇总', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"打印日志汇总","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.6 print_event_daily_summary',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'print_event_daily_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000142, 0, 'audit_report_access', '报表访问审计', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"报表访问审计","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.6 audit_report_access',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'audit_report_access' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000143, 0, 'system_active_user_summary', '系统活跃用户统计', 'reportSqlLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md
SELECT NULL::text AS placeholder WHERE 1=0', '{"title":"系统活跃用户统计","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"统计区间：{{dateRange}}"},{"type":"text","text":"说明：院点可在设计器中完善 SQL 后发布至报表中心","align":"left"}]}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.6 system_active_user_summary',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'system_active_user_summary' AND d.is_deleted = FALSE
);

-- Whale Report V4：发布后处理（自 report/V5 迁入，仅 rpt_definition）
-- Whale Report V4：种子后处理（表头 lines 规范化、默认租户复制、状态兜底）

-- 表头 JSON 升级为 lines[] 动态行（兼容旧 leftText/rightText）
UPDATE rpt_definition
SET header_json = jsonb_build_object(
        'title', COALESCE(header_json::jsonb ->> 'title', name),
        'subtitle', COALESCE(header_json::jsonb ->> 'subtitle', '{{dateRange}}'),
        'lines', CASE
            WHEN header_json IS NOT NULL
                 AND jsonb_array_length(COALESCE(header_json::jsonb -> 'lines', '[]'::jsonb)) > 0
                THEN header_json::jsonb -> 'lines'
            WHEN COALESCE(header_json::jsonb ->> 'leftText', '') <> ''
                 OR COALESCE(header_json::jsonb ->> 'rightText', '') <> ''
                THEN jsonb_build_array(jsonb_build_object(
                    'type', 'split',
                    'left', COALESCE(header_json::jsonb ->> 'leftText', ''),
                    'right', COALESCE(header_json::jsonb ->> 'rightText', '')
                ))
            ELSE jsonb_build_array(jsonb_build_object(
                    'type', 'split',
                    'left', '编制单位：________',
                    'right', '打印日期：{{dateRange}}'
                ))
            END
    )::text
WHERE is_deleted = FALSE
  AND source_type = 'sql'
  AND header_json IS NOT NULL;

-- 将平台出厂报表定义（tenant_id=0）复制到默认院点租户（whale_tenants.id=1）
INSERT INTO rpt_definition (
    id,
    tenant_id,
    report_code,
    name,
    loader_bean,
    source_type,
    status,
    default_columns_json,
    allow_column_customize,
    export_pdf_enabled,
    pdf_print_code,
    query_sql,
    header_json,
    footer_json,
    filter_json,
    remark,
    created_at,
    updated_at,
    created_by,
    updated_by,
    is_deleted,
    deleted_at,
    deleted_by
)
SELECT
    src.id + 800000000000,
    1,
    src.report_code,
    src.name,
    src.loader_bean,
    src.source_type,
    src.status,
    src.default_columns_json,
    src.allow_column_customize,
    src.export_pdf_enabled,
    src.pdf_print_code,
    src.query_sql,
    src.header_json,
    src.footer_json,
    src.filter_json,
    src.remark,
    CURRENT_TIMESTAMP,
    src.updated_at,
    src.created_by,
    src.updated_by,
    FALSE,
    NULL,
    NULL
FROM rpt_definition src
WHERE src.tenant_id = 0
  AND src.is_deleted = FALSE
  AND NOT EXISTS (
      SELECT 1
      FROM rpt_definition d
      WHERE d.tenant_id = 1
        AND d.report_code = src.report_code
        AND d.is_deleted = FALSE
  );

-- Loader 类定义 status/source_type 兜底

-- V4/V5 merged outpatient_daily_settlement presentation

-- NOTE: outpatient_daily_settlement / inpatient_daily_settlement rpt_definition seeds include final header_json and tenant_id=1.
