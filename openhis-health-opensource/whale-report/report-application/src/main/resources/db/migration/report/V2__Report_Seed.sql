-- Whale Report V2：出厂种子数据（由原 V2～V32 合并，清库后一次性执行）
-- history: flyway_schema_history_report


-- ========== V2__Seed_print_elements.sql ==========

-- V2：打印元素目录 seed（tenant_id=0 平台出厂，各租户 preview 可回退读取）
-- pack: outpatient_prescription（flow P1 必达元素 + 部分 overlay 元素）、charge_receipt（P1 骨架）

-- ========== outpatient_prescription ==========

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
) VALUES
(910001000001, 0, 'outpatient_prescription', 'docTitle', '标题', 'staticTitle', NULL,
 NULL, NULL, '门诊处方笺', 'flow', 10, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE),

(910001000002, 0, 'outpatient_prescription', 'hospitalName', '医疗机构', 'text', 'hospital.name',
 NULL, NULL, NULL, 'flow', 20, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE),

(910001000003, 0, 'outpatient_prescription', 'prescriptionMetaRow', '处方头', 'kvRow', 'prescription',
 '[{"label":"处方号","field":"no"},{"label":"日期","field":"authoredDate"},{"label":"科别","field":"deptName"}]',
 NULL, NULL, 'flow', 30, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE),

(910001000004, 0, 'outpatient_prescription', 'patientInfoRow', '患者信息', 'kvRow', 'patient',
 '[{"label":"姓名","field":"name"},{"label":"性别","field":"gender"},{"label":"年龄","field":"age"},{"label":"病历号","field":"busNo"}]',
 NULL, NULL, 'flow', 40, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE),

(910001000005, 0, 'outpatient_prescription', 'diagnosisText', '临床诊断', 'text', 'prescription.diagnosis',
 NULL, NULL, '临床诊断：', 'flow', 50, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE),

(910001000006, 0, 'outpatient_prescription', 'prescriptionDrugTable', '药品明细', 'table', 'prescription.lines',
 NULL,
 '[{"field":"lineNo","header":"序号"},{"field":"drugName","header":"药品名称"},{"field":"spec","header":"规格"},{"field":"quantity","header":"数量"},{"field":"unitText","header":"单位"},{"field":"usageText","header":"用法用量"}]',
 NULL, 'flow', 60, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE),

(910001000007, 0, 'outpatient_prescription', 'prescriptionFooterRow', '脚注签名', 'kvRow', 'footer',
 '[{"label":"医师","field":"prescriberName"},{"label":"审核","field":"reviewerName"},{"label":"金额合计","field":"totalAmount"}]',
 NULL, NULL, 'flow', 70, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE),

(910001000008, 0, 'outpatient_prescription', 'dividerLine', '分隔线', 'line', NULL,
 NULL, NULL, NULL, 'flow', 80, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE),

-- overlay 常用字段（P1 低，box 示意）
(910001000101, 0, 'outpatient_prescription', 'rxNo', '处方号', 'text', 'prescription.no',
 NULL, NULL, NULL, 'overlay', 101, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE),
(910001000102, 0, 'outpatient_prescription', 'patientName', '姓名', 'text', 'patient.name',
 NULL, NULL, NULL, 'overlay', 102, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE),
(910001000103, 0, 'outpatient_prescription', 'patientGender', '性别', 'text', 'patient.gender',
 NULL, NULL, NULL, 'overlay', 103, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE),
(910001000104, 0, 'outpatient_prescription', 'patientAge', '年龄', 'text', 'patient.age',
 NULL, NULL, NULL, 'overlay', 104, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE),
(910001000105, 0, 'outpatient_prescription', 'prescriberName', '医师', 'text', 'practitioner.prescriberName',
 NULL, NULL, NULL, 'overlay', 105, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE);

-- ========== charge_receipt（热敏小票骨架） ==========

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
) VALUES
(910002000001, 0, 'charge_receipt', 'storeName', '机构名', 'text', 'hospital.name',
 NULL, NULL, NULL, 'flow', 10, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE),
(910002000002, 0, 'charge_receipt', 'chargeMetaText', '结算信息', 'kvRow', 'charge',
 '[{"label":"单号","field":"busNo"},{"label":"时间","field":"occurrenceTime"}]',
 NULL, NULL, 'flow', 20, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE),
(910002000003, 0, 'charge_receipt', 'chargeLineTable', '收费明细', 'table', 'charge.lines',
 NULL,
 '[{"field":"itemName","header":"项目"},{"field":"quantity","header":"数量"},{"field":"unitPrice","header":"单价"},{"field":"lineAmount","header":"金额"}]',
 NULL, 'flow', 30, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE),
(910002000004, 0, 'charge_receipt', 'totalAmount', '合计', 'text', 'charge.totalAmount',
 NULL, NULL, '合计：', 'flow', 40, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE),
(910002000005, 0, 'charge_receipt', 'dividerLine', '分隔线', 'line', NULL,
 NULL, NULL, NULL, 'flow', 50, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE);

-- ========== V3__Seed_templates.sql ==========

-- V3：出厂 published 模板（门诊处方 flow P1 必达）

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
) VALUES (
    920001000001,
    0,
    'outpatient_prescription_flow',
    '门诊处方笺（白纸 flow）',
    'page',
    '{
  "pipeline": "page",
  "page": { "size": "A4", "orientation": "portrait", "marginMm": [12, 10, 12, 10] },
  "items": [
    { "elementCode": "docTitle", "visible": true, "labelOverride": "门诊处方笺", "style": { "fontSizePt": 18, "align": "center", "bold": true } },
    { "elementCode": "hospitalName", "visible": true, "style": { "align": "center", "fontSizePt": 11 } },
    { "elementCode": "prescriptionMetaRow", "visible": true },
    { "elementCode": "patientInfoRow", "visible": true },
    { "elementCode": "diagnosisText", "visible": true, "labelOverride": "临床诊断：" },
    { "elementCode": "prescriptionDrugTable", "visible": true },
    { "elementCode": "prescriptionFooterRow", "visible": true },
    { "elementCode": "dividerLine", "visible": true }
  ]
}',
    'outpatient_prescription',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
);

-- ========== V4__Seed_overlay.sql ==========

-- V4：套打 overlay — 元素默认 box_json + 出厂模板 outpatient_prescription_overlay

UPDATE rpt_print_element_def SET box_json = '{"xMm":72,"yMm":12,"widthMm":35,"heightMm":5,"overflow":"clip"}'
WHERE tenant_id = 0 AND pack_code = 'outpatient_prescription' AND element_code = 'rxNo' AND is_deleted = FALSE;

UPDATE rpt_print_element_def SET box_json = '{"xMm":22,"yMm":38,"widthMm":28,"heightMm":5,"overflow":"clip"}'
WHERE tenant_id = 0 AND pack_code = 'outpatient_prescription' AND element_code = 'patientName' AND is_deleted = FALSE;

UPDATE rpt_print_element_def SET box_json = '{"xMm":52,"yMm":38,"widthMm":10,"heightMm":5,"overflow":"clip"}'
WHERE tenant_id = 0 AND pack_code = 'outpatient_prescription' AND element_code = 'patientGender' AND is_deleted = FALSE;

UPDATE rpt_print_element_def SET box_json = '{"xMm":64,"yMm":38,"widthMm":12,"heightMm":5,"overflow":"clip"}'
WHERE tenant_id = 0 AND pack_code = 'outpatient_prescription' AND element_code = 'patientAge' AND is_deleted = FALSE;

UPDATE rpt_print_element_def SET box_json = '{"xMm":22,"yMm":48,"widthMm":130,"heightMm":8,"overflow":"shrinkFont"}'
WHERE tenant_id = 0 AND pack_code = 'outpatient_prescription' AND element_code = 'diagnosisText' AND is_deleted = FALSE;

UPDATE rpt_print_element_def SET box_json = '{"xMm":22,"yMm":62,"widthMm":130,"heightMm":80,"overflow":"clip","rowHeightMm":5}'
WHERE tenant_id = 0 AND pack_code = 'outpatient_prescription' AND element_code = 'prescriptionDrugTable' AND is_deleted = FALSE;

UPDATE rpt_print_element_def SET box_json = '{"xMm":100,"yMm":175,"widthMm":40,"heightMm":5,"overflow":"clip"}'
WHERE tenant_id = 0 AND pack_code = 'outpatient_prescription' AND element_code = 'prescriberName' AND is_deleted = FALSE;

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
) VALUES (
    920001000002,
    0,
    'outpatient_prescription_overlay',
    '门诊处方笺（套打 overlay）',
    'page',
    '{
  "pipeline": "page",
  "page": {
    "size": "A5",
    "orientation": "portrait",
    "layoutMode": "overlay",
    "marginMm": [0, 0, 0, 0],
    "overlay": {
      "printOffsetMm": [0, 0]
    }
  },
  "items": [
    { "elementCode": "rxNo", "visible": true, "box": { "xMm": 72, "yMm": 12, "widthMm": 35, "heightMm": 5 } },
    { "elementCode": "patientName", "visible": true, "box": { "xMm": 22, "yMm": 38, "widthMm": 28, "heightMm": 5 }, "boxDelta": { "dxMm": 0, "dyMm": 0 } },
    { "elementCode": "patientGender", "visible": true },
    { "elementCode": "patientAge", "visible": true },
    { "elementCode": "diagnosisText", "visible": true },
    { "elementCode": "prescriptionDrugTable", "visible": true },
    { "elementCode": "prescriberName", "visible": true }
  ]
}',
    'outpatient_prescription',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
);

-- ========== V5__Seed_print_catalog_stubs.sql ==========

-- V5：打印元素包骨架（02 打印清单 catalog 试打用）

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 920002000001, 0, 'print_page_stub', 'docTitle', '单据标题', 'staticTitle', NULL,
 NULL, NULL, '打印单证', 'flow', 10, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM rpt_print_element_def e WHERE e.tenant_id=0 AND e.pack_code='print_page_stub' AND e.element_code='docTitle' AND e.is_deleted=FALSE);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 920002000002, 0, 'print_page_stub', 'stubHint', '说明行', 'text', 'hospital.name',
 NULL, NULL, '（出厂骨架，待完善）', 'flow', 20, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM rpt_print_element_def e WHERE e.tenant_id=0 AND e.pack_code='print_page_stub' AND e.element_code='stubHint' AND e.is_deleted=FALSE);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 920002000011, 0, 'print_roll_stub', 'rollTitle', '卷纸标题', 'staticTitle', NULL,
 NULL, NULL, '热敏凭条', 'flow', 10, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM rpt_print_element_def e WHERE e.tenant_id=0 AND e.pack_code='print_roll_stub' AND e.element_code='rollTitle' AND e.is_deleted=FALSE);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 920002000012, 0, 'print_roll_stub', 'rollLine1', '内容行', 'text', 'hospital.name',
 NULL, NULL, NULL, 'flow', 20, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM rpt_print_element_def e WHERE e.tenant_id=0 AND e.pack_code='print_roll_stub' AND e.element_code='rollLine1' AND e.is_deleted=FALSE);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted, box_json
)
SELECT 920002000021, 0, 'print_page_overlay_stub', 'overlayField', '套打字段', 'text', 'patient.name',
 NULL, NULL, NULL, 'overlay', 10, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE,
 '{"xMm":20,"yMm":20,"widthMm":60,"heightMm":6,"overflow":"clip"}'
WHERE NOT EXISTS (SELECT 1 FROM rpt_print_element_def e WHERE e.tenant_id=0 AND e.pack_code='print_page_overlay_stub' AND e.element_code='overlayField' AND e.is_deleted=FALSE);

-- ========== V6__Seed_print_template_catalog.sql ==========

-- V6：02-打印清单 全院 B 类 / C-PDF Layout 模板 catalog（platform published v1）
-- 来源: docs/02-打印清单.md + catalog/print-template-catalog.json

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000001,
    0,
    'registration_receipt',
    '挂号小票',
    'roll',
    '{"pipeline":"roll","page":{"widthMm":80,"layoutMode":"flow"},"items":[{"elementCode":"rollTitle","visible":true,"labelOverride":"挂号小票"}]}',
    'print_roll_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'registration_receipt' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000002,
    0,
    'charge_receipt',
    '门诊收费凭条',
    'roll',
    '{"pipeline":"roll","page":{"widthMm":80,"layoutMode":"flow"},"items":[{"elementCode":"storeName","visible":true},{"elementCode":"chargeMetaText","visible":true},{"elementCode":"chargeLineTable","visible":true},{"elementCode":"totalAmount","visible":true},{"elementCode":"dividerLine","visible":true}]}',
    'charge_receipt',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'charge_receipt' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000003,
    0,
    'outpatient_invoice_overlay',
    '门诊发票/票据用户联',
    'page',
    '{"pipeline":"page","page":{"size":"A5","orientation":"portrait","layoutMode":"overlay","marginMm":[0,0,0,0],"overlay":{}},"items":[{"elementCode":"overlayField","visible":true,"box":{"xMm":20,"yMm":20,"widthMm":60,"heightMm":6}}]}',
    'print_page_overlay_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'outpatient_invoice_overlay' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000004,
    0,
    'outpatient_charge_list',
    '门诊费用清单',
    'page',
    '{"pipeline":"page","page":{"size":"A4","orientation":"portrait","marginMm":[12,10,12,10],"layoutMode":"flow"},"items":[{"elementCode":"docTitle","visible":true,"style":{"fontSizePt":14,"align":"center","bold":true},"labelOverride":"门诊费用清单"}]}',
    'print_page_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'outpatient_charge_list' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000005,
    0,
    'outpatient_daily_settlement_pdf',
    '门诊日结报表（PDF）',
    'page',
    '{"pipeline":"page","page":{"size":"A4","orientation":"portrait","marginMm":[12,10,12,10],"layoutMode":"flow"},"items":[{"elementCode":"docTitle","visible":true,"style":{"fontSizePt":14,"align":"center","bold":true},"labelOverride":"门诊日结报表（PDF）"}]}',
    'print_page_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'outpatient_daily_settlement_pdf' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000006,
    0,
    'outpatient_refund_receipt',
    '门诊退费凭条',
    'roll',
    '{"pipeline":"roll","page":{"widthMm":80,"layoutMode":"flow"},"items":[{"elementCode":"rollTitle","visible":true,"labelOverride":"门诊退费凭条"}]}',
    'print_roll_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'outpatient_refund_receipt' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000007,
    0,
    'appointment_queue_ticket',
    '预约/候诊凭条',
    'roll',
    '{"pipeline":"roll","page":{"widthMm":80,"layoutMode":"flow"},"items":[{"elementCode":"rollTitle","visible":true,"labelOverride":"预约/候诊凭条"}]}',
    'print_roll_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'appointment_queue_ticket' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000008,
    0,
    'outpatient_prescription_tcm_flow',
    '中药处方笺（flow）',
    'page',
    '{"pipeline":"page","page":{"size":"A4","orientation":"portrait","marginMm":[12,10,12,10],"layoutMode":"flow"},"items":[{"elementCode":"docTitle","visible":true,"style":{"fontSizePt":18,"align":"center","bold":true},"labelOverride":"中药处方笺"},{"elementCode":"hospitalName","visible":true,"style":{"align":"center","fontSizePt":11}},{"elementCode":"prescriptionMetaRow","visible":true},{"elementCode":"patientInfoRow","visible":true},{"elementCode":"diagnosisText","visible":true,"labelOverride":"临床诊断："},{"elementCode":"prescriptionDrugTable","visible":true},{"elementCode":"prescriptionFooterRow","visible":true}]}',
    'outpatient_prescription',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'outpatient_prescription_tcm_flow' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000009,
    0,
    'outpatient_prescription_tcm_overlay',
    '中药处方笺（套打）',
    'page',
    '{"pipeline":"page","page":{"size":"A5","orientation":"portrait","layoutMode":"overlay","marginMm":[0,0,0,0],"overlay":{}},"items":[{"elementCode":"rxNo","visible":true,"box":{"xMm":72,"yMm":12,"widthMm":35,"heightMm":5}},{"elementCode":"patientName","visible":true},{"elementCode":"patientGender","visible":true},{"elementCode":"patientAge","visible":true},{"elementCode":"diagnosisText","visible":true},{"elementCode":"prescriptionDrugTable","visible":true},{"elementCode":"prescriberName","visible":true}]}',
    'outpatient_prescription',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'outpatient_prescription_tcm_overlay' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000010,
    0,
    'outpatient_prescription_narcotic_flow',
    '麻精一处方（flow）',
    'page',
    '{"pipeline":"page","page":{"size":"A4","orientation":"portrait","marginMm":[12,10,12,10],"layoutMode":"flow"},"items":[{"elementCode":"docTitle","visible":true,"style":{"fontSizePt":18,"align":"center","bold":true},"labelOverride":"麻精一处方"},{"elementCode":"hospitalName","visible":true,"style":{"align":"center","fontSizePt":11}},{"elementCode":"prescriptionMetaRow","visible":true},{"elementCode":"patientInfoRow","visible":true},{"elementCode":"diagnosisText","visible":true,"labelOverride":"临床诊断："},{"elementCode":"prescriptionDrugTable","visible":true},{"elementCode":"prescriptionFooterRow","visible":true}]}',
    'outpatient_prescription',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'outpatient_prescription_narcotic_flow' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000011,
    0,
    'outpatient_prescription_narcotic_overlay',
    '麻精一处方（套打）',
    'page',
    '{"pipeline":"page","page":{"size":"A5","orientation":"portrait","layoutMode":"overlay","marginMm":[0,0,0,0],"overlay":{}},"items":[{"elementCode":"rxNo","visible":true,"box":{"xMm":72,"yMm":12,"widthMm":35,"heightMm":5}},{"elementCode":"patientName","visible":true},{"elementCode":"patientGender","visible":true},{"elementCode":"patientAge","visible":true},{"elementCode":"diagnosisText","visible":true},{"elementCode":"prescriptionDrugTable","visible":true},{"elementCode":"prescriberName","visible":true}]}',
    'outpatient_prescription',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'outpatient_prescription_narcotic_overlay' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000012,
    0,
    'lab_apply_form',
    '检验申请单',
    'page',
    '{"pipeline":"page","page":{"size":"A4","orientation":"portrait","marginMm":[12,10,12,10],"layoutMode":"flow"},"items":[{"elementCode":"docTitle","visible":true,"style":{"fontSizePt":14,"align":"center","bold":true},"labelOverride":"检验申请单"}]}',
    'print_page_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'lab_apply_form' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000013,
    0,
    'exam_apply_form',
    '检查申请单',
    'page',
    '{"pipeline":"page","page":{"size":"A4","orientation":"portrait","marginMm":[12,10,12,10],"layoutMode":"flow"},"items":[{"elementCode":"docTitle","visible":true,"style":{"fontSizePt":14,"align":"center","bold":true},"labelOverride":"检查申请单"}]}',
    'print_page_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'exam_apply_form' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000014,
    0,
    'procedure_apply_form',
    '治疗/处置申请单',
    'page',
    '{"pipeline":"page","page":{"size":"A4","orientation":"portrait","marginMm":[12,10,12,10],"layoutMode":"flow"},"items":[{"elementCode":"docTitle","visible":true,"style":{"fontSizePt":14,"align":"center","bold":true},"labelOverride":"治疗/处置申请单"}]}',
    'print_page_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'procedure_apply_form' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000015,
    0,
    'diagnosis_certificate',
    '诊断/病休证明',
    'page',
    '{"pipeline":"page","page":{"size":"A4","orientation":"portrait","marginMm":[12,10,12,10],"layoutMode":"flow"},"items":[{"elementCode":"docTitle","visible":true,"style":{"fontSizePt":14,"align":"center","bold":true},"labelOverride":"诊断/病休证明"}]}',
    'print_page_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'diagnosis_certificate' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000016,
    0,
    'dispense_slip',
    '发药单/取药单',
    'page',
    '{"pipeline":"page","page":{"size":"A4","orientation":"portrait","marginMm":[12,10,12,10],"layoutMode":"flow"},"items":[{"elementCode":"docTitle","visible":true,"style":{"fontSizePt":18,"align":"center","bold":true},"labelOverride":"发药单"},{"elementCode":"hospitalName","visible":true,"style":{"align":"center","fontSizePt":11}},{"elementCode":"prescriptionMetaRow","visible":true},{"elementCode":"patientInfoRow","visible":true},{"elementCode":"diagnosisText","visible":true,"labelOverride":"临床诊断："},{"elementCode":"prescriptionDrugTable","visible":true},{"elementCode":"prescriptionFooterRow","visible":true}]}',
    'outpatient_prescription',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'dispense_slip' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000017,
    0,
    'drug_label',
    '用药标签（药房通用）',
    'roll',
    '{"pipeline":"roll","page":{"widthMm":80,"layoutMode":"flow"},"items":[{"elementCode":"rollTitle","visible":true,"labelOverride":"用药标签（药房通用）"}]}',
    'print_roll_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'drug_label' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000018,
    0,
    'pharmacy_queue_ticket',
    '配药排队号',
    'roll',
    '{"pipeline":"roll","page":{"widthMm":80,"layoutMode":"flow"},"items":[{"elementCode":"rollTitle","visible":true,"labelOverride":"配药排队号"}]}',
    'print_roll_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'pharmacy_queue_ticket' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000019,
    0,
    'iv_infusion_label',
    '输液瓶贴',
    'roll',
    '{"pipeline":"roll","page":{"widthMm":80,"layoutMode":"flow"},"items":[{"elementCode":"rollTitle","visible":true,"labelOverride":"输液瓶贴"}]}',
    'print_roll_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'iv_infusion_label' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000020,
    0,
    'injection_label',
    '注射用药标签',
    'roll',
    '{"pipeline":"roll","page":{"widthMm":80,"layoutMode":"flow"},"items":[{"elementCode":"rollTitle","visible":true,"labelOverride":"注射用药标签"}]}',
    'print_roll_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'injection_label' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000021,
    0,
    'oral_drug_label',
    '口服药标签/服药单',
    'roll',
    '{"pipeline":"roll","page":{"widthMm":80,"layoutMode":"flow"},"items":[{"elementCode":"rollTitle","visible":true,"labelOverride":"口服药标签/服药单"}]}',
    'print_roll_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'oral_drug_label' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000022,
    0,
    'treatment_execute_sheet',
    '治疗/处置执行单',
    'page',
    '{"pipeline":"page","page":{"size":"A4","orientation":"portrait","marginMm":[12,10,12,10],"layoutMode":"flow"},"items":[{"elementCode":"docTitle","visible":true,"style":{"fontSizePt":14,"align":"center","bold":true},"labelOverride":"治疗/处置执行单"}]}',
    'print_page_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'treatment_execute_sheet' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000023,
    0,
    'bedside_specimen_barcode',
    '采血条码（床旁）',
    'roll',
    '{"pipeline":"roll","page":{"widthMm":80,"layoutMode":"flow"},"items":[{"elementCode":"rollTitle","visible":true,"labelOverride":"采血条码（床旁）"}]}',
    'print_roll_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'bedside_specimen_barcode' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000024,
    0,
    'iv_infusion_execute_sheet',
    '输液执行单/输液卡',
    'page',
    '{"pipeline":"page","page":{"size":"A4","orientation":"portrait","marginMm":[12,10,12,10],"layoutMode":"flow"},"items":[{"elementCode":"docTitle","visible":true,"style":{"fontSizePt":14,"align":"center","bold":true},"labelOverride":"输液执行单/输液卡"}]}',
    'print_page_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'iv_infusion_execute_sheet' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000025,
    0,
    'skin_test_label',
    '皮试标签',
    'roll',
    '{"pipeline":"roll","page":{"widthMm":80,"layoutMode":"flow"},"items":[{"elementCode":"rollTitle","visible":true,"labelOverride":"皮试标签"}]}',
    'print_roll_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'skin_test_label' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000026,
    0,
    'nebulization_label',
    '雾化/吸入标签',
    'roll',
    '{"pipeline":"roll","page":{"widthMm":80,"layoutMode":"flow"},"items":[{"elementCode":"rollTitle","visible":true,"labelOverride":"雾化/吸入标签"}]}',
    'print_roll_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'nebulization_label' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000027,
    0,
    'triage_queue_ticket',
    '分诊排队小票',
    'roll',
    '{"pipeline":"roll","page":{"widthMm":80,"layoutMode":"flow"},"items":[{"elementCode":"rollTitle","visible":true,"labelOverride":"分诊排队小票"}]}',
    'print_roll_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'triage_queue_ticket' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000028,
    0,
    'outpatient_nurse_prescription',
    '门诊护士处方执行联',
    'page',
    '{"pipeline":"page","page":{"size":"A4","orientation":"portrait","marginMm":[12,10,12,10],"layoutMode":"flow"},"items":[{"elementCode":"docTitle","visible":true,"style":{"fontSizePt":18,"align":"center","bold":true},"labelOverride":"护士处方执行联"},{"elementCode":"hospitalName","visible":true,"style":{"align":"center","fontSizePt":11}},{"elementCode":"prescriptionMetaRow","visible":true},{"elementCode":"patientInfoRow","visible":true},{"elementCode":"diagnosisText","visible":true,"labelOverride":"临床诊断："},{"elementCode":"prescriptionDrugTable","visible":true},{"elementCode":"prescriptionFooterRow","visible":true}]}',
    'outpatient_prescription',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'outpatient_nurse_prescription' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000029,
    0,
    'admission_certificate',
    '入院证',
    'page',
    '{"pipeline":"page","page":{"size":"A4","orientation":"portrait","marginMm":[12,10,12,10],"layoutMode":"flow"},"items":[{"elementCode":"docTitle","visible":true,"style":{"fontSizePt":14,"align":"center","bold":true},"labelOverride":"入院证"}]}',
    'print_page_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'admission_certificate' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000030,
    0,
    'patient_wristband',
    '患者腕带',
    'roll',
    '{"pipeline":"roll","page":{"widthMm":80,"layoutMode":"flow"},"items":[{"elementCode":"rollTitle","visible":true,"labelOverride":"患者腕带"}]}',
    'print_roll_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'patient_wristband' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000031,
    0,
    'infant_wristband',
    '婴儿腕带',
    'roll',
    '{"pipeline":"roll","page":{"widthMm":80,"layoutMode":"flow"},"items":[{"elementCode":"rollTitle","visible":true,"labelOverride":"婴儿腕带"}]}',
    'print_roll_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'infant_wristband' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000032,
    0,
    'bed_card',
    '床头卡',
    'roll',
    '{"pipeline":"roll","page":{"widthMm":80,"layoutMode":"flow"},"items":[{"elementCode":"rollTitle","visible":true,"labelOverride":"床头卡"}]}',
    'print_roll_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'bed_card' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000033,
    0,
    'discharge_certificate',
    '出院证/出院小结单证',
    'page',
    '{"pipeline":"page","page":{"size":"A4","orientation":"portrait","marginMm":[12,10,12,10],"layoutMode":"flow"},"items":[{"elementCode":"docTitle","visible":true,"style":{"fontSizePt":14,"align":"center","bold":true},"labelOverride":"出院证/出院小结单证"}]}',
    'print_page_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'discharge_certificate' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000034,
    0,
    'nursing_record',
    '护理记录单',
    'page',
    '{"pipeline":"page","page":{"size":"A4","orientation":"portrait","marginMm":[12,10,12,10],"layoutMode":"flow"},"items":[{"elementCode":"docTitle","visible":true,"style":{"fontSizePt":14,"align":"center","bold":true},"labelOverride":"护理记录单"}]}',
    'print_page_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'nursing_record' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000035,
    0,
    'order_execute_sheet',
    '医嘱执行单（单患者）',
    'page',
    '{"pipeline":"page","page":{"size":"A4","orientation":"portrait","marginMm":[12,10,12,10],"layoutMode":"flow"},"items":[{"elementCode":"docTitle","visible":true,"style":{"fontSizePt":14,"align":"center","bold":true},"labelOverride":"医嘱执行单（单患者）"}]}',
    'print_page_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'order_execute_sheet' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000036,
    0,
    'inpatient_order_sheet',
    '长期/临时医嘱单',
    'page',
    '{"pipeline":"page","page":{"size":"A4","orientation":"portrait","marginMm":[12,10,12,10],"layoutMode":"flow"},"items":[{"elementCode":"docTitle","visible":true,"style":{"fontSizePt":14,"align":"center","bold":true},"labelOverride":"长期/临时医嘱单"}]}',
    'print_page_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'inpatient_order_sheet' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000037,
    0,
    'ward_dispense_summary',
    '统领摆药单/病区发药汇总',
    'page',
    '{"pipeline":"page","page":{"size":"A4","orientation":"portrait","marginMm":[12,10,12,10],"layoutMode":"flow"},"items":[{"elementCode":"docTitle","visible":true,"style":{"fontSizePt":14,"align":"center","bold":true},"labelOverride":"统领摆药单/病区发药汇总"}]}',
    'print_page_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'ward_dispense_summary' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000038,
    0,
    'temperature_chart',
    '体温单',
    'page',
    '{"pipeline":"page","page":{"size":"A4","orientation":"portrait","marginMm":[12,10,12,10],"layoutMode":"flow"},"items":[{"elementCode":"docTitle","visible":true,"style":{"fontSizePt":14,"align":"center","bold":true},"labelOverride":"体温单"}]}',
    'print_page_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'temperature_chart' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000039,
    0,
    'nurse_shift_handover',
    '交接班记录',
    'page',
    '{"pipeline":"page","page":{"size":"A4","orientation":"portrait","marginMm":[12,10,12,10],"layoutMode":"flow"},"items":[{"elementCode":"docTitle","visible":true,"style":{"fontSizePt":14,"align":"center","bold":true},"labelOverride":"交接班记录"}]}',
    'print_page_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'nurse_shift_handover' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000040,
    0,
    'nursing_care_label',
    '管道/护理标识贴',
    'roll',
    '{"pipeline":"roll","page":{"widthMm":80,"layoutMode":"flow"},"items":[{"elementCode":"rollTitle","visible":true,"labelOverride":"管道/护理标识贴"}]}',
    'print_roll_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'nursing_care_label' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000041,
    0,
    'companion_wristband',
    '陪护腕带',
    'roll',
    '{"pipeline":"roll","page":{"widthMm":80,"layoutMode":"flow"},"items":[{"elementCode":"rollTitle","visible":true,"labelOverride":"陪护腕带"}]}',
    'print_roll_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'companion_wristband' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000042,
    0,
    'inpatient_daily_charge_list',
    '住院患者日费用清单',
    'page',
    '{"pipeline":"page","page":{"size":"A4","orientation":"portrait","marginMm":[12,10,12,10],"layoutMode":"flow"},"items":[{"elementCode":"docTitle","visible":true,"style":{"fontSizePt":14,"align":"center","bold":true},"labelOverride":"住院患者日费用清单"}]}',
    'print_page_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'inpatient_daily_charge_list' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000043,
    0,
    'case_record_homepage',
    '病案首页',
    'page',
    '{"pipeline":"page","page":{"size":"A4","orientation":"portrait","marginMm":[12,10,12,10],"layoutMode":"flow"},"items":[{"elementCode":"docTitle","visible":true,"style":{"fontSizePt":14,"align":"center","bold":true},"labelOverride":"病案首页"}]}',
    'print_page_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'case_record_homepage' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000044,
    0,
    'inpatient_prescription',
    '住院处方笺',
    'page',
    '{"pipeline":"page","page":{"size":"A4","orientation":"portrait","marginMm":[12,10,12,10],"layoutMode":"flow"},"items":[{"elementCode":"docTitle","visible":true,"style":{"fontSizePt":18,"align":"center","bold":true},"labelOverride":"住院处方笺"},{"elementCode":"hospitalName","visible":true,"style":{"align":"center","fontSizePt":11}},{"elementCode":"prescriptionMetaRow","visible":true},{"elementCode":"patientInfoRow","visible":true},{"elementCode":"diagnosisText","visible":true,"labelOverride":"临床诊断："},{"elementCode":"prescriptionDrugTable","visible":true},{"elementCode":"prescriptionFooterRow","visible":true}]}',
    'outpatient_prescription',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'inpatient_prescription' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000045,
    0,
    'surgery_apply_form',
    '手术/麻醉申请单',
    'page',
    '{"pipeline":"page","page":{"size":"A4","orientation":"portrait","marginMm":[12,10,12,10],"layoutMode":"flow"},"items":[{"elementCode":"docTitle","visible":true,"style":{"fontSizePt":14,"align":"center","bold":true},"labelOverride":"手术/麻醉申请单"}]}',
    'print_page_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'surgery_apply_form' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000046,
    0,
    'blood_apply_form',
    '输血申请单',
    'page',
    '{"pipeline":"page","page":{"size":"A4","orientation":"portrait","marginMm":[12,10,12,10],"layoutMode":"flow"},"items":[{"elementCode":"docTitle","visible":true,"style":{"fontSizePt":14,"align":"center","bold":true},"labelOverride":"输血申请单"}]}',
    'print_page_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'blood_apply_form' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000047,
    0,
    'inpatient_charge_detail_list',
    '住院费用清单（明细）',
    'page',
    '{"pipeline":"page","page":{"size":"A4","orientation":"portrait","marginMm":[12,10,12,10],"layoutMode":"flow"},"items":[{"elementCode":"docTitle","visible":true,"style":{"fontSizePt":14,"align":"center","bold":true},"labelOverride":"住院费用清单（明细）"}]}',
    'print_page_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'inpatient_charge_detail_list' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000048,
    0,
    'deposit_receipt',
    '预交金收据',
    'roll',
    '{"pipeline":"roll","page":{"widthMm":80,"layoutMode":"flow"},"items":[{"elementCode":"rollTitle","visible":true,"labelOverride":"预交金收据"}]}',
    'print_roll_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'deposit_receipt' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000049,
    0,
    'inpatient_invoice_overlay',
    '住院收费票据（套打）',
    'page',
    '{"pipeline":"page","page":{"size":"A5","orientation":"portrait","layoutMode":"overlay","marginMm":[0,0,0,0],"overlay":{}},"items":[{"elementCode":"overlayField","visible":true,"box":{"xMm":20,"yMm":20,"widthMm":60,"heightMm":6}}]}',
    'print_page_overlay_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'inpatient_invoice_overlay' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000050,
    0,
    'inpatient_charge_summary',
    '住院费用总清单',
    'page',
    '{"pipeline":"page","page":{"size":"A4","orientation":"portrait","marginMm":[12,10,12,10],"layoutMode":"flow"},"items":[{"elementCode":"docTitle","visible":true,"style":{"fontSizePt":14,"align":"center","bold":true},"labelOverride":"住院费用总清单"}]}',
    'print_page_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'inpatient_charge_summary' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000051,
    0,
    'insurance_settlement',
    '医保结算单',
    'page',
    '{"pipeline":"page","page":{"size":"A4","orientation":"portrait","marginMm":[12,10,12,10],"layoutMode":"flow"},"items":[{"elementCode":"docTitle","visible":true,"style":{"fontSizePt":14,"align":"center","bold":true},"labelOverride":"医保结算单"}]}',
    'print_page_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'insurance_settlement' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000052,
    0,
    'inpatient_daily_settlement_pdf',
    '住院日结报表（PDF）',
    'page',
    '{"pipeline":"page","page":{"size":"A4","orientation":"portrait","marginMm":[12,10,12,10],"layoutMode":"flow"},"items":[{"elementCode":"docTitle","visible":true,"style":{"fontSizePt":14,"align":"center","bold":true},"labelOverride":"住院日结报表（PDF）"}]}',
    'print_page_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'inpatient_daily_settlement_pdf' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000053,
    0,
    'inpatient_drug_label',
    '药品标签（住院包药机）',
    'roll',
    '{"pipeline":"roll","page":{"widthMm":80,"layoutMode":"flow"},"items":[{"elementCode":"rollTitle","visible":true,"labelOverride":"药品标签（住院包药机）"}]}',
    'print_roll_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'inpatient_drug_label' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000054,
    0,
    'tcm_decoction_label',
    '中药代煎标签',
    'roll',
    '{"pipeline":"roll","page":{"widthMm":80,"layoutMode":"flow"},"items":[{"elementCode":"rollTitle","visible":true,"labelOverride":"中药代煎标签"}]}',
    'print_roll_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'tcm_decoction_label' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000055,
    0,
    'shelf_price_label',
    '货位/价签',
    'roll',
    '{"pipeline":"roll","page":{"widthMm":80,"layoutMode":"flow"},"items":[{"elementCode":"rollTitle","visible":true,"labelOverride":"货位/价签"}]}',
    'print_roll_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'shelf_price_label' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000056,
    0,
    'controlled_drug_label',
    '毒麻精专区标识',
    'roll',
    '{"pipeline":"roll","page":{"widthMm":80,"layoutMode":"flow"},"items":[{"elementCode":"rollTitle","visible":true,"labelOverride":"毒麻精专区标识"}]}',
    'print_roll_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'controlled_drug_label' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000057,
    0,
    'pivas_infusion_label',
    '集中配液瓶贴',
    'roll',
    '{"pipeline":"roll","page":{"widthMm":80,"layoutMode":"flow"},"items":[{"elementCode":"rollTitle","visible":true,"labelOverride":"集中配液瓶贴"}]}',
    'print_roll_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'pivas_infusion_label' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000058,
    0,
    'specimen_barcode',
    '检验条码/标本管贴',
    'roll',
    '{"pipeline":"roll","page":{"widthMm":80,"layoutMode":"flow"},"items":[{"elementCode":"rollTitle","visible":true,"labelOverride":"检验条码/标本管贴"}]}',
    'print_roll_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'specimen_barcode' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000059,
    0,
    'exam_appointment_slip',
    '检查预约/到检单',
    'page',
    '{"pipeline":"page","page":{"size":"A4","orientation":"portrait","marginMm":[12,10,12,10],"layoutMode":"flow"},"items":[{"elementCode":"docTitle","visible":true,"style":{"fontSizePt":14,"align":"center","bold":true},"labelOverride":"检查预约/到检单"}]}',
    'print_page_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'exam_appointment_slip' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000060,
    0,
    'surgery_safety_checklist',
    '手术安全核查表',
    'page',
    '{"pipeline":"page","page":{"size":"A4","orientation":"portrait","marginMm":[12,10,12,10],"layoutMode":"flow"},"items":[{"elementCode":"docTitle","visible":true,"style":{"fontSizePt":14,"align":"center","bold":true},"labelOverride":"手术安全核查表"}]}',
    'print_page_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'surgery_safety_checklist' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000061,
    0,
    'surgery_handover_sheet',
    '手术交接/转运单',
    'page',
    '{"pipeline":"page","page":{"size":"A4","orientation":"portrait","marginMm":[12,10,12,10],"layoutMode":"flow"},"items":[{"elementCode":"docTitle","visible":true,"style":{"fontSizePt":14,"align":"center","bold":true},"labelOverride":"手术交接/转运单"}]}',
    'print_page_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'surgery_handover_sheet' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000062,
    0,
    'instrument_pack_label',
    '器械包/消毒包标签',
    'roll',
    '{"pipeline":"roll","page":{"widthMm":80,"layoutMode":"flow"},"items":[{"elementCode":"rollTitle","visible":true,"labelOverride":"器械包/消毒包标签"}]}',
    'print_roll_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'instrument_pack_label' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000063,
    0,
    'blood_reserve_label',
    '备血标签',
    'roll',
    '{"pipeline":"roll","page":{"widthMm":80,"layoutMode":"flow"},"items":[{"elementCode":"rollTitle","visible":true,"labelOverride":"备血标签"}]}',
    'print_roll_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'blood_reserve_label' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000064,
    0,
    'blood_transfusion_label',
    '血袋/输血执行标签',
    'roll',
    '{"pipeline":"roll","page":{"widthMm":80,"layoutMode":"flow"},"items":[{"elementCode":"rollTitle","visible":true,"labelOverride":"血袋/输血执行标签"}]}',
    'print_roll_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'blood_transfusion_label' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000065,
    0,
    'emergency_wristband',
    '急诊预检分诊腕带',
    'roll',
    '{"pipeline":"roll","page":{"widthMm":80,"layoutMode":"flow"},"items":[{"elementCode":"rollTitle","visible":true,"labelOverride":"急诊预检分诊腕带"}]}',
    'print_roll_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'emergency_wristband' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000066,
    0,
    'emergency_triage_ticket',
    '急诊分诊排队小票',
    'roll',
    '{"pipeline":"roll","page":{"widthMm":80,"layoutMode":"flow"},"items":[{"elementCode":"rollTitle","visible":true,"labelOverride":"急诊分诊排队小票"}]}',
    'print_roll_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'emergency_triage_ticket' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000067,
    0,
    'health_check_guide',
    '体检导引单',
    'page',
    '{"pipeline":"page","page":{"size":"A4","orientation":"portrait","marginMm":[12,10,12,10],"layoutMode":"flow"},"items":[{"elementCode":"docTitle","visible":true,"style":{"fontSizePt":14,"align":"center","bold":true},"labelOverride":"体检导引单"}]}',
    'print_page_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'health_check_guide' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000068,
    0,
    'supply_label',
    '材料/高值耗材标签',
    'roll',
    '{"pipeline":"roll","page":{"widthMm":80,"layoutMode":"flow"},"items":[{"elementCode":"rollTitle","visible":true,"labelOverride":"材料/高值耗材标签"}]}',
    'print_roll_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'supply_label' AND t.is_deleted = FALSE
);

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000069,
    0,
    'insurance_ec_voucher_slip',
    '医保电子凭证替代凭条',
    'roll',
    '{"pipeline":"roll","page":{"widthMm":80,"layoutMode":"flow"},"items":[{"elementCode":"rollTitle","visible":true,"labelOverride":"医保电子凭证替代凭条"}]}',
    'print_roll_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'insurance_ec_voucher_slip' AND t.is_deleted = FALSE
);

-- ========== V7__Seed_iv_infusion_label.sql ==========

-- iv_infusion_label 元素包 + 升级出厂模板（OP-23 / PRT-OP-23）

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910003000001, 0, 'iv_infusion_label', 'patientName', '患者姓名', 'text', 'patient.name',
       NULL, NULL, NULL, 'flow', 10, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'iv_infusion_label' AND e.element_code = 'patientName' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910003000002, 0, 'iv_infusion_label', 'patientBed', '床号/流水', 'text', 'patient.bedNo',
       NULL, NULL, NULL, 'flow', 20, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'iv_infusion_label' AND e.element_code = 'patientBed' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910003000003, 0, 'iv_infusion_label', 'drugGroupTitle', '输液组', 'text', 'infusion.groupName',
       NULL, NULL, NULL, 'flow', 30, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'iv_infusion_label' AND e.element_code = 'drugGroupTitle' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910003000004, 0, 'iv_infusion_label', 'drugLines', '药品明细', 'table', 'infusion.drugs',
       NULL,
       '[{"field":"drugText","header":"药品"}]',
       NULL, 'flow', 40, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'iv_infusion_label' AND e.element_code = 'drugLines' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910003000005, 0, 'iv_infusion_label', 'routeFrequency', '途径频次', 'text', 'order.routeFrequency',
       NULL, NULL, NULL, 'flow', 50, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'iv_infusion_label' AND e.element_code = 'routeFrequency' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910003000006, 0, 'iv_infusion_label', 'planTime', '计划时间', 'text', 'order.planTime',
       NULL, NULL, NULL, 'flow', 60, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'iv_infusion_label' AND e.element_code = 'planTime' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910003000007, 0, 'iv_infusion_label', 'executeBarcode', '执行条码', 'text', 'order.executeBarcode',
       NULL, NULL, NULL, 'flow', 70, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'iv_infusion_label' AND e.element_code = 'executeBarcode' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910003000008, 0, 'iv_infusion_label', 'dividerLine', '分隔线', 'line', NULL,
       NULL, NULL, NULL, 'flow', 80, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'iv_infusion_label' AND e.element_code = 'dividerLine' AND e.is_deleted = FALSE
);

UPDATE rpt_template
SET name = '输液瓶贴',
    pipeline = 'roll',
    dataset_code = 'iv_infusion_label',
    layout_json = '{"pipeline":"roll","page":{"widthMm":50,"layoutMode":"flow","marginMm":[2,2,2,2]},"items":[{"elementCode":"patientName","visible":true},{"elementCode":"patientBed","visible":true},{"elementCode":"drugGroupTitle","visible":true},{"elementCode":"drugLines","visible":true},{"elementCode":"routeFrequency","visible":true},{"elementCode":"planTime","visible":true},{"elementCode":"executeBarcode","visible":true},{"elementCode":"dividerLine","visible":true}]}'
WHERE tenant_id = 0
  AND code = 'iv_infusion_label'
  AND is_deleted = FALSE;

-- ========== V8__Seed_bedside_specimen_and_treatment.sql ==========

-- bedside_specimen_barcode + treatment_execute_sheet 元素包 + 升级出厂模板（OP-27 / OP-26）

-- ========== bedside_specimen_barcode ==========

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910004000001, 0, 'bedside_specimen_barcode', 'patientName', '患者姓名', 'text', 'patient.name',
       NULL, NULL, NULL, 'flow', 10, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'bedside_specimen_barcode' AND e.element_code = 'patientName' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910004000002, 0, 'bedside_specimen_barcode', 'patientMrn', '病历号', 'text', 'patient.mrn',
       NULL, NULL, NULL, 'flow', 20, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'bedside_specimen_barcode' AND e.element_code = 'patientMrn' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910004000003, 0, 'bedside_specimen_barcode', 'specimenType', '标本类型', 'text', 'specimen.typeName',
       NULL, NULL, NULL, 'flow', 30, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'bedside_specimen_barcode' AND e.element_code = 'specimenType' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910004000004, 0, 'bedside_specimen_barcode', 'tubeColor', '试管颜色', 'text', 'specimen.tubeColor',
       NULL, NULL, NULL, 'flow', 40, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'bedside_specimen_barcode' AND e.element_code = 'tubeColor' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910004000005, 0, 'bedside_specimen_barcode', 'orderItem', '检验项目', 'text', 'order.itemName',
       NULL, NULL, NULL, 'flow', 50, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'bedside_specimen_barcode' AND e.element_code = 'orderItem' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910004000006, 0, 'bedside_specimen_barcode', 'specimenBarcode', '标本条码', 'text', 'specimen.barcode',
       NULL, NULL, NULL, 'flow', 60, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'bedside_specimen_barcode' AND e.element_code = 'specimenBarcode' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910004000007, 0, 'bedside_specimen_barcode', 'collectTime', '采集时间', 'text', 'specimen.collectTime',
       NULL, NULL, NULL, 'flow', 70, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'bedside_specimen_barcode' AND e.element_code = 'collectTime' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910004000008, 0, 'bedside_specimen_barcode', 'dividerLine', '分隔线', 'line', NULL,
       NULL, NULL, NULL, 'flow', 80, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'bedside_specimen_barcode' AND e.element_code = 'dividerLine' AND e.is_deleted = FALSE
);

UPDATE rpt_template
SET name = '采血条码（床旁）',
    pipeline = 'roll',
    dataset_code = 'bedside_specimen_barcode',
    layout_json = '{"pipeline":"roll","page":{"widthMm":50,"layoutMode":"flow","marginMm":[2,2,2,2]},"items":[{"elementCode":"patientName","visible":true},{"elementCode":"patientMrn","visible":true},{"elementCode":"specimenType","visible":true},{"elementCode":"tubeColor","visible":true},{"elementCode":"orderItem","visible":true},{"elementCode":"specimenBarcode","visible":true},{"elementCode":"collectTime","visible":true},{"elementCode":"dividerLine","visible":true}]}'
WHERE tenant_id = 0
  AND code = 'bedside_specimen_barcode'
  AND is_deleted = FALSE;

-- ========== treatment_execute_sheet ==========

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910004000101, 0, 'treatment_execute_sheet', 'docTitle', '标题', 'staticTitle', NULL,
       NULL, NULL, '治疗/处置执行单', 'flow', 10, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'treatment_execute_sheet' AND e.element_code = 'docTitle' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910004000102, 0, 'treatment_execute_sheet', 'hospitalName', '医疗机构', 'text', 'hospital.name',
       NULL, NULL, NULL, 'flow', 20, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'treatment_execute_sheet' AND e.element_code = 'hospitalName' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910004000103, 0, 'treatment_execute_sheet', 'patientInfoRow', '患者信息', 'kvRow', 'patient',
       '[{"label":"姓名","field":"name"},{"label":"性别","field":"gender"},{"label":"年龄","field":"age"},{"label":"流水号","field":"bedNo"},{"label":"科室","field":"deptName"}]',
       NULL, NULL, 'flow', 30, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'treatment_execute_sheet' AND e.element_code = 'patientInfoRow' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910004000104, 0, 'treatment_execute_sheet', 'orderMetaRow', '单头信息', 'kvRow', 'order',
       '[{"label":"单号","field":"sheetNo"},{"label":"开立医生","field":"prescriberName"},{"label":"日期","field":"authoredDate"}]',
       NULL, NULL, 'flow', 40, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'treatment_execute_sheet' AND e.element_code = 'orderMetaRow' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910004000105, 0, 'treatment_execute_sheet', 'procedureTable', '处置明细', 'table', 'order.lines',
       NULL,
       '[{"field":"itemName","header":"项目"},{"field":"bodySite","header":"部位"},{"field":"frequency","header":"频次"},{"field":"remark","header":"备注"}]',
       NULL, 'flow', 50, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'treatment_execute_sheet' AND e.element_code = 'procedureTable' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910004000106, 0, 'treatment_execute_sheet', 'nurseSignArea', '执行签名', 'kvRow', 'execution',
       '[{"label":"执行护士","field":"nurseName"},{"label":"执行时间","field":"executeTime"}]',
       NULL, NULL, 'flow', 60, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'treatment_execute_sheet' AND e.element_code = 'nurseSignArea' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910004000107, 0, 'treatment_execute_sheet', 'dividerLine', '分隔线', 'line', NULL,
       NULL, NULL, NULL, 'flow', 70, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'treatment_execute_sheet' AND e.element_code = 'dividerLine' AND e.is_deleted = FALSE
);

UPDATE rpt_template
SET name = '治疗/处置执行单',
    pipeline = 'page',
    dataset_code = 'treatment_execute_sheet',
    layout_json = '{"pipeline":"page","page":{"size":"A4","orientation":"portrait","marginMm":[12,10,12,10],"layoutMode":"flow"},"items":[{"elementCode":"docTitle","visible":true,"style":{"fontSizePt":16,"align":"center","bold":true},"labelOverride":"治疗/处置执行单"},{"elementCode":"hospitalName","visible":true,"style":{"align":"center","fontSizePt":11}},{"elementCode":"patientInfoRow","visible":true},{"elementCode":"orderMetaRow","visible":true},{"elementCode":"procedureTable","visible":true},{"elementCode":"nurseSignArea","visible":true},{"elementCode":"dividerLine","visible":true}]}'
WHERE tenant_id = 0
  AND code = 'treatment_execute_sheet'
  AND is_deleted = FALSE;

-- ========== V9__Seed_patient_wristband.sql ==========

-- patient_wristband 元素包 + 升级出厂模板（IP-02 / OT-03）

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910005000001, 0, 'patient_wristband', 'patientName', '患者姓名', 'text', 'patient.name',
       NULL, NULL, NULL, 'flow', 10, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'patient_wristband' AND e.element_code = 'patientName' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910005000002, 0, 'patient_wristband', 'patientGender', '性别', 'text', 'patient.gender',
       NULL, NULL, NULL, 'flow', 20, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'patient_wristband' AND e.element_code = 'patientGender' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910005000003, 0, 'patient_wristband', 'patientAge', '年龄', 'text', 'patient.age',
       NULL, NULL, NULL, 'flow', 30, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'patient_wristband' AND e.element_code = 'patientAge' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910005000004, 0, 'patient_wristband', 'patientMrn', '病历号', 'text', 'patient.mrn',
       NULL, NULL, NULL, 'flow', 40, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'patient_wristband' AND e.element_code = 'patientMrn' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910005000005, 0, 'patient_wristband', 'deptBed', '科室床号', 'text', 'encounter.deptBed',
       NULL, NULL, NULL, 'flow', 50, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'patient_wristband' AND e.element_code = 'deptBed' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910005000006, 0, 'patient_wristband', 'admissionDate', '入院日期', 'text', 'encounter.admissionDate',
       NULL, NULL, NULL, 'flow', 60, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'patient_wristband' AND e.element_code = 'admissionDate' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910005000007, 0, 'patient_wristband', 'wristbandBarcode', '腕带条码', 'text', 'patient.wristbandBarcode',
       NULL, NULL, NULL, 'flow', 70, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'patient_wristband' AND e.element_code = 'wristbandBarcode' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910005000008, 0, 'patient_wristband', 'dividerLine', '分隔线', 'line', NULL,
       NULL, NULL, NULL, 'flow', 80, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'patient_wristband' AND e.element_code = 'dividerLine' AND e.is_deleted = FALSE
);

UPDATE rpt_template
SET name = '患者腕带',
    pipeline = 'roll',
    dataset_code = 'patient_wristband',
    layout_json = '{"pipeline":"roll","page":{"widthMm":30,"layoutMode":"flow","marginMm":[1,1,1,1]},"items":[{"elementCode":"patientName","visible":true,"style":{"fontSizePt":11,"bold":true}},{"elementCode":"patientGender","visible":true},{"elementCode":"patientAge","visible":true},{"elementCode":"patientMrn","visible":true},{"elementCode":"deptBed","visible":true},{"elementCode":"admissionDate","visible":true},{"elementCode":"wristbandBarcode","visible":true},{"elementCode":"dividerLine","visible":true}]}'
WHERE tenant_id = 0
  AND code = 'patient_wristband'
  AND is_deleted = FALSE;

-- ========== V10__Seed_inpatient_daily_charge_list.sql ==========

-- inpatient_daily_charge_list 元素包 + 升级出厂模板（IP-38）

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910006000001, 0, 'inpatient_daily_charge_list', 'docTitle', '标题', 'staticTitle', NULL,
       NULL, NULL, '住院患者日费用清单', 'flow', 10, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'inpatient_daily_charge_list' AND e.element_code = 'docTitle' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910006000002, 0, 'inpatient_daily_charge_list', 'hospitalName', '医疗机构', 'text', 'hospital.name',
       NULL, NULL, NULL, 'flow', 20, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'inpatient_daily_charge_list' AND e.element_code = 'hospitalName' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910006000003, 0, 'inpatient_daily_charge_list', 'patientInfoRow', '患者信息', 'kvRow', 'patient',
       '[{"label":"姓名","field":"name"},{"label":"床号","field":"bedNo"},{"label":"住院号","field":"inpatientNo"},{"label":"科室","field":"deptName"}]',
       NULL, NULL, 'flow', 30, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'inpatient_daily_charge_list' AND e.element_code = 'patientInfoRow' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910006000004, 0, 'inpatient_daily_charge_list', 'billDateRow', '费用日期', 'kvRow', 'bill',
       '[{"label":"费用日期","field":"billDate"},{"label":"入院天数","field":"admissionDays"}]',
       NULL, NULL, 'flow', 40, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'inpatient_daily_charge_list' AND e.element_code = 'billDateRow' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910006000005, 0, 'inpatient_daily_charge_list', 'chargeDetailTable', '费用明细', 'table', 'charge.lines',
       NULL,
       '[{"field":"itemName","header":"项目"},{"field":"quantity","header":"数量"},{"field":"unitPrice","header":"单价"},{"field":"amount","header":"金额"},{"field":"category","header":"类别"}]',
       NULL, 'flow', 50, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'inpatient_daily_charge_list' AND e.element_code = 'chargeDetailTable' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910006000006, 0, 'inpatient_daily_charge_list', 'dailySummaryRow', '费用汇总', 'kvRow', 'summary',
       '[{"label":"日小计","field":"dailySubtotal"},{"label":"累计费用","field":"cumulativeTotal"},{"label":"预交金额","field":"prepayAmount"},{"label":"结余","field":"balance"}]',
       NULL, NULL, 'flow', 60, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'inpatient_daily_charge_list' AND e.element_code = 'dailySummaryRow' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910006000007, 0, 'inpatient_daily_charge_list', 'printTime', '打印时间', 'text', 'system.printTime',
       NULL, NULL, NULL, 'flow', 70, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'inpatient_daily_charge_list' AND e.element_code = 'printTime' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910006000008, 0, 'inpatient_daily_charge_list', 'dividerLine', '分隔线', 'line', NULL,
       NULL, NULL, NULL, 'flow', 80, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'inpatient_daily_charge_list' AND e.element_code = 'dividerLine' AND e.is_deleted = FALSE
);

UPDATE rpt_template
SET name = '住院患者日费用清单',
    pipeline = 'page',
    dataset_code = 'inpatient_daily_charge_list',
    layout_json = '{"pipeline":"page","page":{"size":"A4","orientation":"portrait","marginMm":[12,10,12,10],"layoutMode":"flow"},"items":[{"elementCode":"docTitle","visible":true,"style":{"fontSizePt":16,"align":"center","bold":true},"labelOverride":"住院患者日费用清单"},{"elementCode":"hospitalName","visible":true,"style":{"align":"center","fontSizePt":11}},{"elementCode":"patientInfoRow","visible":true},{"elementCode":"billDateRow","visible":true},{"elementCode":"chargeDetailTable","visible":true},{"elementCode":"dailySummaryRow","visible":true},{"elementCode":"printTime","visible":true},{"elementCode":"dividerLine","visible":true}]}'
WHERE tenant_id = 0
  AND code = 'inpatient_daily_charge_list'
  AND is_deleted = FALSE;

-- ========== V11__Seed_deposit_receipt.sql ==========

-- deposit_receipt 元素包 + 升级出厂模板（IP-30）

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910007000001, 0, 'deposit_receipt', 'storeName', '机构名', 'text', 'hospital.name',
       NULL, NULL, NULL, 'flow', 10, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'deposit_receipt' AND e.element_code = 'storeName' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910007000002, 0, 'deposit_receipt', 'depositTitle', '单据标题', 'text', NULL,
       NULL, NULL, '预交金收据', 'flow', 20, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'deposit_receipt' AND e.element_code = 'depositTitle' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910007000003, 0, 'deposit_receipt', 'depositMetaRow', '交易信息', 'kvRow', 'deposit',
       '[{"label":"票据号","field":"ticketNo"},{"label":"时间","field":"tradeTime"},{"label":"业务","field":"bizTypeText"}]',
       NULL, NULL, 'flow', 30, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'deposit_receipt' AND e.element_code = 'depositMetaRow' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910007000004, 0, 'deposit_receipt', 'patientInfoRow', '患者信息', 'kvRow', 'patient',
       '[{"label":"姓名","field":"name"},{"label":"住院号","field":"encounterBusNo"},{"label":"病区床号","field":"wardBed"}]',
       NULL, NULL, 'flow', 40, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'deposit_receipt' AND e.element_code = 'patientInfoRow' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910007000005, 0, 'deposit_receipt', 'amountText', '金额', 'text', 'deposit.amount',
       NULL, NULL, NULL, 'flow', 50, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'deposit_receipt' AND e.element_code = 'amountText' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910007000006, 0, 'deposit_receipt', 'paymentMethod', '支付方式', 'text', 'payment.methodText',
       NULL, NULL, NULL, 'flow', 60, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'deposit_receipt' AND e.element_code = 'paymentMethod' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910007000007, 0, 'deposit_receipt', 'accountBalance', '账户余额', 'text', 'account.balance',
       NULL, NULL, NULL, 'flow', 70, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'deposit_receipt' AND e.element_code = 'accountBalance' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910007000008, 0, 'deposit_receipt', 'operatorName', '操作员', 'text', 'operator.name',
       NULL, NULL, NULL, 'flow', 80, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'deposit_receipt' AND e.element_code = 'operatorName' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910007000009, 0, 'deposit_receipt', 'printTime', '打印时间', 'text', 'system.printTime',
       NULL, NULL, NULL, 'flow', 90, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'deposit_receipt' AND e.element_code = 'printTime' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910007000010, 0, 'deposit_receipt', 'dividerLine', '分隔线', 'line', NULL,
       NULL, NULL, NULL, 'flow', 100, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'deposit_receipt' AND e.element_code = 'dividerLine' AND e.is_deleted = FALSE
);

UPDATE rpt_template
SET name = '预交金收据',
    pipeline = 'roll',
    dataset_code = 'deposit_receipt',
    layout_json = '{"pipeline":"roll","page":{"widthMm":80,"layoutMode":"flow"},"items":[{"elementCode":"storeName","visible":true,"style":{"align":"center","fontSizePt":11,"bold":true}},{"elementCode":"depositTitle","visible":true,"style":{"align":"center","fontSizePt":12,"bold":true}},{"elementCode":"depositMetaRow","visible":true},{"elementCode":"patientInfoRow","visible":true},{"elementCode":"amountText","visible":true,"style":{"fontSizePt":14,"bold":true}},{"elementCode":"paymentMethod","visible":true},{"elementCode":"accountBalance","visible":true},{"elementCode":"operatorName","visible":true},{"elementCode":"printTime","visible":true},{"elementCode":"dividerLine","visible":true}]}'
WHERE tenant_id = 0
  AND code = 'deposit_receipt'
  AND is_deleted = FALSE;

-- ========== V12__Seed_order_execute_sheet.sql ==========

-- order_execute_sheet 元素包 + 升级出厂模板（IP-08）

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910008000001, 0, 'order_execute_sheet', 'docTitle', '标题', 'staticTitle', NULL,
       NULL, NULL, '医嘱执行单', 'flow', 10, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'order_execute_sheet' AND e.element_code = 'docTitle' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910008000002, 0, 'order_execute_sheet', 'hospitalName', '医疗机构', 'text', 'hospital.name',
       NULL, NULL, NULL, 'flow', 20, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'order_execute_sheet' AND e.element_code = 'hospitalName' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910008000003, 0, 'order_execute_sheet', 'patientInfoRow', '患者信息', 'kvRow', 'patient',
       '[{"label":"姓名","field":"name"},{"label":"性别","field":"gender"},{"label":"年龄","field":"age"},{"label":"住院号","field":"encounterBusNo"},{"label":"病区床号","field":"wardBed"},{"label":"科室","field":"deptName"}]',
       NULL, NULL, 'flow', 30, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'order_execute_sheet' AND e.element_code = 'patientInfoRow' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910008000004, 0, 'order_execute_sheet', 'sheetMetaRow', '筛选条件', 'kvRow', 'sheet',
       '[{"label":"执行状态","field":"executeTabText"},{"label":"长临","field":"therapyTypeText"},{"label":"截止时间","field":"executionDeadline"}]',
       NULL, NULL, 'flow', 40, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'order_execute_sheet' AND e.element_code = 'sheetMetaRow' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910008000005, 0, 'order_execute_sheet', 'orderLineTable', '医嘱明细', 'table', 'order.lines',
       NULL,
       '[{"field":"typeLabel","header":"类型"},{"field":"content","header":"医嘱内容"},{"field":"dosage","header":"剂量"},{"field":"usageFreq","header":"用法频次"},{"field":"startStop","header":"开始/终止"},{"field":"planTimes","header":"计划执行时点"},{"field":"prescriberName","header":"开立医生"}]',
       NULL, 'flow', 50, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'order_execute_sheet' AND e.element_code = 'orderLineTable' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910008000006, 0, 'order_execute_sheet', 'printTime', '打印时间', 'text', 'system.printTime',
       NULL, NULL, NULL, 'flow', 60, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'order_execute_sheet' AND e.element_code = 'printTime' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910008000007, 0, 'order_execute_sheet', 'dividerLine', '分隔线', 'line', NULL,
       NULL, NULL, NULL, 'flow', 70, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'order_execute_sheet' AND e.element_code = 'dividerLine' AND e.is_deleted = FALSE
);

UPDATE rpt_template
SET name = '医嘱执行单（单患者）',
    pipeline = 'page',
    dataset_code = 'order_execute_sheet',
    layout_json = '{"pipeline":"page","page":{"size":"A4","orientation":"portrait","marginMm":[12,10,12,10],"layoutMode":"flow"},"items":[{"elementCode":"docTitle","visible":true,"style":{"fontSizePt":16,"align":"center","bold":true}},{"elementCode":"hospitalName","visible":true,"style":{"align":"center","fontSizePt":11}},{"elementCode":"patientInfoRow","visible":true},{"elementCode":"sheetMetaRow","visible":true},{"elementCode":"orderLineTable","visible":true},{"elementCode":"printTime","visible":true},{"elementCode":"dividerLine","visible":true}]}'
WHERE tenant_id = 0
  AND code = 'order_execute_sheet'
  AND is_deleted = FALSE;

-- ========== V13__Seed_inpatient_charge_summary.sql ==========

-- inpatient_charge_summary 元素包 + 升级出厂模板（IP-32）

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910009000001, 0, 'inpatient_charge_summary', 'docTitle', '标题', 'staticTitle', NULL,
       NULL, NULL, '住院费用总清单', 'flow', 10, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'inpatient_charge_summary' AND e.element_code = 'docTitle' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910009000002, 0, 'inpatient_charge_summary', 'hospitalName', '医疗机构', 'text', 'hospital.name',
       NULL, NULL, NULL, 'flow', 20, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'inpatient_charge_summary' AND e.element_code = 'hospitalName' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910009000003, 0, 'inpatient_charge_summary', 'patientInfoRow', '患者信息', 'kvRow', 'patient',
       '[{"label":"姓名","field":"name"},{"label":"床号","field":"bedNo"},{"label":"住院号","field":"inpatientNo"},{"label":"科室","field":"deptName"}]',
       NULL, NULL, 'flow', 30, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'inpatient_charge_summary' AND e.element_code = 'patientInfoRow' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910009000004, 0, 'inpatient_charge_summary', 'settleMetaRow', '结算信息', 'kvRow', 'settle',
       '[{"label":"结算时间","field":"settleTime"},{"label":"操作员","field":"operatorName"}]',
       NULL, NULL, 'flow', 40, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'inpatient_charge_summary' AND e.element_code = 'settleMetaRow' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910009000005, 0, 'inpatient_charge_summary', 'chargeDetailTable', '费用明细', 'table', 'charge.lines',
       NULL,
       '[{"field":"itemName","header":"项目"},{"field":"spec","header":"规格"},{"field":"quantity","header":"数量"},{"field":"unitPrice","header":"单价"},{"field":"amount","header":"金额"},{"field":"category","header":"类别"},{"field":"occurrenceTime","header":"发生时间"}]',
       NULL, 'flow', 50, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'inpatient_charge_summary' AND e.element_code = 'chargeDetailTable' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910009000006, 0, 'inpatient_charge_summary', 'summaryRow', '费用汇总', 'kvRow', 'summary',
       '[{"label":"费用合计","field":"totalAmount"},{"label":"预交金","field":"prepayAmount"},{"label":"差额提示","field":"balanceHint"}]',
       NULL, NULL, 'flow', 60, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'inpatient_charge_summary' AND e.element_code = 'summaryRow' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910009000007, 0, 'inpatient_charge_summary', 'printTime', '打印时间', 'text', 'system.printTime',
       NULL, NULL, NULL, 'flow', 70, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'inpatient_charge_summary' AND e.element_code = 'printTime' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910009000008, 0, 'inpatient_charge_summary', 'dividerLine', '分隔线', 'line', NULL,
       NULL, NULL, NULL, 'flow', 80, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'inpatient_charge_summary' AND e.element_code = 'dividerLine' AND e.is_deleted = FALSE
);

UPDATE rpt_template
SET name = '住院费用总清单',
    pipeline = 'page',
    dataset_code = 'inpatient_charge_summary',
    layout_json = '{"pipeline":"page","page":{"size":"A4","orientation":"portrait","marginMm":[12,10,12,10],"layoutMode":"flow"},"items":[{"elementCode":"docTitle","visible":true,"style":{"fontSizePt":16,"align":"center","bold":true}},{"elementCode":"hospitalName","visible":true,"style":{"align":"center","fontSizePt":11}},{"elementCode":"patientInfoRow","visible":true},{"elementCode":"settleMetaRow","visible":true},{"elementCode":"chargeDetailTable","visible":true},{"elementCode":"summaryRow","visible":true},{"elementCode":"printTime","visible":true},{"elementCode":"dividerLine","visible":true}]}'
WHERE tenant_id = 0
  AND code = 'inpatient_charge_summary'
  AND is_deleted = FALSE;

-- ========== V14__Seed_inpatient_invoice_overlay.sql ==========

-- inpatient_invoice_overlay 元素包 + 升级出厂模板（IP-31）

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, box_json, created_at, is_deleted
)
SELECT 910010000001, 0, 'inpatient_invoice_overlay', 'invoiceCode', '票据代码', 'text', 'inpatientInvoice.code',
       NULL, NULL, NULL, 'overlay', 10, FALSE, TRUE,
       '{"xMm":18,"yMm":18,"widthMm":40,"heightMm":5,"overflow":"clip"}',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'inpatient_invoice_overlay' AND e.element_code = 'invoiceCode' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, box_json, created_at, is_deleted
)
SELECT 910010000002, 0, 'inpatient_invoice_overlay', 'invoiceNo', '票据号码', 'text', 'inpatientInvoice.no',
       NULL, NULL, NULL, 'overlay', 20, TRUE, TRUE,
       '{"xMm":62,"yMm":18,"widthMm":45,"heightMm":5,"overflow":"clip"}',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'inpatient_invoice_overlay' AND e.element_code = 'invoiceNo' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, box_json, created_at, is_deleted
)
SELECT 910010000003, 0, 'inpatient_invoice_overlay', 'patientName', '患者姓名', 'text', 'patient.name',
       NULL, NULL, NULL, 'overlay', 30, TRUE, TRUE,
       '{"xMm":28,"yMm":38,"widthMm":28,"heightMm":5,"overflow":"clip"}',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'inpatient_invoice_overlay' AND e.element_code = 'patientName' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, box_json, created_at, is_deleted
)
SELECT 910010000004, 0, 'inpatient_invoice_overlay', 'inpatientNo', '住院号', 'text', 'patient.inpatientNo',
       NULL, NULL, NULL, 'overlay', 40, TRUE, TRUE,
       '{"xMm":72,"yMm":38,"widthMm":40,"heightMm":5,"overflow":"clip"}',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'inpatient_invoice_overlay' AND e.element_code = 'inpatientNo' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, box_json, created_at, is_deleted
)
SELECT 910010000005, 0, 'inpatient_invoice_overlay', 'chargeAmount', '金额（小写）', 'text', 'settlement.amount',
       NULL, NULL, NULL, 'overlay', 50, TRUE, TRUE,
       '{"xMm":28,"yMm":58,"widthMm":35,"heightMm":5,"overflow":"clip"}',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'inpatient_invoice_overlay' AND e.element_code = 'chargeAmount' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, box_json, created_at, is_deleted
)
SELECT 910010000006, 0, 'inpatient_invoice_overlay', 'chargeAmountChinese', '金额（大写）', 'text', 'settlement.amountChinese',
       NULL, NULL, NULL, 'overlay', 60, TRUE, TRUE,
       '{"xMm":28,"yMm":66,"widthMm":90,"heightMm":6,"overflow":"shrinkFont"}',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'inpatient_invoice_overlay' AND e.element_code = 'chargeAmountChinese' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, box_json, created_at, is_deleted
)
SELECT 910010000007, 0, 'inpatient_invoice_overlay', 'payerInfo', '科室床号', 'text', 'patient.payerInfo',
       NULL, NULL, NULL, 'overlay', 70, FALSE, TRUE,
       '{"xMm":28,"yMm":48,"widthMm":60,"heightMm":5,"overflow":"clip"}',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'inpatient_invoice_overlay' AND e.element_code = 'payerInfo' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, box_json, created_at, is_deleted
)
SELECT 910010000008, 0, 'inpatient_invoice_overlay', 'payMethodText', '支付方式', 'text', 'settlement.payMethodText',
       NULL, NULL, NULL, 'overlay', 80, FALSE, TRUE,
       '{"xMm":28,"yMm":76,"widthMm":30,"heightMm":5,"overflow":"clip"}',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'inpatient_invoice_overlay' AND e.element_code = 'payMethodText' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, box_json, created_at, is_deleted
)
SELECT 910010000009, 0, 'inpatient_invoice_overlay', 'operatorName', '收费员', 'text', 'operator.name',
       NULL, NULL, NULL, 'overlay', 90, FALSE, TRUE,
       '{"xMm":100,"yMm":175,"widthMm":35,"heightMm":5,"overflow":"clip"}',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'inpatient_invoice_overlay' AND e.element_code = 'operatorName' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, box_json, created_at, is_deleted
)
SELECT 910010000010, 0, 'inpatient_invoice_overlay', 'printDate', '开票日期', 'text', 'inpatientInvoice.date',
       NULL, NULL, NULL, 'overlay', 100, FALSE, TRUE,
       '{"xMm":100,"yMm":18,"widthMm":35,"heightMm":5,"overflow":"clip"}',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'inpatient_invoice_overlay' AND e.element_code = 'printDate' AND e.is_deleted = FALSE
);

UPDATE rpt_template
SET name = '住院收费票据（套打）',
    pipeline = 'page',
    dataset_code = 'inpatient_invoice_overlay',
    layout_json = '{"pipeline":"page","page":{"size":"A5","orientation":"portrait","layoutMode":"overlay","marginMm":[0,0,0,0],"overlay":{"printOffsetMm":[0,0]}},"items":[{"elementCode":"invoiceCode","visible":true,"box":{"xMm":18,"yMm":18,"widthMm":40,"heightMm":5}},{"elementCode":"invoiceNo","visible":true,"box":{"xMm":62,"yMm":18,"widthMm":45,"heightMm":5}},{"elementCode":"printDate","visible":true},{"elementCode":"patientName","visible":true,"box":{"xMm":28,"yMm":38,"widthMm":28,"heightMm":5}},{"elementCode":"inpatientNo","visible":true},{"elementCode":"payerInfo","visible":true},{"elementCode":"chargeAmount","visible":true},{"elementCode":"chargeAmountChinese","visible":true},{"elementCode":"payMethodText","visible":true},{"elementCode":"operatorName","visible":true}]}'
WHERE tenant_id = 0
  AND code = 'inpatient_invoice_overlay'
  AND is_deleted = FALSE;

-- ========== V15__Seed_insurance_settlement.sql ==========

-- insurance_settlement 元素包 + 升级出厂模板（IP-33）

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910011000001, 0, 'insurance_settlement', 'docTitle', '标题', 'staticTitle', NULL,
       NULL, NULL, '医保结算单', 'flow', 10, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'insurance_settlement' AND e.element_code = 'docTitle' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910011000002, 0, 'insurance_settlement', 'hospitalName', '医疗机构', 'text', 'hospital.name',
       NULL, NULL, NULL, 'flow', 20, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'insurance_settlement' AND e.element_code = 'hospitalName' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910011000003, 0, 'insurance_settlement', 'patientInfoRow', '患者信息', 'kvRow', 'patient',
       '[{"label":"姓名","field":"name"},{"label":"住院号","field":"inpatientNo"},{"label":"性别","field":"gender"},{"label":"年龄","field":"age"},{"label":"医保类型","field":"insuranceType"}]',
       NULL, NULL, 'flow', 30, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'insurance_settlement' AND e.element_code = 'patientInfoRow' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910011000004, 0, 'insurance_settlement', 'insuranceMetaRow', '医保结算信息', 'kvRow', 'insurance',
       '[{"label":"结算单号","field":"settleNo"},{"label":"就医ID","field":"mdtrtId"},{"label":"合同号","field":"contractNo"},{"label":"结算时间","field":"settleTime"},{"label":"清算状态","field":"clearedText"}]',
       NULL, NULL, 'flow', 40, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'insurance_settlement' AND e.element_code = 'insuranceMetaRow' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910011000005, 0, 'insurance_settlement', 'insuranceFeeRow', '费用分项', 'kvRow', 'insuranceFee',
       '[{"label":"费用总额","field":"totalAmount"},{"label":"统筹支付","field":"fundPayAmount"},{"label":"个账支付","field":"personalPayAmount"},{"label":"现金支付","field":"cashPayAmount"},{"label":"医保内费用","field":"insuranceInFee"},{"label":"医保外自费","field":"insuranceOutFee"}]',
       NULL, NULL, 'flow', 50, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'insurance_settlement' AND e.element_code = 'insuranceFeeRow' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910011000006, 0, 'insurance_settlement', 'settlementMetaRow', 'HIS结算', 'kvRow', 'settlement',
       '[{"label":"支付流水","field":"paymentNo"},{"label":"支付方式","field":"payMethodText"}]',
       NULL, NULL, 'flow', 60, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'insurance_settlement' AND e.element_code = 'settlementMetaRow' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910011000007, 0, 'insurance_settlement', 'operatorName', '操作员', 'text', 'operator.name',
       NULL, NULL, NULL, 'flow', 70, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'insurance_settlement' AND e.element_code = 'operatorName' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910011000008, 0, 'insurance_settlement', 'printTime', '打印时间', 'text', 'system.printTime',
       NULL, NULL, NULL, 'flow', 80, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'insurance_settlement' AND e.element_code = 'printTime' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910011000009, 0, 'insurance_settlement', 'dividerLine', '分隔线', 'line', NULL,
       NULL, NULL, NULL, 'flow', 90, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'insurance_settlement' AND e.element_code = 'dividerLine' AND e.is_deleted = FALSE
);

UPDATE rpt_template
SET name = '医保结算单',
    pipeline = 'page',
    dataset_code = 'insurance_settlement',
    layout_json = '{"pipeline":"page","page":{"size":"A4","orientation":"portrait","marginMm":[12,10,12,10],"layoutMode":"flow"},"items":[{"elementCode":"docTitle","visible":true,"style":{"fontSizePt":16,"align":"center","bold":true}},{"elementCode":"hospitalName","visible":true,"style":{"align":"center","fontSizePt":11}},{"elementCode":"patientInfoRow","visible":true},{"elementCode":"insuranceMetaRow","visible":true},{"elementCode":"insuranceFeeRow","visible":true},{"elementCode":"settlementMetaRow","visible":true},{"elementCode":"operatorName","visible":true},{"elementCode":"printTime","visible":true},{"elementCode":"dividerLine","visible":true}]}'
WHERE tenant_id = 0
  AND code = 'insurance_settlement'
  AND is_deleted = FALSE;

-- ========== V16__Seed_outpatient_invoice_overlay.sql ==========

-- outpatient_invoice_overlay 元素包 + 升级出厂模板（OP-03）

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, box_json, created_at, is_deleted
)
SELECT 910012000001, 0, 'outpatient_invoice_overlay', 'invoiceCode', '票据代码', 'text', 'invoice.code',
       NULL, NULL, NULL, 'overlay', 10, FALSE, TRUE,
       '{"xMm":18,"yMm":18,"widthMm":40,"heightMm":5,"overflow":"clip"}',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'outpatient_invoice_overlay' AND e.element_code = 'invoiceCode' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, box_json, created_at, is_deleted
)
SELECT 910012000002, 0, 'outpatient_invoice_overlay', 'invoiceNo', '票据号码', 'text', 'invoice.no',
       NULL, NULL, NULL, 'overlay', 20, TRUE, TRUE,
       '{"xMm":62,"yMm":18,"widthMm":45,"heightMm":5,"overflow":"clip"}',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'outpatient_invoice_overlay' AND e.element_code = 'invoiceNo' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, box_json, created_at, is_deleted
)
SELECT 910012000003, 0, 'outpatient_invoice_overlay', 'patientName', '患者姓名', 'text', 'patient.name',
       NULL, NULL, NULL, 'overlay', 30, TRUE, TRUE,
       '{"xMm":28,"yMm":38,"widthMm":28,"heightMm":5,"overflow":"clip"}',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'outpatient_invoice_overlay' AND e.element_code = 'patientName' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, box_json, created_at, is_deleted
)
SELECT 910012000004, 0, 'outpatient_invoice_overlay', 'encounterBusNo', '就诊号', 'text', 'encounter.busNo',
       NULL, NULL, NULL, 'overlay', 40, TRUE, TRUE,
       '{"xMm":72,"yMm":38,"widthMm":40,"heightMm":5,"overflow":"clip"}',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'outpatient_invoice_overlay' AND e.element_code = 'encounterBusNo' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, box_json, created_at, is_deleted
)
SELECT 910012000005, 0, 'outpatient_invoice_overlay', 'chargeAmount', '金额（小写）', 'text', 'invoice.amount',
       NULL, NULL, NULL, 'overlay', 50, TRUE, TRUE,
       '{"xMm":28,"yMm":58,"widthMm":35,"heightMm":5,"overflow":"clip"}',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'outpatient_invoice_overlay' AND e.element_code = 'chargeAmount' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, box_json, created_at, is_deleted
)
SELECT 910012000006, 0, 'outpatient_invoice_overlay', 'chargeAmountChinese', '金额（大写）', 'text', 'invoice.amountChinese',
       NULL, NULL, NULL, 'overlay', 60, TRUE, TRUE,
       '{"xMm":28,"yMm":66,"widthMm":90,"heightMm":6,"overflow":"shrinkFont"}',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'outpatient_invoice_overlay' AND e.element_code = 'chargeAmountChinese' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, box_json, created_at, is_deleted
)
SELECT 910012000007, 0, 'outpatient_invoice_overlay', 'payerInfo', '交款人信息', 'text', 'patient.payerInfo',
       NULL, NULL, NULL, 'overlay', 70, FALSE, TRUE,
       '{"xMm":28,"yMm":48,"widthMm":60,"heightMm":5,"overflow":"clip"}',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'outpatient_invoice_overlay' AND e.element_code = 'payerInfo' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, box_json, created_at, is_deleted
)
SELECT 910012000008, 0, 'outpatient_invoice_overlay', 'payMethodText', '支付方式', 'text', 'payment.methodText',
       NULL, NULL, NULL, 'overlay', 80, FALSE, TRUE,
       '{"xMm":28,"yMm":76,"widthMm":30,"heightMm":5,"overflow":"clip"}',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'outpatient_invoice_overlay' AND e.element_code = 'payMethodText' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, box_json, created_at, is_deleted
)
SELECT 910012000009, 0, 'outpatient_invoice_overlay', 'operatorName', '收费员', 'text', 'operator.name',
       NULL, NULL, NULL, 'overlay', 90, FALSE, TRUE,
       '{"xMm":100,"yMm":175,"widthMm":35,"heightMm":5,"overflow":"clip"}',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'outpatient_invoice_overlay' AND e.element_code = 'operatorName' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, box_json, created_at, is_deleted
)
SELECT 910012000010, 0, 'outpatient_invoice_overlay', 'printDate', '开票日期', 'text', 'invoice.date',
       NULL, NULL, NULL, 'overlay', 100, FALSE, TRUE,
       '{"xMm":100,"yMm":18,"widthMm":35,"heightMm":5,"overflow":"clip"}',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'outpatient_invoice_overlay' AND e.element_code = 'printDate' AND e.is_deleted = FALSE
);

UPDATE rpt_template
SET name = '门诊发票/票据用户联（套打）',
    pipeline = 'page',
    dataset_code = 'outpatient_invoice_overlay',
    layout_json = '{"pipeline":"page","page":{"size":"A5","orientation":"portrait","layoutMode":"overlay","marginMm":[0,0,0,0],"overlay":{"printOffsetMm":[0,0]}},"items":[{"elementCode":"invoiceCode","visible":true,"box":{"xMm":18,"yMm":18,"widthMm":40,"heightMm":5}},{"elementCode":"invoiceNo","visible":true,"box":{"xMm":62,"yMm":18,"widthMm":45,"heightMm":5}},{"elementCode":"printDate","visible":true},{"elementCode":"patientName","visible":true,"box":{"xMm":28,"yMm":38,"widthMm":28,"heightMm":5}},{"elementCode":"encounterBusNo","visible":true},{"elementCode":"payerInfo","visible":true},{"elementCode":"chargeAmount","visible":true},{"elementCode":"chargeAmountChinese","visible":true},{"elementCode":"payMethodText","visible":true},{"elementCode":"operatorName","visible":true}]}'
WHERE tenant_id = 0
  AND code = 'outpatient_invoice_overlay'
  AND is_deleted = FALSE;

-- ========== V17__Seed_outpatient_charge_list_and_refund_receipt.sql ==========

-- outpatient_charge_list（OP-04）+ outpatient_refund_receipt（OP-07）

-- ========== outpatient_charge_list ==========

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910013000001, 0, 'outpatient_charge_list', 'docTitle', '标题', 'staticTitle', NULL,
       NULL, NULL, '门诊费用清单', 'flow', 10, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'outpatient_charge_list' AND e.element_code = 'docTitle' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910013000002, 0, 'outpatient_charge_list', 'hospitalName', '医疗机构', 'text', 'hospital.name',
       NULL, NULL, NULL, 'flow', 20, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'outpatient_charge_list' AND e.element_code = 'hospitalName' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910013000003, 0, 'outpatient_charge_list', 'patientInfoRow', '患者信息', 'kvRow', 'patient',
       '[{"label":"姓名","field":"name"},{"label":"患者号","field":"busNo"}]',
       NULL, NULL, 'flow', 30, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'outpatient_charge_list' AND e.element_code = 'patientInfoRow' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910013000004, 0, 'outpatient_charge_list', 'encounterMetaRow', '就诊信息', 'kvRow', 'encounter',
       '[{"label":"就诊号","field":"busNo"}]',
       NULL, NULL, 'flow', 40, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'outpatient_charge_list' AND e.element_code = 'encounterMetaRow' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910013000005, 0, 'outpatient_charge_list', 'chargeMetaRow', '结算信息', 'kvRow', 'charge',
       '[{"label":"支付单号","field":"busNo"},{"label":"结算时间","field":"occurrenceTime"}]',
       NULL, NULL, 'flow', 50, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'outpatient_charge_list' AND e.element_code = 'chargeMetaRow' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910013000006, 0, 'outpatient_charge_list', 'chargeDetailTable', '费用明细', 'table', 'charge.lines',
       NULL,
       '[{"field":"itemName","header":"项目"},{"field":"quantity","header":"数量"},{"field":"unitText","header":"单位"},{"field":"unitPrice","header":"单价"},{"field":"lineAmount","header":"金额"}]',
       NULL, 'flow', 60, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'outpatient_charge_list' AND e.element_code = 'chargeDetailTable' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910013000007, 0, 'outpatient_charge_list', 'paymentSummaryRow', '支付汇总', 'kvRow', 'payment',
       '[{"label":"支付方式","field":"methodText"},{"label":"实收","field":"paidAmount"}]',
       NULL, NULL, 'flow', 70, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'outpatient_charge_list' AND e.element_code = 'paymentSummaryRow' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910013000010, 0, 'outpatient_charge_list', 'totalAmount', '合计', 'text', 'charge.totalAmount',
       NULL, NULL, '合计：', 'flow', 75, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'outpatient_charge_list' AND e.element_code = 'totalAmount' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910013000008, 0, 'outpatient_charge_list', 'operatorName', '收费员', 'text', 'operator.name',
       NULL, NULL, NULL, 'flow', 80, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'outpatient_charge_list' AND e.element_code = 'operatorName' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910013000009, 0, 'outpatient_charge_list', 'printTime', '打印时间', 'text', 'system.printTime',
       NULL, NULL, NULL, 'flow', 90, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'outpatient_charge_list' AND e.element_code = 'printTime' AND e.is_deleted = FALSE
);

UPDATE rpt_template
SET name = '门诊费用清单',
    pipeline = 'page',
    dataset_code = 'outpatient_charge_list',
    layout_json = '{"pipeline":"page","page":{"size":"A4","orientation":"portrait","marginMm":[12,10,12,10],"layoutMode":"flow"},"items":[{"elementCode":"docTitle","visible":true,"style":{"fontSizePt":16,"align":"center","bold":true}},{"elementCode":"hospitalName","visible":true,"style":{"align":"center","fontSizePt":11}},{"elementCode":"patientInfoRow","visible":true},{"elementCode":"encounterMetaRow","visible":true},{"elementCode":"chargeMetaRow","visible":true},{"elementCode":"chargeDetailTable","visible":true},{"elementCode":"paymentSummaryRow","visible":true},{"elementCode":"totalAmount","visible":true},{"elementCode":"operatorName","visible":true},{"elementCode":"printTime","visible":true}]}'
WHERE tenant_id = 0
  AND code = 'outpatient_charge_list'
  AND is_deleted = FALSE;

-- ========== outpatient_refund_receipt ==========

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910014000001, 0, 'outpatient_refund_receipt', 'rollTitle', '标题', 'staticTitle', NULL,
       NULL, NULL, '门诊退费凭条', 'flow', 10, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'outpatient_refund_receipt' AND e.element_code = 'rollTitle' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910014000002, 0, 'outpatient_refund_receipt', 'storeName', '机构名', 'text', 'hospital.name',
       NULL, NULL, NULL, 'flow', 20, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'outpatient_refund_receipt' AND e.element_code = 'storeName' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910014000003, 0, 'outpatient_refund_receipt', 'refundMetaRow', '退费信息', 'kvRow', 'refund',
       '[{"label":"原支付单号","field":"originalPaymentNo"},{"label":"退费金额","field":"refundAmount"}]',
       NULL, NULL, 'flow', 30, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'outpatient_refund_receipt' AND e.element_code = 'refundMetaRow' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910014000004, 0, 'outpatient_refund_receipt', 'chargeMetaText', '结算信息', 'kvRow', 'charge',
       '[{"label":"退费单号","field":"busNo"},{"label":"时间","field":"occurrenceTime"}]',
       NULL, NULL, 'flow', 40, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'outpatient_refund_receipt' AND e.element_code = 'chargeMetaText' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910014000005, 0, 'outpatient_refund_receipt', 'chargeLineTable', '退费明细', 'table', 'charge.lines',
       NULL,
       '[{"field":"itemName","header":"项目"},{"field":"quantity","header":"数量"},{"field":"unitPrice","header":"单价"},{"field":"lineAmount","header":"金额"}]',
       NULL, 'flow', 50, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'outpatient_refund_receipt' AND e.element_code = 'chargeLineTable' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910014000006, 0, 'outpatient_refund_receipt', 'totalAmount', '合计', 'text', 'charge.totalAmount',
       NULL, NULL, '合计：', 'flow', 60, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'outpatient_refund_receipt' AND e.element_code = 'totalAmount' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910014000007, 0, 'outpatient_refund_receipt', 'dividerLine', '分隔线', 'line', NULL,
       NULL, NULL, NULL, 'flow', 70, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'outpatient_refund_receipt' AND e.element_code = 'dividerLine' AND e.is_deleted = FALSE
);

UPDATE rpt_template
SET name = '门诊退费凭条',
    pipeline = 'roll',
    dataset_code = 'outpatient_refund_receipt',
    layout_json = '{"pipeline":"roll","page":{"widthMm":80,"layoutMode":"flow"},"items":[{"elementCode":"rollTitle","visible":true},{"elementCode":"storeName","visible":true},{"elementCode":"refundMetaRow","visible":true},{"elementCode":"chargeMetaText","visible":true},{"elementCode":"chargeLineTable","visible":true},{"elementCode":"totalAmount","visible":true},{"elementCode":"dividerLine","visible":true}]}'
WHERE tenant_id = 0
  AND code = 'outpatient_refund_receipt'
  AND is_deleted = FALSE;

-- ========== V18__Seed_registration_receipt_and_ward_dispense_summary.sql ==========

-- registration_receipt（OP-01）+ ward_dispense_summary（IP-15）

-- ========== registration_receipt ==========

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910018000001, 0, 'registration_receipt', 'rollTitle', '单据标题', 'text', NULL,
       NULL, NULL, '挂号小票', 'flow', 10, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'registration_receipt' AND e.element_code = 'rollTitle' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910018000002, 0, 'registration_receipt', 'storeName', '机构名', 'text', 'hospital.name',
       NULL, NULL, NULL, 'flow', 20, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'registration_receipt' AND e.element_code = 'storeName' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910018000003, 0, 'registration_receipt', 'registrationMetaRow', '挂号信息', 'kvRow', 'registration',
       '[{"label":"科室","field":"departmentName"},{"label":"医生","field":"doctorName"},{"label":"号别","field":"serviceTypeName"},{"label":"序号","field":"queueNo"},{"label":"挂号时间","field":"registerTime"}]',
       NULL, NULL, 'flow', 30, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'registration_receipt' AND e.element_code = 'registrationMetaRow' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910018000004, 0, 'registration_receipt', 'patientInfoRow', '患者信息', 'kvRow', 'patient',
       '[{"label":"姓名","field":"name"},{"label":"患者号","field":"busNo"}]',
       NULL, NULL, 'flow', 40, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'registration_receipt' AND e.element_code = 'patientInfoRow' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910018000005, 0, 'registration_receipt', 'chargeMetaRow', '结算信息', 'kvRow', 'charge',
       '[{"label":"支付单号","field":"busNo"},{"label":"结算时间","field":"occurrenceTime"}]',
       NULL, NULL, 'flow', 50, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'registration_receipt' AND e.element_code = 'chargeMetaRow' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910018000006, 0, 'registration_receipt', 'chargeLineTable', '费用明细', 'table', 'charge.lines',
       NULL,
       '[{"field":"itemName","header":"项目"},{"field":"quantity","header":"数量"},{"field":"unitText","header":"单位"},{"field":"lineAmount","header":"金额"}]',
       NULL, 'flow', 60, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'registration_receipt' AND e.element_code = 'chargeLineTable' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910018000007, 0, 'registration_receipt', 'totalAmount', '合计', 'text', 'charge.totalAmount',
       NULL, NULL, '合计：', 'flow', 70, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'registration_receipt' AND e.element_code = 'totalAmount' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910018000008, 0, 'registration_receipt', 'paymentMethod', '支付方式', 'text', 'payment.methodText',
       NULL, NULL, NULL, 'flow', 80, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'registration_receipt' AND e.element_code = 'paymentMethod' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910018000009, 0, 'registration_receipt', 'operatorName', '操作员', 'text', 'operator.name',
       NULL, NULL, NULL, 'flow', 90, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'registration_receipt' AND e.element_code = 'operatorName' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910018000010, 0, 'registration_receipt', 'printTime', '打印时间', 'text', 'system.printTime',
       NULL, NULL, NULL, 'flow', 100, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'registration_receipt' AND e.element_code = 'printTime' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910018000011, 0, 'registration_receipt', 'dividerLine', '分隔线', 'line', NULL,
       NULL, NULL, NULL, 'flow', 110, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'registration_receipt' AND e.element_code = 'dividerLine' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, box_json, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910018000012, 0, 'registration_receipt', 'outpatientNoQrcode', '门诊号二维码', 'qrcode', 'patient.outpatientNo',
       NULL, NULL, NULL, '{"xMm":22,"yMm":120,"widthMm":36,"heightMm":36}', 'flow', 105, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'registration_receipt' AND e.element_code = 'outpatientNoQrcode' AND e.is_deleted = FALSE
);

UPDATE rpt_template
SET name = '挂号小票',
    pipeline = 'roll',
    dataset_code = 'registration_receipt',
    layout_json = '{"pipeline":"roll","page":{"widthMm":80,"layoutMode":"flow"},"items":[{"elementCode":"rollTitle","visible":true},{"elementCode":"storeName","visible":true},{"elementCode":"registrationMetaRow","visible":true},{"elementCode":"patientInfoRow","visible":true},{"elementCode":"chargeMetaRow","visible":true},{"elementCode":"chargeLineTable","visible":true},{"elementCode":"totalAmount","visible":true},{"elementCode":"paymentMethod","visible":true},{"elementCode":"operatorName","visible":true},{"elementCode":"printTime","visible":true},{"elementCode":"outpatientNoQrcode","visible":true,"box":{"xMm":22,"yMm":120,"widthMm":36,"heightMm":36}},{"elementCode":"dividerLine","visible":true}]}'
WHERE tenant_id = 0
  AND code = 'registration_receipt'
  AND is_deleted = FALSE;

-- ========== ward_dispense_summary ==========

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910019000001, 0, 'ward_dispense_summary', 'docTitle', '标题', 'staticTitle', NULL,
       NULL, NULL, '病区发药汇总单', 'flow', 10, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'ward_dispense_summary' AND e.element_code = 'docTitle' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910019000002, 0, 'ward_dispense_summary', 'hospitalName', '医疗机构', 'text', 'hospital.name',
       NULL, NULL, NULL, 'flow', 20, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'ward_dispense_summary' AND e.element_code = 'hospitalName' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910019000003, 0, 'ward_dispense_summary', 'summaryMetaRow', '汇总信息', 'kvRow', 'summary',
       '[{"label":"汇总单号","field":"summaryNo"},{"label":"申请时间","field":"applyTime"},{"label":"药房","field":"pharmacyName"},{"label":"患者数","field":"patientCount"},{"label":"明细行数","field":"lineCount"}]',
       NULL, NULL, 'flow', 30, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'ward_dispense_summary' AND e.element_code = 'summaryMetaRow' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910019000004, 0, 'ward_dispense_summary', 'dispenseDetailTable', '发药明细', 'table', 'summary.lines',
       NULL,
       '[{"field":"bedNo","header":"床号"},{"field":"patientName","header":"姓名"},{"field":"encounterBusNo","header":"住院号"},{"field":"drugName","header":"药品"},{"field":"quantity","header":"数量"},{"field":"unitText","header":"单位"},{"field":"orderClass","header":"长临"},{"field":"pharmacyName","header":"药房"}]',
       NULL, 'flow', 40, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'ward_dispense_summary' AND e.element_code = 'dispenseDetailTable' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910019000005, 0, 'ward_dispense_summary', 'printTime', '打印时间', 'text', 'system.printTime',
       NULL, NULL, NULL, 'flow', 50, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'ward_dispense_summary' AND e.element_code = 'printTime' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910019000006, 0, 'ward_dispense_summary', 'dividerLine', '分隔线', 'line', NULL,
       NULL, NULL, NULL, 'flow', 60, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'ward_dispense_summary' AND e.element_code = 'dividerLine' AND e.is_deleted = FALSE
);

UPDATE rpt_template
SET name = '病区发药汇总单',
    pipeline = 'page',
    dataset_code = 'ward_dispense_summary',
    layout_json = '{"pipeline":"page","page":{"size":"A4","orientation":"portrait","marginMm":[12,10,12,10]},"items":[{"elementCode":"docTitle","visible":true},{"elementCode":"hospitalName","visible":true,"style":{"align":"center","fontSizePt":11}},{"elementCode":"summaryMetaRow","visible":true},{"elementCode":"dispenseDetailTable","visible":true},{"elementCode":"printTime","visible":true},{"elementCode":"dividerLine","visible":true}]}'
WHERE tenant_id = 0
  AND code = 'ward_dispense_summary'
  AND is_deleted = FALSE;

-- ========== V19__Seed_iv_infusion_execute_and_medication_labels.sql ==========

-- iv_infusion_execute_sheet（IP-14/OP-28）+ injection_label / oral_drug_label 升级

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910019000001, 0, 'iv_infusion_execute_sheet', 'docTitle', '标题', 'staticTitle', NULL,
       NULL, NULL, '输液执行单', 'flow', 10, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e WHERE e.id = 910019000001
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910019000002, 0, 'iv_infusion_execute_sheet', 'hospitalName', '医疗机构', 'text', 'hospital.name',
       NULL, NULL, NULL, 'flow', 20, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e WHERE e.id = 910019000002
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910019000003, 0, 'iv_infusion_execute_sheet', 'patientInfoRow', '患者信息', 'kvRow', 'patient',
       '[{"label":"姓名","field":"name"},{"label":"性别","field":"gender"},{"label":"年龄","field":"age"},{"label":"住院号","field":"encounterBusNo"},{"label":"病区床号","field":"wardBed"},{"label":"科室","field":"deptName"}]',
       NULL, NULL, 'flow', 30, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e WHERE e.id = 910019000003
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910019000004, 0, 'iv_infusion_execute_sheet', 'sheetMetaRow', '筛选条件', 'kvRow', 'sheet',
       '[{"label":"执行状态","field":"executeTabText"},{"label":"长临","field":"therapyTypeText"},{"label":"截止时间","field":"executionDeadline"}]',
       NULL, NULL, 'flow', 40, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e WHERE e.id = 910019000004
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910019000005, 0, 'iv_infusion_execute_sheet', 'orderLineTable', '输液医嘱', 'table', 'order.lines',
       NULL,
       '[{"field":"typeLabel","header":"类型"},{"field":"content","header":"医嘱内容"},{"field":"dosage","header":"剂量"},{"field":"usageFreq","header":"用法频次"},{"field":"startStop","header":"开始/终止"},{"field":"planTimes","header":"计划执行时点"},{"field":"prescriberName","header":"开立医生"}]',
       NULL, 'flow', 50, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e WHERE e.id = 910019000005
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910019000006, 0, 'iv_infusion_execute_sheet', 'printTime', '打印时间', 'text', 'system.printTime',
       NULL, NULL, NULL, 'flow', 60, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e WHERE e.id = 910019000006
);

UPDATE rpt_template
SET name = '输液执行单/输液卡',
    pipeline = 'page',
    dataset_code = 'iv_infusion_execute_sheet',
    layout_json = '{"pipeline":"page","page":{"size":"A4","orientation":"portrait","marginMm":[12,10,12,10],"layoutMode":"flow"},"items":[{"elementCode":"docTitle","visible":true,"style":{"fontSizePt":16,"align":"center","bold":true}},{"elementCode":"hospitalName","visible":true,"style":{"align":"center","fontSizePt":11}},{"elementCode":"patientInfoRow","visible":true},{"elementCode":"sheetMetaRow","visible":true},{"elementCode":"orderLineTable","visible":true},{"elementCode":"printTime","visible":true}]}'
WHERE tenant_id = 0
  AND code = 'iv_infusion_execute_sheet'
  AND is_deleted = FALSE;

UPDATE rpt_template
SET name = '注射用药标签',
    pipeline = 'roll',
    dataset_code = 'iv_infusion_label',
    layout_json = '{"pipeline":"roll","page":{"widthMm":50,"layoutMode":"flow","marginMm":[2,2,2,2]},"items":[{"elementCode":"patientName","visible":true},{"elementCode":"patientBed","visible":true},{"elementCode":"drugGroupTitle","visible":true},{"elementCode":"drugLines","visible":true},{"elementCode":"routeFrequency","visible":true},{"elementCode":"planTime","visible":true},{"elementCode":"executeBarcode","visible":true},{"elementCode":"dividerLine","visible":true}]}'
WHERE tenant_id = 0
  AND code = 'injection_label'
  AND is_deleted = FALSE;

UPDATE rpt_template
SET name = '口服药标签/服药单',
    pipeline = 'roll',
    dataset_code = 'iv_infusion_label',
    layout_json = '{"pipeline":"roll","page":{"widthMm":50,"layoutMode":"flow","marginMm":[2,2,2,2]},"items":[{"elementCode":"patientName","visible":true},{"elementCode":"patientBed","visible":true},{"elementCode":"drugGroupTitle","visible":true},{"elementCode":"drugLines","visible":true},{"elementCode":"routeFrequency","visible":true},{"elementCode":"planTime","visible":true},{"elementCode":"executeBarcode","visible":true},{"elementCode":"dividerLine","visible":true}]}'
WHERE tenant_id = 0
  AND code = 'oral_drug_label'
  AND is_deleted = FALSE;

-- ========== V20__Seed_admission_discharge_and_apply_forms.sql ==========

-- admission_certificate / discharge_certificate / lab_apply_form / exam_apply_form

-- ========== admission_certificate ==========
INSERT INTO rpt_print_element_def (id, tenant_id, pack_code, element_code, label, render_type, bind_path, kv_items_json, column_defs_json, default_label, layout_mode, sort_order, required, enabled, created_at, is_deleted)
SELECT 910020001001, 0, 'admission_certificate', 'docTitle', '标题', 'staticTitle', NULL, NULL, NULL, '入院证', 'flow', 10, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM rpt_print_element_def e WHERE e.tenant_id = 0 AND e.pack_code = 'admission_certificate' AND e.element_code = 'docTitle' AND e.is_deleted = FALSE);

INSERT INTO rpt_print_element_def (id, tenant_id, pack_code, element_code, label, render_type, bind_path, kv_items_json, column_defs_json, default_label, layout_mode, sort_order, required, enabled, created_at, is_deleted)
SELECT 910020001002, 0, 'admission_certificate', 'hospitalName', '医疗机构', 'text', 'hospital.name', NULL, NULL, NULL, 'flow', 20, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM rpt_print_element_def e WHERE e.tenant_id = 0 AND e.pack_code = 'admission_certificate' AND e.element_code = 'hospitalName' AND e.is_deleted = FALSE);

INSERT INTO rpt_print_element_def (id, tenant_id, pack_code, element_code, label, render_type, bind_path, kv_items_json, column_defs_json, default_label, layout_mode, sort_order, required, enabled, created_at, is_deleted)
SELECT 910020001003, 0, 'admission_certificate', 'patientInfoRow', '患者信息', 'kvRow', 'patient',
'[{"label":"姓名","field":"name"},{"label":"性别","field":"gender"},{"label":"年龄","field":"age"},{"label":"住院号","field":"encounterBusNo"}]', NULL, NULL, 'flow', 30, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM rpt_print_element_def e WHERE e.tenant_id = 0 AND e.pack_code = 'admission_certificate' AND e.element_code = 'patientInfoRow' AND e.is_deleted = FALSE);

INSERT INTO rpt_print_element_def (id, tenant_id, pack_code, element_code, label, render_type, bind_path, kv_items_json, column_defs_json, default_label, layout_mode, sort_order, required, enabled, created_at, is_deleted)
SELECT 910020001004, 0, 'admission_certificate', 'certificateMetaRow', '入出院信息', 'kvRow', 'encounter',
'[{"label":"科室","field":"deptName"},{"label":"病区","field":"wardName"},{"label":"床位","field":"bedName"},{"label":"入院时间","field":"admissionTime"},{"label":"出院时间","field":"dischargeTime"},{"label":"主治医生","field":"attendingDoctorName"},{"label":"责任护士","field":"primaryNurseName"},{"label":"状态","field":"statusText"}]', NULL, NULL, 'flow', 40, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM rpt_print_element_def e WHERE e.tenant_id = 0 AND e.pack_code = 'admission_certificate' AND e.element_code = 'certificateMetaRow' AND e.is_deleted = FALSE);

INSERT INTO rpt_print_element_def (id, tenant_id, pack_code, element_code, label, render_type, bind_path, kv_items_json, column_defs_json, default_label, layout_mode, sort_order, required, enabled, created_at, is_deleted)
SELECT 910020001005, 0, 'admission_certificate', 'diagnosisText', '诊断', 'text', 'encounter.diagnosisSummary', NULL, NULL, NULL, 'flow', 50, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM rpt_print_element_def e WHERE e.tenant_id = 0 AND e.pack_code = 'admission_certificate' AND e.element_code = 'diagnosisText' AND e.is_deleted = FALSE);

INSERT INTO rpt_print_element_def (id, tenant_id, pack_code, element_code, label, render_type, bind_path, kv_items_json, column_defs_json, default_label, layout_mode, sort_order, required, enabled, created_at, is_deleted)
SELECT 910020001006, 0, 'admission_certificate', 'printTime', '打印时间', 'text', 'system.printTime', NULL, NULL, NULL, 'flow', 60, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM rpt_print_element_def e WHERE e.tenant_id = 0 AND e.pack_code = 'admission_certificate' AND e.element_code = 'printTime' AND e.is_deleted = FALSE);

UPDATE rpt_template SET name = '入院证', pipeline = 'page', dataset_code = 'admission_certificate',
layout_json = '{"pipeline":"page","page":{"size":"A4","orientation":"portrait","marginMm":[12,10,12,10],"layoutMode":"flow"},"items":[{"elementCode":"docTitle","visible":true,"style":{"fontSizePt":16,"align":"center","bold":true}},{"elementCode":"hospitalName","visible":true,"style":{"align":"center","fontSizePt":11}},{"elementCode":"patientInfoRow","visible":true},{"elementCode":"certificateMetaRow","visible":true},{"elementCode":"diagnosisText","visible":true},{"elementCode":"printTime","visible":true}]}'
WHERE tenant_id = 0 AND code = 'admission_certificate' AND is_deleted = FALSE;

-- ========== discharge_certificate ==========
INSERT INTO rpt_print_element_def (id, tenant_id, pack_code, element_code, label, render_type, bind_path, kv_items_json, column_defs_json, default_label, layout_mode, sort_order, required, enabled, created_at, is_deleted)
SELECT 910020002001, 0, 'discharge_certificate', 'docTitle', '标题', 'staticTitle', NULL, NULL, NULL, '出院证', 'flow', 10, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM rpt_print_element_def e WHERE e.tenant_id = 0 AND e.pack_code = 'discharge_certificate' AND e.element_code = 'docTitle' AND e.is_deleted = FALSE);

INSERT INTO rpt_print_element_def (id, tenant_id, pack_code, element_code, label, render_type, bind_path, kv_items_json, column_defs_json, default_label, layout_mode, sort_order, required, enabled, created_at, is_deleted)
SELECT 910020002002, 0, 'discharge_certificate', 'hospitalName', '医疗机构', 'text', 'hospital.name', NULL, NULL, NULL, 'flow', 20, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM rpt_print_element_def e WHERE e.tenant_id = 0 AND e.pack_code = 'discharge_certificate' AND e.element_code = 'hospitalName' AND e.is_deleted = FALSE);

INSERT INTO rpt_print_element_def (id, tenant_id, pack_code, element_code, label, render_type, bind_path, kv_items_json, column_defs_json, default_label, layout_mode, sort_order, required, enabled, created_at, is_deleted)
SELECT 910020002003, 0, 'discharge_certificate', 'patientInfoRow', '患者信息', 'kvRow', 'patient',
'[{"label":"姓名","field":"name"},{"label":"性别","field":"gender"},{"label":"年龄","field":"age"},{"label":"住院号","field":"encounterBusNo"}]', NULL, NULL, 'flow', 30, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM rpt_print_element_def e WHERE e.tenant_id = 0 AND e.pack_code = 'discharge_certificate' AND e.element_code = 'patientInfoRow' AND e.is_deleted = FALSE);

INSERT INTO rpt_print_element_def (id, tenant_id, pack_code, element_code, label, render_type, bind_path, kv_items_json, column_defs_json, default_label, layout_mode, sort_order, required, enabled, created_at, is_deleted)
SELECT 910020002004, 0, 'discharge_certificate', 'certificateMetaRow', '入出院信息', 'kvRow', 'encounter',
'[{"label":"科室","field":"deptName"},{"label":"病区","field":"wardName"},{"label":"床位","field":"bedName"},{"label":"入院时间","field":"admissionTime"},{"label":"出院时间","field":"dischargeTime"},{"label":"主治医生","field":"attendingDoctorName"},{"label":"责任护士","field":"primaryNurseName"},{"label":"状态","field":"statusText"}]', NULL, NULL, 'flow', 40, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM rpt_print_element_def e WHERE e.tenant_id = 0 AND e.pack_code = 'discharge_certificate' AND e.element_code = 'certificateMetaRow' AND e.is_deleted = FALSE);

INSERT INTO rpt_print_element_def (id, tenant_id, pack_code, element_code, label, render_type, bind_path, kv_items_json, column_defs_json, default_label, layout_mode, sort_order, required, enabled, created_at, is_deleted)
SELECT 910020002005, 0, 'discharge_certificate', 'diagnosisText', '诊断', 'text', 'encounter.diagnosisSummary', NULL, NULL, NULL, 'flow', 50, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM rpt_print_element_def e WHERE e.tenant_id = 0 AND e.pack_code = 'discharge_certificate' AND e.element_code = 'diagnosisText' AND e.is_deleted = FALSE);

INSERT INTO rpt_print_element_def (id, tenant_id, pack_code, element_code, label, render_type, bind_path, kv_items_json, column_defs_json, default_label, layout_mode, sort_order, required, enabled, created_at, is_deleted)
SELECT 910020002006, 0, 'discharge_certificate', 'printTime', '打印时间', 'text', 'system.printTime', NULL, NULL, NULL, 'flow', 60, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM rpt_print_element_def e WHERE e.tenant_id = 0 AND e.pack_code = 'discharge_certificate' AND e.element_code = 'printTime' AND e.is_deleted = FALSE);

UPDATE rpt_template SET name = '出院证/出院小结单证', pipeline = 'page', dataset_code = 'discharge_certificate',
layout_json = '{"pipeline":"page","page":{"size":"A4","orientation":"portrait","marginMm":[12,10,12,10],"layoutMode":"flow"},"items":[{"elementCode":"docTitle","visible":true,"style":{"fontSizePt":16,"align":"center","bold":true}},{"elementCode":"hospitalName","visible":true,"style":{"align":"center","fontSizePt":11}},{"elementCode":"patientInfoRow","visible":true},{"elementCode":"certificateMetaRow","visible":true},{"elementCode":"diagnosisText","visible":true},{"elementCode":"printTime","visible":true}]}'
WHERE tenant_id = 0 AND code = 'discharge_certificate' AND is_deleted = FALSE;

-- ========== lab_apply_form ==========
INSERT INTO rpt_print_element_def (id, tenant_id, pack_code, element_code, label, render_type, bind_path, kv_items_json, column_defs_json, default_label, layout_mode, sort_order, required, enabled, created_at, is_deleted)
SELECT 910020003001, 0, 'lab_apply_form', 'docTitle', '标题', 'staticTitle', NULL, NULL, NULL, '检验申请单', 'flow', 10, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM rpt_print_element_def e WHERE e.tenant_id = 0 AND e.pack_code = 'lab_apply_form' AND e.element_code = 'docTitle' AND e.is_deleted = FALSE);

INSERT INTO rpt_print_element_def (id, tenant_id, pack_code, element_code, label, render_type, bind_path, kv_items_json, column_defs_json, default_label, layout_mode, sort_order, required, enabled, created_at, is_deleted)
SELECT 910020003002, 0, 'lab_apply_form', 'hospitalName', '医疗机构', 'text', 'hospital.name', NULL, NULL, NULL, 'flow', 20, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM rpt_print_element_def e WHERE e.tenant_id = 0 AND e.pack_code = 'lab_apply_form' AND e.element_code = 'hospitalName' AND e.is_deleted = FALSE);

INSERT INTO rpt_print_element_def (id, tenant_id, pack_code, element_code, label, render_type, bind_path, kv_items_json, column_defs_json, default_label, layout_mode, sort_order, required, enabled, created_at, is_deleted)
SELECT 910020003003, 0, 'lab_apply_form', 'patientInfoRow', '患者信息', 'kvRow', 'patient',
'[{"label":"姓名","field":"name"},{"label":"性别","field":"gender"},{"label":"年龄","field":"age"},{"label":"床号","field":"bedNo"},{"label":"科室","field":"deptName"}]', NULL, NULL, 'flow', 30, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM rpt_print_element_def e WHERE e.tenant_id = 0 AND e.pack_code = 'lab_apply_form' AND e.element_code = 'patientInfoRow' AND e.is_deleted = FALSE);

INSERT INTO rpt_print_element_def (id, tenant_id, pack_code, element_code, label, render_type, bind_path, kv_items_json, column_defs_json, default_label, layout_mode, sort_order, required, enabled, created_at, is_deleted)
SELECT 910020003004, 0, 'lab_apply_form', 'orderMetaRow', '单头信息', 'kvRow', 'order',
'[{"label":"单号","field":"sheetNo"},{"label":"开立医生","field":"prescriberName"},{"label":"日期","field":"authoredDate"}]', NULL, NULL, 'flow', 40, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM rpt_print_element_def e WHERE e.tenant_id = 0 AND e.pack_code = 'lab_apply_form' AND e.element_code = 'orderMetaRow' AND e.is_deleted = FALSE);

INSERT INTO rpt_print_element_def (id, tenant_id, pack_code, element_code, label, render_type, bind_path, kv_items_json, column_defs_json, default_label, layout_mode, sort_order, required, enabled, created_at, is_deleted)
SELECT 910020003005, 0, 'lab_apply_form', 'applyLineTable', '申请明细', 'table', 'order.lines', NULL,
'[{"field":"itemName","header":"项目"},{"field":"bodySite","header":"部位"},{"field":"frequency","header":"频次"},{"field":"remark","header":"备注"}]', NULL, 'flow', 50, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM rpt_print_element_def e WHERE e.tenant_id = 0 AND e.pack_code = 'lab_apply_form' AND e.element_code = 'applyLineTable' AND e.is_deleted = FALSE);

INSERT INTO rpt_print_element_def (id, tenant_id, pack_code, element_code, label, render_type, bind_path, kv_items_json, column_defs_json, default_label, layout_mode, sort_order, required, enabled, created_at, is_deleted)
SELECT 910020003006, 0, 'lab_apply_form', 'printTime', '打印时间', 'text', 'system.printTime', NULL, NULL, NULL, 'flow', 60, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM rpt_print_element_def e WHERE e.tenant_id = 0 AND e.pack_code = 'lab_apply_form' AND e.element_code = 'printTime' AND e.is_deleted = FALSE);

UPDATE rpt_template SET name = '检验申请单', pipeline = 'page', dataset_code = 'lab_apply_form',
layout_json = '{"pipeline":"page","page":{"size":"A4","orientation":"portrait","marginMm":[12,10,12,10],"layoutMode":"flow"},"items":[{"elementCode":"docTitle","visible":true,"style":{"fontSizePt":16,"align":"center","bold":true}},{"elementCode":"hospitalName","visible":true,"style":{"align":"center","fontSizePt":11}},{"elementCode":"patientInfoRow","visible":true},{"elementCode":"orderMetaRow","visible":true},{"elementCode":"applyLineTable","visible":true},{"elementCode":"printTime","visible":true}]}'
WHERE tenant_id = 0 AND code = 'lab_apply_form' AND is_deleted = FALSE;

-- ========== exam_apply_form ==========
INSERT INTO rpt_print_element_def (id, tenant_id, pack_code, element_code, label, render_type, bind_path, kv_items_json, column_defs_json, default_label, layout_mode, sort_order, required, enabled, created_at, is_deleted)
SELECT 910020004001, 0, 'exam_apply_form', 'docTitle', '标题', 'staticTitle', NULL, NULL, NULL, '检查申请单', 'flow', 10, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM rpt_print_element_def e WHERE e.tenant_id = 0 AND e.pack_code = 'exam_apply_form' AND e.element_code = 'docTitle' AND e.is_deleted = FALSE);

INSERT INTO rpt_print_element_def (id, tenant_id, pack_code, element_code, label, render_type, bind_path, kv_items_json, column_defs_json, default_label, layout_mode, sort_order, required, enabled, created_at, is_deleted)
SELECT 910020004002, 0, 'exam_apply_form', 'hospitalName', '医疗机构', 'text', 'hospital.name', NULL, NULL, NULL, 'flow', 20, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM rpt_print_element_def e WHERE e.tenant_id = 0 AND e.pack_code = 'exam_apply_form' AND e.element_code = 'hospitalName' AND e.is_deleted = FALSE);

INSERT INTO rpt_print_element_def (id, tenant_id, pack_code, element_code, label, render_type, bind_path, kv_items_json, column_defs_json, default_label, layout_mode, sort_order, required, enabled, created_at, is_deleted)
SELECT 910020004003, 0, 'exam_apply_form', 'patientInfoRow', '患者信息', 'kvRow', 'patient',
'[{"label":"姓名","field":"name"},{"label":"性别","field":"gender"},{"label":"年龄","field":"age"},{"label":"床号","field":"bedNo"},{"label":"科室","field":"deptName"}]', NULL, NULL, 'flow', 30, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM rpt_print_element_def e WHERE e.tenant_id = 0 AND e.pack_code = 'exam_apply_form' AND e.element_code = 'patientInfoRow' AND e.is_deleted = FALSE);

INSERT INTO rpt_print_element_def (id, tenant_id, pack_code, element_code, label, render_type, bind_path, kv_items_json, column_defs_json, default_label, layout_mode, sort_order, required, enabled, created_at, is_deleted)
SELECT 910020004004, 0, 'exam_apply_form', 'orderMetaRow', '单头信息', 'kvRow', 'order',
'[{"label":"单号","field":"sheetNo"},{"label":"开立医生","field":"prescriberName"},{"label":"日期","field":"authoredDate"}]', NULL, NULL, 'flow', 40, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM rpt_print_element_def e WHERE e.tenant_id = 0 AND e.pack_code = 'exam_apply_form' AND e.element_code = 'orderMetaRow' AND e.is_deleted = FALSE);

INSERT INTO rpt_print_element_def (id, tenant_id, pack_code, element_code, label, render_type, bind_path, kv_items_json, column_defs_json, default_label, layout_mode, sort_order, required, enabled, created_at, is_deleted)
SELECT 910020004005, 0, 'exam_apply_form', 'applyLineTable', '申请明细', 'table', 'order.lines', NULL,
'[{"field":"itemName","header":"项目"},{"field":"bodySite","header":"部位"},{"field":"frequency","header":"频次"},{"field":"remark","header":"备注"}]', NULL, 'flow', 50, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM rpt_print_element_def e WHERE e.tenant_id = 0 AND e.pack_code = 'exam_apply_form' AND e.element_code = 'applyLineTable' AND e.is_deleted = FALSE);

INSERT INTO rpt_print_element_def (id, tenant_id, pack_code, element_code, label, render_type, bind_path, kv_items_json, column_defs_json, default_label, layout_mode, sort_order, required, enabled, created_at, is_deleted)
SELECT 910020004006, 0, 'exam_apply_form', 'printTime', '打印时间', 'text', 'system.printTime', NULL, NULL, NULL, 'flow', 60, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM rpt_print_element_def e WHERE e.tenant_id = 0 AND e.pack_code = 'exam_apply_form' AND e.element_code = 'printTime' AND e.is_deleted = FALSE);

UPDATE rpt_template SET name = '检查申请单', pipeline = 'page', dataset_code = 'exam_apply_form',
layout_json = '{"pipeline":"page","page":{"size":"A4","orientation":"portrait","marginMm":[12,10,12,10],"layoutMode":"flow"},"items":[{"elementCode":"docTitle","visible":true,"style":{"fontSizePt":16,"align":"center","bold":true}},{"elementCode":"hospitalName","visible":true,"style":{"align":"center","fontSizePt":11}},{"elementCode":"patientInfoRow","visible":true},{"elementCode":"orderMetaRow","visible":true},{"elementCode":"applyLineTable","visible":true},{"elementCode":"printTime","visible":true}]}'
WHERE tenant_id = 0 AND code = 'exam_apply_form' AND is_deleted = FALSE;

-- ========== V21__B_class_dataset_wiring.sql ==========

-- V21：剩余 B 类模板 dataset 接线 + procedure_apply_form 元素包

-- ========== procedure_apply_form（复用 lab 申请单元素结构） ==========
INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT e.id + 100000, e.tenant_id, 'procedure_apply_form', e.element_code, e.label, e.render_type, e.bind_path,
       e.kv_items_json, e.column_defs_json, '治疗/处置申请单', e.layout_mode, e.sort_order,
       e.required, e.enabled, CURRENT_TIMESTAMP, e.is_deleted
FROM rpt_print_element_def e
WHERE e.tenant_id = 0 AND e.pack_code = 'lab_apply_form' AND e.is_deleted = FALSE
  AND NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def p
    WHERE p.tenant_id = 0 AND p.pack_code = 'procedure_apply_form' AND p.element_code = e.element_code AND p.is_deleted = FALSE
  );

UPDATE rpt_template
SET dataset_code = 'procedure_apply_form',
    layout_json = '{"pipeline":"page","page":{"size":"A4","orientation":"portrait","marginMm":[12,10,12,10],"layoutMode":"flow"},"items":[{"elementCode":"docTitle","visible":true,"style":{"fontSizePt":16,"align":"center","bold":true},"labelOverride":"治疗/处置申请单"},{"elementCode":"hospitalName","visible":true,"style":{"align":"center","fontSizePt":11}},{"elementCode":"patientInfoRow","visible":true},{"elementCode":"orderMetaRow","visible":true},{"elementCode":"applyLineTable","visible":true},{"elementCode":"printTime","visible":true}]}'
WHERE tenant_id = 0 AND code = 'procedure_apply_form' AND is_deleted = FALSE;

-- 检验标本管贴（LAB-02）复用床旁采血数据集
UPDATE rpt_template
SET dataset_code = 'bedside_specimen_barcode'
WHERE tenant_id = 0 AND code = 'specimen_barcode' AND is_deleted = FALSE;

-- 腕带/床头卡族复用 patient_wristband
UPDATE rpt_template
SET dataset_code = 'patient_wristband'
WHERE tenant_id = 0 AND code IN (
    'infant_wristband', 'companion_wristband', 'emergency_wristband', 'bed_card'
) AND is_deleted = FALSE;

-- 药品标签族复用 iv_infusion_label
UPDATE rpt_template
SET dataset_code = 'iv_infusion_label'
WHERE tenant_id = 0 AND code IN (
    'drug_label', 'nebulization_label', 'skin_test_label', 'inpatient_drug_label',
    'tcm_decoction_label', 'pivas_infusion_label', 'controlled_drug_label'
) AND is_deleted = FALSE;

-- 急诊输液瓶贴（EM-03）与 OP-23 同模板数据集
UPDATE rpt_template
SET dataset_code = 'iv_infusion_label'
WHERE tenant_id = 0 AND code = 'iv_infusion_label' AND is_deleted = FALSE;

-- 住院费用明细（IP-25）复用日费用清单数据集
UPDATE rpt_template
SET dataset_code = 'inpatient_daily_charge_list'
WHERE tenant_id = 0 AND code = 'inpatient_charge_detail_list' AND is_deleted = FALSE;

-- 长期/临时医嘱单（IP-10）复用医嘱执行单数据集
UPDATE rpt_template
SET dataset_code = 'order_execute_sheet'
WHERE tenant_id = 0 AND code = 'inpatient_order_sheet' AND is_deleted = FALSE;

-- ========== V22__C_class_report_loader_seed.sql ==========

-- V22：C 类报表定义 seed + P3 B 类 stub 数据集接线

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    created_at, is_deleted
)
SELECT 920001000001, 0, 'outpatient_daily_settlement', '门诊日结报表',
       'outpatientDailySettlementLoader',
       '[{"field":"category","label":"费用类别"},{"field":"amount","label":"金额"},{"field":"unit","label":"单位"}]',
       TRUE, TRUE, 'outpatient_daily_settlement_pdf',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'outpatient_daily_settlement' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    created_at, is_deleted
)
SELECT 920001000002, 0, 'inpatient_daily_settlement', '住院日结报表',
       'inpatientDailySettlementLoader',
       '[{"field":"wardName","label":"病区"},{"field":"category","label":"费用类别"},{"field":"amount","label":"金额"}]',
       TRUE, TRUE, 'inpatient_daily_settlement_pdf',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'inpatient_daily_settlement' AND d.is_deleted = FALSE
);

-- IP-19 / IP-07：stub 数据集接线（正式打印走 EncounterStubPrintDatasetService）
UPDATE rpt_template
SET dataset_code = 'print_page_stub'
WHERE tenant_id = 0 AND code IN ('temperature_chart', 'nursing_record') AND is_deleted = FALSE;

UPDATE rpt_template
SET dataset_code = 'print_page_stub'
WHERE tenant_id = 0 AND code IN (
    'outpatient_daily_settlement_pdf', 'inpatient_daily_settlement_pdf'
) AND is_deleted = FALSE;

-- ========== V23__C_class_more_loaders.sql ==========

-- V23：扩展 C 类报表定义（PH-01 / FIN-01）

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    created_at, is_deleted
)
SELECT 920001000003, 0, 'medication_dispense_summary', '发药汇总单',
       'medicationDispenseSummaryLoader',
       '[{"field":"locationId","label":"药房/库位"},{"field":"dispenseCount","label":"发药笔数"},{"field":"totalQuantity","label":"发药数量"}]',
       TRUE, FALSE, NULL,
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'medication_dispense_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    created_at, is_deleted
)
SELECT 920001000004, 0, 'dept_revenue_report', '科室收入统计',
       'deptRevenueReportLoader',
       '[{"field":"deptName","label":"科室"},{"field":"incomeAmount","label":"收入金额"}]',
       TRUE, FALSE, NULL,
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'dept_revenue_report' AND d.is_deleted = FALSE
);

-- ========== V24__C_class_warehouse_inventory_loaders.sql ==========

-- V24：住院日结真实数据接线说明 + PH-02/03 C 类报表定义

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    created_at, is_deleted
)
SELECT 920001000005, 0, 'warehouse_document', '出库/入库单',
       'warehouseDocumentLoader',
       '[{"field":"busNo","label":"单据号"},{"field":"typeEnum","label":"类型"},{"field":"totalPrice","label":"金额"}]',
       TRUE, FALSE, NULL,
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'warehouse_document' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    created_at, is_deleted
)
SELECT 920001000006, 0, 'inventory_ledger', '库存台账',
       'inventoryLedgerLoader',
       '[{"field":"name","label":"品名"},{"field":"lotNumber","label":"批号"},{"field":"quantity","label":"数量"}]',
       TRUE, FALSE, NULL,
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'inventory_ledger' AND d.is_deleted = FALSE
);

-- ========== V25__C_class_complete_loaders.sql ==========

-- V25：补齐 C 类报表定义（出厂 Loader 全集）

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    created_at, is_deleted
)
SELECT 920001000007, 0, 'cashier_daily_report', '收费员缴款日报',
       'cashierDailyReportLoader',
       '[{"field":"cashierId","label":"收费员"},{"field":"paymentCount","label":"笔数"},{"field":"amount","label":"金额"}]',
       TRUE, FALSE, NULL,
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'cashier_daily_report' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    created_at, is_deleted
)
SELECT 920001000008, 0, 'order_execute_batch', '医嘱执行单（批量）',
       'orderExecuteBatchReportLoader',
       '[{"field":"procedureId","label":"执行ID"},{"field":"encounterId","label":"就诊ID"},{"field":"occurrenceTime","label":"执行时间"}]',
       TRUE, FALSE, NULL,
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'order_execute_batch' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    created_at, is_deleted
)
SELECT 920001000009, 0, 'inventory_stocktake', '盘点表',
       'inventoryStocktakeReportLoader',
       '[{"field":"busNo","label":"单据号"},{"field":"typeEnum","label":"类型"},{"field":"itemQuantity","label":"盈亏数量"}]',
       TRUE, FALSE, NULL,
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'inventory_stocktake' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    created_at, is_deleted
)
SELECT 920001000010, 0, 'warehouse_document_detail', '库房单据明细',
       'warehouseDocumentDetailReportLoader',
       '[{"field":"busNo","label":"单据号"},{"field":"typeEnum","label":"类型"},{"field":"totalPrice","label":"金额"}]',
       TRUE, FALSE, NULL,
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'warehouse_document_detail' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    created_at, is_deleted
)
SELECT 920001000011, 0, 'warehouse_transfer', '调拨单',
       'warehouseTransferReportLoader',
       '[{"field":"busNo","label":"单据号"},{"field":"typeEnum","label":"类型"},{"field":"totalPrice","label":"金额"}]',
       TRUE, FALSE, NULL,
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'warehouse_transfer' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    created_at, is_deleted
)
SELECT 920001000012, 0, 'pivas_batch_summary', '配液汇总单',
       'pivasBatchSummaryReportLoader',
       '[{"field":"batchNo","label":"批次号"},{"field":"bagCount","label":"袋数"}]',
       TRUE, FALSE, NULL,
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'pivas_batch_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    created_at, is_deleted
)
SELECT 920001000013, 0, 'lab_worklist', '检验工作清单',
       'labWorklistReportLoader',
       '[{"field":"workStep","label":"环节"},{"field":"sampleHint","label":"说明"}]',
       TRUE, FALSE, NULL,
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'lab_worklist' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    created_at, is_deleted
)
SELECT 920001000014, 0, 'invoice_usage_report', '发票/票据使用明细',
       'invoiceUsageReportLoader',
       '[{"field":"busNo","label":"票据号"},{"field":"typeCode","label":"类型"},{"field":"amount","label":"金额"}]',
       TRUE, FALSE, NULL,
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'invoice_usage_report' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    created_at, is_deleted
)
SELECT 920001000015, 0, 'insurance_reconciliation', '医保对账表',
       'insuranceReconciliationReportLoader',
       '[{"field":"paymentNo","label":"支付单号"},{"field":"ybSettleIds","label":"医保结算ID"}]',
       TRUE, FALSE, NULL,
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'insurance_reconciliation' AND d.is_deleted = FALSE
);

-- ========== V26__Retire_case_record_homepage_from_report.sql ==========

-- V26：病案首页打印归 whale-mrms（OpenPDF），移出 whale-report 模板目录

UPDATE rpt_template
SET is_deleted = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE tenant_id = 0
  AND code = 'case_record_homepage'
  AND is_deleted = FALSE;

-- ========== V27__Expand_charge_receipt_element_pack.sql ==========

-- charge_receipt 元素包扩展：对齐门诊收费明细热敏小票（患者/明细/医保支付/发票提示）

-- 更新明细表列：收费名称、规格、数量、金额、类
UPDATE rpt_print_element_def
SET column_defs_json = '[{"field":"itemName","header":"收费名称"},{"field":"specText","header":"规格"},{"field":"quantity","header":"数量"},{"field":"lineAmount","header":"金额"},{"field":"categoryCode","header":"类"}]',
    label = '收费明细'
WHERE tenant_id = 0
  AND pack_code = 'charge_receipt'
  AND element_code = 'chargeLineTable'
  AND is_deleted = FALSE;

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910027000001, 0, 'charge_receipt', 'rollTitle', '单据标题', 'staticTitle', NULL,
       NULL, NULL, '门诊收费明细', 'flow', 15, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'charge_receipt' AND e.element_code = 'rollTitle' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910027000002, 0, 'charge_receipt', 'patientInfoRow', '患者信息', 'kvRow', 'patient',
       '[{"label":"姓名","field":"name"},{"label":"性别","field":"genderText"},{"label":"年龄","field":"ageText"},{"label":"病人类型","field":"patientTypeText"},{"label":"门诊号","field":"outpatientNo"},{"label":"院内门诊号","field":"internalOutpatientNo"}]',
       NULL, NULL, 'flow', 25, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'charge_receipt' AND e.element_code = 'patientInfoRow' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910027000003, 0, 'charge_receipt', 'paymentSummaryRow', '结算支付', 'kvRow', 'paymentSummary',
       '[{"label":"合计金额","field":"totalAmount"},{"label":"实收金额","field":"actualReceivedAmount"},{"label":"卡内支付","field":"cardPayAmount"},{"label":"原账户余额","field":"accountBalanceBefore"},{"label":"大特病统筹","field":"seriousDiseaseFund"},{"label":"现账户余额","field":"accountBalanceAfter"},{"label":"医疗救助","field":"medicalAssistance"},{"label":"公务员补助","field":"civilServantSubsidy"},{"label":"离休统筹","field":"retiredOfficialFund"},{"label":"兜底扶贫","field":"povertyAlleviation"},{"label":"共济支付","field":"mutualAidPay"},{"label":"工伤统筹","field":"workInjuryFund"},{"label":"医保基金总额","field":"insuranceFundTotal"},{"label":"微信刷卡支付","field":"wechatCardPay"},{"label":"统筹支付","field":"fundPayAmount"}]',
       NULL, NULL, 'flow', 45, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'charge_receipt' AND e.element_code = 'paymentSummaryRow' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910027000004, 0, 'charge_receipt', 'chargeFooterRow', '收费员与时间', 'kvRow', 'footer',
       '[{"label":"收费员","field":"operatorName"},{"label":"收费时间","field":"chargeTime"}]',
       NULL, NULL, 'flow', 55, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'charge_receipt' AND e.element_code = 'chargeFooterRow' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910027000005, 0, 'charge_receipt', 'invoiceHintTitle', '发票提示标题', 'staticTitle', NULL,
       NULL, NULL, '财政发票查验获取', 'flow', 60, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'charge_receipt' AND e.element_code = 'invoiceHintTitle' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910027000006, 0, 'charge_receipt', 'invoiceHintBody', '发票提示说明', 'staticTitle', NULL,
       NULL, NULL, '1.到人工窗口打印  2.扫描下方二维码', 'flow', 65, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'charge_receipt' AND e.element_code = 'invoiceHintBody' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910027000007, 0, 'charge_receipt', 'invoiceStatusText', '开票状态', 'text', 'invoice.statusText',
       NULL, NULL, NULL, 'flow', 70, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'charge_receipt' AND e.element_code = 'invoiceStatusText' AND e.is_deleted = FALSE
);

-- §9.2 文档列出的补充单项元素（设计器可选）
INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910027000010, 0, 'charge_receipt', 'chargeBusNo', '支付单号', 'text', 'charge.busNo',
       NULL, NULL, NULL, 'flow', 22, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'charge_receipt' AND e.element_code = 'chargeBusNo' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910027000011, 0, 'charge_receipt', 'subtotalAmount', '小计', 'text', 'charge.subtotalAmount',
       NULL, NULL, '小计：', 'flow', 42, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'charge_receipt' AND e.element_code = 'subtotalAmount' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910027000012, 0, 'charge_receipt', 'payMethodText', '支付方式', 'text', 'payment.methodText',
       NULL, NULL, NULL, 'flow', 48, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'charge_receipt' AND e.element_code = 'payMethodText' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910027000014, 0, 'charge_receipt', 'paymentPayRow', '支付方式与实收', 'kvRow', 'payment',
       '[{"label":"支付方式","field":"methodText"},{"label":"实收","field":"paidAmount"}]',
       NULL, NULL, 'flow', 47, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'charge_receipt' AND e.element_code = 'paymentPayRow' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, box_json, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910027000015, 0, 'charge_receipt', 'outpatientNoQrcode', '门诊号二维码', 'qrcode', 'patient.outpatientNo',
       NULL, NULL, NULL, '{"xMm":22,"yMm":150,"widthMm":36,"heightMm":36}', 'flow', 72, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'charge_receipt' AND e.element_code = 'outpatientNoQrcode' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910027000013, 0, 'charge_receipt', 'printTime', '打印时间', 'text', 'system.printTime',
       NULL, NULL, NULL, 'flow', 75, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'charge_receipt' AND e.element_code = 'printTime' AND e.is_deleted = FALSE
);

-- 出厂模板 layout：对齐热敏收费明细小票结构
UPDATE rpt_template
SET name = '门诊收费凭条',
    layout_json = '{"pipeline":"roll","page":{"widthMm":80,"layoutMode":"flow"},"items":[{"elementCode":"storeName","visible":true,"style":{"align":"center","fontSizePt":11,"bold":true}},{"elementCode":"rollTitle","visible":true,"style":{"align":"center","fontSizePt":12,"bold":true}},{"elementCode":"patientInfoRow","visible":true},{"elementCode":"chargeLineTable","visible":true},{"elementCode":"paymentPayRow","visible":true,"columns":2},{"elementCode":"paymentSummaryRow","visible":true},{"elementCode":"chargeFooterRow","visible":true},{"elementCode":"invoiceHintTitle","visible":true,"style":{"fontSizePt":10,"bold":true}},{"elementCode":"invoiceHintBody","visible":true,"style":{"fontSizePt":9}},{"elementCode":"invoiceStatusText","visible":true,"style":{"fontSizePt":9}},{"elementCode":"outpatientNoQrcode","visible":true,"box":{"xMm":22,"yMm":150,"widthMm":36,"heightMm":36}},{"elementCode":"dividerLine","visible":true}]}'
WHERE tenant_id = 0
  AND code = 'charge_receipt'
  AND is_deleted = FALSE;

-- ========== V28__print_binding.sql ==========
-- 表结构已并入 V1__Report_Init.sql；此处无种子数据。

-- ========== V29__Print_text_alignment_and_hospital_name.sql ==========

-- 机构名称元素：设计器展示名改为「机构名称」，打印侧由 bind_path=hospital.name 仅输出名称值
UPDATE rpt_print_element_def
SET label = '机构名称'
WHERE tenant_id = 0
  AND element_code IN ('storeName', 'hospitalName')
  AND is_deleted = FALSE;

-- ========== V30__Fix_roll_template_layout_pipeline.sql ==========

-- roll 模板 layout_json 缺失 pipeline 时会被按 A4 渲染；补全 JSON 内 pipeline 字段
UPDATE rpt_template
SET layout_json = REPLACE(layout_json, '{"page":', '{"pipeline":"roll","page":')
WHERE pipeline = 'roll'
  AND is_deleted = FALSE
  AND layout_json IS NOT NULL
  AND layout_json NOT LIKE '%"pipeline"%'
  AND layout_json LIKE '{"page":%';

UPDATE rpt_template
SET layout_json = REPLACE(layout_json, '{"items":', '{"pipeline":"roll","items":')
WHERE pipeline = 'roll'
  AND is_deleted = FALSE
  AND layout_json IS NOT NULL
  AND layout_json NOT LIKE '%"pipeline"%'
  AND layout_json LIKE '{"items":%';

-- ========== V31__Fix_roll_template_pipeline_column.sql ==========

-- 纠正 roll 小票模板 pipeline 列与 layout_json（避免按 A4 渲染）
UPDATE rpt_template
SET pipeline = 'roll'
WHERE code IN (
    'charge_receipt',
    'registration_receipt',
    'outpatient_refund_receipt',
    'deposit_receipt',
    'iv_infusion_label',
    'patient_wristband',
    'bedside_specimen_barcode',
    'appointment_queue_ticket'
  )
  AND is_deleted = FALSE
  AND pipeline IS DISTINCT FROM 'roll';

UPDATE rpt_template
SET layout_json = REPLACE(layout_json, '"pipeline":"page"', '"pipeline":"roll"')
WHERE code IN (
    'charge_receipt',
    'registration_receipt',
    'outpatient_refund_receipt',
    'deposit_receipt'
  )
  AND is_deleted = FALSE
  AND layout_json LIKE '%"pipeline":"page"%';

UPDATE rpt_template
SET layout_json = jsonb_set(
        layout_json::jsonb,
        '{page,widthMm}',
        '80'::jsonb,
        true
    )::text
WHERE pipeline = 'roll'
  AND is_deleted = FALSE
  AND layout_json IS NOT NULL
  AND layout_json NOT LIKE '%"widthMm"%';

-- ========== V32__Strip_page_size_from_roll_layout_json.sql ==========

-- roll 模板 layout_json 不应含 page.size / orientation（设计器误存 A4/A5 时清理）
UPDATE rpt_template
SET layout_json = (layout_json::jsonb - 'page')
    || jsonb_build_object(
        'page',
        (layout_json::jsonb -> 'page') - 'size' - 'orientation'
    )
WHERE pipeline = 'roll'
  AND is_deleted = FALSE
  AND layout_json IS NOT NULL
  AND layout_json::jsonb -> 'page' ?| array['size', 'orientation'];

-- ========== 发版合并：原 V3–V22 增量种子（清库后随 V2 一次性执行）==========

-- ----- V3__KvRow_columns_and_payment_pay_row.sql -----
-- kvRow columns 列数 + charge_receipt 支付方式/实收两列元素

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910027000014, 0, 'charge_receipt', 'paymentPayRow', '支付方式与实收', 'kvRow', 'payment',
       '[{"label":"支付方式","field":"methodText"},{"label":"实收","field":"paidAmount"}]',
       NULL, NULL, 'flow', 47, TRUE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'charge_receipt' AND e.element_code = 'paymentPayRow' AND e.is_deleted = FALSE
);

UPDATE rpt_template
SET layout_json = '{"pipeline":"roll","page":{"widthMm":80,"layoutMode":"flow"},"items":[{"elementCode":"storeName","visible":true,"style":{"align":"center","fontSizePt":11,"bold":true}},{"elementCode":"rollTitle","visible":true,"style":{"align":"center","fontSizePt":12,"bold":true}},{"elementCode":"patientInfoRow","visible":true},{"elementCode":"chargeLineTable","visible":true},{"elementCode":"paymentPayRow","visible":true,"columns":2},{"elementCode":"paymentSummaryRow","visible":true},{"elementCode":"chargeFooterRow","visible":true},{"elementCode":"invoiceHintTitle","visible":true,"style":{"fontSizePt":10,"bold":true}},{"elementCode":"invoiceHintBody","visible":true,"style":{"fontSizePt":9}},{"elementCode":"invoiceStatusText","visible":true,"style":{"fontSizePt":9}},{"elementCode":"dividerLine","visible":true}]}'
WHERE tenant_id = 0
  AND code = 'charge_receipt'
  AND is_deleted = FALSE
  AND layout_json NOT LIKE '%paymentPayRow%';

-- ----- V4__Outpatient_no_qrcode.sql -----
-- 门诊号二维码元素（charge_receipt / registration_receipt）

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, box_json, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910027000015, 0, 'charge_receipt', 'outpatientNoQrcode', '门诊号二维码', 'qrcode', 'patient.outpatientNo',
       NULL, NULL, NULL, '{"xMm":22,"yMm":150,"widthMm":36,"heightMm":36}', 'flow', 72, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'charge_receipt' AND e.element_code = 'outpatientNoQrcode' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, box_json, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910018000012, 0, 'registration_receipt', 'outpatientNoQrcode', '门诊号二维码', 'qrcode', 'patient.outpatientNo',
       NULL, NULL, NULL, '{"xMm":22,"yMm":120,"widthMm":36,"heightMm":36}', 'flow', 72, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'registration_receipt' AND e.element_code = 'outpatientNoQrcode' AND e.is_deleted = FALSE
);

UPDATE rpt_template
SET layout_json = jsonb_set(
        layout_json::jsonb,
        '{items}',
        (layout_json::jsonb -> 'items') || '[{"elementCode":"outpatientNoQrcode","visible":true,"box":{"xMm":22,"yMm":150,"widthMm":36,"heightMm":36}}]'::jsonb,
        true
    )::text
WHERE tenant_id = 0
  AND code = 'charge_receipt'
  AND is_deleted = FALSE
  AND layout_json NOT LIKE '%outpatientNoQrcode%';

UPDATE rpt_template
SET layout_json = jsonb_set(
        layout_json::jsonb,
        '{items}',
        (layout_json::jsonb -> 'items') || '[{"elementCode":"outpatientNoQrcode","visible":true,"box":{"xMm":22,"yMm":120,"widthMm":36,"heightMm":36}}]'::jsonb,
        true
    )::text
WHERE tenant_id = 0
  AND code = 'registration_receipt'
  AND is_deleted = FALSE
  AND layout_json NOT LIKE '%outpatientNoQrcode%';

-- ----- V5__Fix_qrcode_default_position.sql -----
-- 修正门诊号二维码默认位置（y=300 在设计区可视范围外）

UPDATE rpt_print_element_def
SET box_json = '{"xMm":22,"yMm":150,"widthMm":36,"heightMm":36}'
WHERE element_code = 'outpatientNoQrcode'
  AND pack_code = 'charge_receipt'
  AND is_deleted = FALSE
  AND box_json LIKE '%"yMm":300%';

UPDATE rpt_print_element_def
SET box_json = '{"xMm":22,"yMm":120,"widthMm":36,"heightMm":36}'
WHERE element_code = 'outpatientNoQrcode'
  AND pack_code = 'registration_receipt'
  AND is_deleted = FALSE
  AND (box_json LIKE '%"yMm":180%' OR box_json LIKE '%"yMm":300%');

UPDATE rpt_template
SET layout_json = REPLACE(layout_json, '"yMm":300', '"yMm":150')
WHERE code = 'charge_receipt'
  AND is_deleted = FALSE
  AND layout_json LIKE '%outpatientNoQrcode%'
  AND layout_json LIKE '%"yMm":300%';

UPDATE rpt_template
SET layout_json = REPLACE(
        layout_json,
        '"elementCode":"outpatientNoQrcode","visible":true,"box":{"xMm":22,"yMm":180',
        '"elementCode":"outpatientNoQrcode","visible":true,"box":{"xMm":22,"yMm":120'
    )
WHERE code = 'registration_receipt'
  AND is_deleted = FALSE
  AND layout_json LIKE '%outpatientNoQrcode%';

-- ----- V6__Booking_appointment_voucher.sql -----
-- appointment_voucher：预约凭证（Booking 10.5/10.6）

INSERT INTO rpt_template (
    id, tenant_id, code, name, pipeline, layout_json, dataset_code,
    status, version, organization_id, created_at, is_deleted
)
SELECT
    920003000070,
    0,
    'appointment_voucher',
    '预约凭证',
    'roll',
    '{"pipeline":"roll","page":{"widthMm":80,"layoutMode":"flow"},"items":[{"elementCode":"rollTitle","visible":true,"style":{"fontSizePt":12,"align":"center","bold":true},"labelOverride":"预约凭证"},{"elementCode":"kvRow","visible":true,"bindPath":"busNo","labelOverride":"预约单号"},{"elementCode":"kvRow","visible":true,"bindPath":"patientName","labelOverride":"患者"},{"elementCode":"kvRow","visible":true,"bindPath":"bookingTime","labelOverride":"预约时间"},{"elementCode":"kvRow","visible":true,"bindPath":"departmentName","labelOverride":"科室"}]}',
    'print_roll_stub',
    'published',
    1,
    NULL,
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_template t
    WHERE t.tenant_id = 0 AND t.code = 'appointment_voucher' AND t.is_deleted = FALSE
);

-- ----- V7__Clean_template_display_names.sql -----
-- V7：模板展示名去内部编号前缀（P1/OP-11 等仅保留在 catalog JSON，不暴露给产品 UI）

UPDATE rpt_template
SET name = regexp_replace(name, '^【P[0-9]+·[A-Z0-9]+-[0-9]+】', ''),
    updated_at = CURRENT_TIMESTAMP
WHERE tenant_id = 0
  AND is_deleted = FALSE
  AND name ~ '^【P[0-9]+·';

-- ----- V8__Charge_receipt_invoice_qrcode_and_miniprogram.sql -----
-- 收费类热敏小票：预置「发票二维码」「小程序图像」种子元素

-- ========== charge_receipt ==========
INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, box_json, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910028000001, 0, 'charge_receipt', 'invoiceQrcode', '发票二维码', 'qrcode', 'invoice.verifyUrl',
       NULL, NULL, NULL, '{"xMm":6,"yMm":192,"widthMm":34,"heightMm":34}', 'flow', 73, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'charge_receipt' AND e.element_code = 'invoiceQrcode' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, box_json, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910028000002, 0, 'charge_receipt', 'miniProgramImage', '小程序图像', 'image', 'hospital.miniProgramImageUrl',
       NULL, NULL, NULL, '{"xMm":42,"yMm":192,"widthMm":34,"heightMm":34}', 'flow', 74, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'charge_receipt' AND e.element_code = 'miniProgramImage' AND e.is_deleted = FALSE
);

-- ========== registration_receipt ==========
INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, box_json, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910028000011, 0, 'registration_receipt', 'invoiceQrcode', '发票二维码', 'qrcode', 'invoice.verifyUrl',
       NULL, NULL, NULL, '{"xMm":6,"yMm":192,"widthMm":34,"heightMm":34}', 'flow', 73, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'registration_receipt' AND e.element_code = 'invoiceQrcode' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, box_json, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910028000012, 0, 'registration_receipt', 'miniProgramImage', '小程序图像', 'image', 'hospital.miniProgramImageUrl',
       NULL, NULL, NULL, '{"xMm":42,"yMm":192,"widthMm":34,"heightMm":34}', 'flow', 74, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'registration_receipt' AND e.element_code = 'miniProgramImage' AND e.is_deleted = FALSE
);

-- ========== outpatient_refund_receipt ==========
INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, box_json, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910028000021, 0, 'outpatient_refund_receipt', 'invoiceQrcode', '发票二维码', 'qrcode', 'invoice.verifyUrl',
       NULL, NULL, NULL, '{"xMm":6,"yMm":192,"widthMm":34,"heightMm":34}', 'flow', 73, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'outpatient_refund_receipt' AND e.element_code = 'invoiceQrcode' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, box_json, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910028000022, 0, 'outpatient_refund_receipt', 'miniProgramImage', '小程序图像', 'image', 'hospital.miniProgramImageUrl',
       NULL, NULL, NULL, '{"xMm":42,"yMm":192,"widthMm":34,"heightMm":34}', 'flow', 74, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'outpatient_refund_receipt' AND e.element_code = 'miniProgramImage' AND e.is_deleted = FALSE
);

-- ========== deposit_receipt ==========
INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, box_json, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910028000031, 0, 'deposit_receipt', 'invoiceQrcode', '发票二维码', 'qrcode', 'invoice.verifyUrl',
       NULL, NULL, NULL, '{"xMm":6,"yMm":192,"widthMm":34,"heightMm":34}', 'flow', 73, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'deposit_receipt' AND e.element_code = 'invoiceQrcode' AND e.is_deleted = FALSE
);

INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, box_json, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
)
SELECT 910028000032, 0, 'deposit_receipt', 'miniProgramImage', '小程序图像', 'image', 'hospital.miniProgramImageUrl',
       NULL, NULL, NULL, '{"xMm":42,"yMm":192,"widthMm":34,"heightMm":34}', 'flow', 74, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_print_element_def e
    WHERE e.tenant_id = 0 AND e.pack_code = 'deposit_receipt' AND e.element_code = 'miniProgramImage' AND e.is_deleted = FALSE
);

-- 出厂模板 layout：追加绝对层元素（已定制模板不覆盖整份 JSON）
UPDATE rpt_template
SET layout_json = jsonb_set(
        layout_json::jsonb,
        '{items}',
        (layout_json::jsonb -> 'items')
            || '[{"elementCode":"invoiceQrcode","visible":true,"box":{"xMm":6,"yMm":192,"widthMm":34,"heightMm":34}},{"elementCode":"miniProgramImage","visible":true,"box":{"xMm":42,"yMm":192,"widthMm":34,"heightMm":34}}]'::jsonb,
        true
    )::text
WHERE tenant_id = 0
  AND code = 'charge_receipt'
  AND is_deleted = FALSE
  AND layout_json NOT LIKE '%invoiceQrcode%';

UPDATE rpt_template
SET layout_json = jsonb_set(
        layout_json::jsonb,
        '{items}',
        (layout_json::jsonb -> 'items')
            || '[{"elementCode":"invoiceQrcode","visible":true,"box":{"xMm":6,"yMm":192,"widthMm":34,"heightMm":34}},{"elementCode":"miniProgramImage","visible":true,"box":{"xMm":42,"yMm":192,"widthMm":34,"heightMm":34}}]'::jsonb,
        true
    )::text
WHERE tenant_id = 0
  AND code = 'registration_receipt'
  AND is_deleted = FALSE
  AND layout_json NOT LIKE '%invoiceQrcode%';

UPDATE rpt_template
SET layout_json = jsonb_set(
        layout_json::jsonb,
        '{items}',
        (layout_json::jsonb -> 'items')
            || '[{"elementCode":"invoiceQrcode","visible":true,"box":{"xMm":6,"yMm":192,"widthMm":34,"heightMm":34}},{"elementCode":"miniProgramImage","visible":true,"box":{"xMm":42,"yMm":192,"widthMm":34,"heightMm":34}}]'::jsonb,
        true
    )::text
WHERE tenant_id = 0
  AND code = 'outpatient_refund_receipt'
  AND is_deleted = FALSE
  AND layout_json NOT LIKE '%invoiceQrcode%';

UPDATE rpt_template
SET layout_json = jsonb_set(
        layout_json::jsonb,
        '{items}',
        (layout_json::jsonb -> 'items')
            || '[{"elementCode":"invoiceQrcode","visible":true,"box":{"xMm":6,"yMm":192,"widthMm":34,"heightMm":34}},{"elementCode":"miniProgramImage","visible":true,"box":{"xMm":42,"yMm":192,"widthMm":34,"heightMm":34}}]'::jsonb,
        true
    )::text
WHERE tenant_id = 0
  AND code = 'deposit_receipt'
  AND is_deleted = FALSE
  AND layout_json NOT LIKE '%invoiceQrcode%';

-- ----- V9__Remove_overlay_stock_fields.sql -----
-- V9：移除 overlay 中未参与渲染的 stockCode / stockName（预印纸由模板名 + guideImageUrl + items[].box 标识）

UPDATE rpt_template
SET layout_json = (
        (layout_json::jsonb #- '{page,overlay,stockCode}' #- '{page,overlay,stockName}')::text
    ),
    updated_at = CURRENT_TIMESTAMP
WHERE is_deleted = FALSE
  AND layout_json IS NOT NULL
  AND layout_json::jsonb -> 'page' -> 'overlay' IS NOT NULL
  AND (
    (layout_json::jsonb -> 'page' -> 'overlay') ? 'stockCode'
    OR (layout_json::jsonb -> 'page' -> 'overlay') ? 'stockName'
  );

-- ----- V10__Ensure_prescription_overlay_box_json.sql -----
-- V10：处方套打共用 flow 元素补齐 box_json（设计器落格框依赖 defaultBox）

UPDATE rpt_print_element_def SET box_json = '{"xMm":22,"yMm":48,"widthMm":130,"heightMm":8,"overflow":"shrinkFont"}'
WHERE tenant_id = 0 AND pack_code = 'outpatient_prescription' AND element_code = 'diagnosisText'
  AND is_deleted = FALSE AND (box_json IS NULL OR TRIM(box_json) = '');

UPDATE rpt_print_element_def SET box_json = '{"xMm":22,"yMm":62,"widthMm":130,"heightMm":80,"overflow":"clip","rowHeightMm":5}'
WHERE tenant_id = 0 AND pack_code = 'outpatient_prescription' AND element_code = 'prescriptionDrugTable'
  AND is_deleted = FALSE AND (box_json IS NULL OR TRIM(box_json) = '');

-- ----- V11__Remove_factory_guide_image_urls.sql -----
-- V11：清除 overlay 出厂示意 guideImageUrl（定位纸需院点自行上传）

UPDATE rpt_template
SET layout_json = (
        (layout_json::jsonb #- '{page,overlay,guideImageUrl}')::text
    ),
    updated_at = CURRENT_TIMESTAMP
WHERE is_deleted = FALSE
  AND layout_json IS NOT NULL
  AND layout_json::jsonb -> 'page' -> 'overlay' ->> 'guideImageUrl' LIKE '/assets/print-guides/%';

-- ----- V12__Prescription_overlay_layout_items.sql -----
-- V12：门诊处方套打补齐标题、患者/处方头、脚注等排版项（对齐预印 A5 处方笺）

-- flow 共用元素：补 box_json 后 overlay 设计器可选
UPDATE rpt_print_element_def SET box_json = '{"xMm":24,"yMm":4,"widthMm":100,"heightMm":5,"overflow":"clip"}'
WHERE tenant_id = 0 AND pack_code = 'outpatient_prescription' AND element_code = 'hospitalName' AND is_deleted = FALSE;

UPDATE rpt_print_element_def SET box_json = '{"xMm":44,"yMm":9,"widthMm":60,"heightMm":6,"overflow":"clip"}'
WHERE tenant_id = 0 AND pack_code = 'outpatient_prescription' AND element_code = 'docTitle' AND is_deleted = FALSE;

UPDATE rpt_print_element_def SET box_json = '{"xMm":82,"yMm":22,"widthMm":58,"heightMm":4,"overflow":"clip"}'
WHERE tenant_id = 0 AND pack_code = 'outpatient_prescription' AND element_code = 'rxNo' AND is_deleted = FALSE;

UPDATE rpt_print_element_def SET box_json = '{"xMm":24,"yMm":28,"widthMm":22,"heightMm":4,"overflow":"clip"}'
WHERE tenant_id = 0 AND pack_code = 'outpatient_prescription' AND element_code = 'patientName' AND is_deleted = FALSE;

UPDATE rpt_print_element_def SET box_json = '{"xMm":78,"yMm":28,"widthMm":12,"heightMm":4,"overflow":"clip"}'
WHERE tenant_id = 0 AND pack_code = 'outpatient_prescription' AND element_code = 'patientGender' AND is_deleted = FALSE;

UPDATE rpt_print_element_def SET box_json = '{"xMm":58,"yMm":28,"widthMm":14,"heightMm":4,"overflow":"clip"}'
WHERE tenant_id = 0 AND pack_code = 'outpatient_prescription' AND element_code = 'patientAge' AND is_deleted = FALSE;

UPDATE rpt_print_element_def SET box_json = '{"xMm":24,"yMm":46,"widthMm":108,"heightMm":5,"overflow":"shrinkFont"}'
WHERE tenant_id = 0 AND pack_code = 'outpatient_prescription' AND element_code = 'diagnosisText' AND is_deleted = FALSE;

UPDATE rpt_print_element_def SET box_json = '{"xMm":12,"yMm":58,"widthMm":124,"heightMm":102,"overflow":"clip","rowHeightMm":5}'
WHERE tenant_id = 0 AND pack_code = 'outpatient_prescription' AND element_code = 'prescriptionDrugTable' AND is_deleted = FALSE;

UPDATE rpt_print_element_def SET box_json = '{"xMm":24,"yMm":172,"widthMm":28,"heightMm":4,"overflow":"clip"}'
WHERE tenant_id = 0 AND pack_code = 'outpatient_prescription' AND element_code = 'prescriberName' AND is_deleted = FALSE;

-- 新增 overlay 专用字段
INSERT INTO rpt_print_element_def (
    id, tenant_id, pack_code, element_code, label, render_type, bind_path,
    kv_items_json, column_defs_json, default_label, box_json, layout_mode, sort_order,
    required, enabled, created_at, is_deleted
) VALUES
(910001000106, 0, 'outpatient_prescription', 'encounterClassText', '就诊类别', 'text', 'encounter.classText',
 NULL, NULL, NULL, '{"xMm":105,"yMm":5,"widthMm":38,"heightMm":4,"overflow":"clip"}', 'overlay', 106, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE),
(910001000107, 0, 'outpatient_prescription', 'rxValidityHint', '有效提示', 'staticTitle', NULL,
 NULL, NULL, '当日有效', '{"xMm":105,"yMm":9,"widthMm":38,"heightMm":4,"overflow":"clip"}', 'overlay', 107, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE),
(910001000108, 0, 'outpatient_prescription', 'deptName', '科别', 'text', 'prescription.deptName',
 NULL, NULL, NULL, '{"xMm":12,"yMm":22,"widthMm":24,"heightMm":4,"overflow":"clip"}', 'overlay', 108, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE),
(910001000109, 0, 'outpatient_prescription', 'payTypeText', '费别', 'text', 'encounter.payTypeText',
 NULL, NULL, NULL, '{"xMm":38,"yMm":22,"widthMm":18,"heightMm":4,"overflow":"clip"}', 'overlay', 109, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE),
(910001000110, 0, 'outpatient_prescription', 'patientPhone', '电话', 'text', 'patient.phone',
 NULL, NULL, NULL, '{"xMm":24,"yMm":34,"widthMm":42,"heightMm":4,"overflow":"clip"}', 'overlay', 110, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE),
(910001000111, 0, 'outpatient_prescription', 'authoredDate', '开具日期', 'text', 'prescription.authoredDate',
 NULL, NULL, NULL, '{"xMm":72,"yMm":34,"widthMm":68,"heightMm":4,"overflow":"clip"}', 'overlay', 111, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE),
(910001000112, 0, 'outpatient_prescription', 'patientIdCard', '身份证号', 'text', 'patient.idCard',
 NULL, NULL, NULL, '{"xMm":32,"yMm":40,"widthMm":88,"heightMm":4,"overflow":"clip"}', 'overlay', 112, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE),
(910001000113, 0, 'outpatient_prescription', 'rpLabel', 'Rp', 'staticTitle', NULL,
 NULL, NULL, 'Rp:', '{"xMm":12,"yMm":54,"widthMm":8,"heightMm":4,"overflow":"clip"}', 'overlay', 113, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE),
(910001000114, 0, 'outpatient_prescription', 'totalAmount', '金额', 'text', 'prescription.totalAmount',
 NULL, NULL, NULL, '{"xMm":72,"yMm":172,"widthMm":24,"heightMm":4,"overflow":"clip"}', 'overlay', 114, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE),
(910001000115, 0, 'outpatient_prescription', 'dispenserName', '调配', 'text', 'practitioner.dispenserName',
 NULL, NULL, NULL, '{"xMm":24,"yMm":178,"widthMm":28,"heightMm":4,"overflow":"clip"}', 'overlay', 115, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE),
(910001000116, 0, 'outpatient_prescription', 'checkerName', '核对', 'text', 'practitioner.checkerName',
 NULL, NULL, NULL, '{"xMm":24,"yMm":184,"widthMm":28,"heightMm":4,"overflow":"clip"}', 'overlay', 116, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE),
(910001000117, 0, 'outpatient_prescription', 'reviewerName', '审核', 'text', 'practitioner.reviewerName',
 NULL, NULL, NULL, '{"xMm":72,"yMm":178,"widthMm":28,"heightMm":4,"overflow":"clip"}', 'overlay', 117, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE),
(910001000118, 0, 'outpatient_prescription', 'issuerName', '发药', 'text', 'practitioner.issuerName',
 NULL, NULL, NULL, '{"xMm":72,"yMm":184,"widthMm":28,"heightMm":4,"overflow":"clip"}', 'overlay', 118, FALSE, TRUE, CURRENT_TIMESTAMP, FALSE);

-- 出厂 overlay 模板：补齐排版项（预印纸仅打数值区，标题/标签已在纸上时可隐藏 docTitle/hospitalName/rpLabel）
UPDATE rpt_template
SET layout_json = '{
  "pipeline": "page",
  "page": {
    "size": "A5",
    "orientation": "portrait",
    "layoutMode": "overlay",
    "marginMm": [0, 0, 0, 0],
    "overlay": { "printOffsetMm": [0, 0] }
  },
  "items": [
    { "elementCode": "hospitalName", "visible": true, "style": { "align": "center", "fontSizePt": 11 } },
    { "elementCode": "docTitle", "visible": true, "labelOverride": "门诊处方笺", "style": { "align": "center", "fontSizePt": 14, "bold": true } },
    { "elementCode": "encounterClassText", "visible": true },
    { "elementCode": "rxValidityHint", "visible": true },
    { "elementCode": "deptName", "visible": true },
    { "elementCode": "payTypeText", "visible": true },
    { "elementCode": "rxNo", "visible": true },
    { "elementCode": "patientName", "visible": true },
    { "elementCode": "patientAge", "visible": true },
    { "elementCode": "patientGender", "visible": true },
    { "elementCode": "patientPhone", "visible": true },
    { "elementCode": "authoredDate", "visible": true },
    { "elementCode": "patientIdCard", "visible": true },
    { "elementCode": "diagnosisText", "visible": true },
    { "elementCode": "rpLabel", "visible": true },
    { "elementCode": "prescriptionDrugTable", "visible": true },
    { "elementCode": "prescriberName", "visible": true },
    { "elementCode": "totalAmount", "visible": true },
    { "elementCode": "dispenserName", "visible": true },
    { "elementCode": "checkerName", "visible": true },
    { "elementCode": "reviewerName", "visible": true },
    { "elementCode": "issuerName", "visible": true }
  ]
}'::jsonb,
    version = version + 1
WHERE tenant_id = 0 AND code = 'outpatient_prescription_overlay' AND is_deleted = FALSE;

-- ----- V13__Prescription_drug_table_max_rows_slash.sql -----
-- V13：处方药品表固定 5 行槽，空位由 PDF 渲染斜线补齐（方案 B）

UPDATE rpt_print_element_def
SET box_json = '{"xMm":12,"yMm":58,"widthMm":124,"heightMm":102,"overflow":"clip","maxRows":5}'
WHERE tenant_id = 0
  AND pack_code = 'outpatient_prescription'
  AND element_code = 'prescriptionDrugTable'
  AND is_deleted = FALSE;

-- ----- V14__Prescription_drug_table_extend_columns.sql -----
-- V14：药品明细 table 扩展列定义（途径 / 频次 / 皮试，与 Port 输出及 BRD 对齐）

UPDATE rpt_print_element_def
SET column_defs_json = '[
  {"field":"lineNo","header":"序号"},
  {"field":"drugName","header":"药品名称"},
  {"field":"spec","header":"规格"},
  {"field":"quantity","header":"数量"},
  {"field":"unitText","header":"单位"},
  {"field":"usageText","header":"用法用量"},
  {"field":"methodText","header":"途径"},
  {"field":"rateText","header":"频次"},
  {"field":"skinTestText","header":"皮试"}
]'
WHERE tenant_id = 0
  AND pack_code = 'outpatient_prescription'
  AND element_code = 'prescriptionDrugTable'
  AND is_deleted = FALSE;

-- ----- V15__Prescription_drug_table_infusion_group.sql -----
-- V15：药品明细 table 增加输液成组括线列 groupMarker

UPDATE rpt_print_element_def
SET column_defs_json = '[
  {"field":"lineNo","header":"序号"},
  {"field":"groupMarker","header":""},
  {"field":"drugName","header":"药品名称"},
  {"field":"spec","header":"规格"},
  {"field":"quantity","header":"数量"},
  {"field":"unitText","header":"单位"},
  {"field":"usageText","header":"用法用量"},
  {"field":"methodText","header":"途径"},
  {"field":"rateText","header":"频次"},
  {"field":"skinTestText","header":"皮试"}
]'
WHERE tenant_id = 0
  AND pack_code = 'outpatient_prescription'
  AND element_code = 'prescriptionDrugTable'
  AND is_deleted = FALSE;

-- ----- V16__Prescription_drug_table_column_align.sql -----
-- V16：处方药品明细列默认对齐（序号/括线居中，数量右对齐）

UPDATE rpt_print_element_def
SET column_defs_json = '[
  {"field":"lineNo","header":"序号","align":"center"},
  {"field":"groupMarker","header":"","align":"center"},
  {"field":"drugName","header":"药品名称","align":"left"},
  {"field":"spec","header":"规格","align":"left"},
  {"field":"quantity","header":"数量","align":"right"},
  {"field":"unitText","header":"单位","align":"center"},
  {"field":"usageText","header":"用法用量","align":"left"},
  {"field":"methodText","header":"途径","align":"center"},
  {"field":"rateText","header":"频次","align":"center"},
  {"field":"skinTestText","header":"皮试","align":"center"}
]'
WHERE tenant_id = 0
  AND pack_code = 'outpatient_prescription'
  AND element_code = 'prescriptionDrugTable'
  AND is_deleted = FALSE;

-- ----- V17__Supply_document_report_definitions.sql -----
-- 按 SupplyType 拆分的库房 / 药房 / 科室供应单据 C 类报表定义（PH-11+）

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    created_at, is_deleted
)
SELECT 920001000016, 0, 'supply_cabinet_purchase_order', '【药库】药库订货单',
       'supplyDocumentReportLoader',
       '[{"field":"busNo","label":"单据号"},{"field":"typeEnum","label":"类型"},{"field":"totalPrice","label":"金额"}]',
       TRUE, FALSE, NULL, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'supply_cabinet_purchase_order' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    created_at, is_deleted
)
SELECT 920001000017, 0, 'supply_cabinet_inbound_receipt', '【药库】药库进货单',
       'supplyDocumentReportLoader',
       '[{"field":"busNo","label":"单据号"},{"field":"typeEnum","label":"类型"},{"field":"totalPrice","label":"金额"}]',
       TRUE, FALSE, NULL, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'supply_cabinet_inbound_receipt' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    created_at, is_deleted
)
SELECT 920001000018, 0, 'supply_cabinet_inbound_receipt_inspection', '【药库】药库进货验收单',
       'supplyDocumentReportLoader',
       '[{"field":"busNo","label":"单据号"},{"field":"typeEnum","label":"类型"},{"field":"totalPrice","label":"金额"}]',
       TRUE, FALSE, NULL, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'supply_cabinet_inbound_receipt_inspection' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    created_at, is_deleted
)
SELECT 920001000019, 0, 'supply_cabinet_return_to_supplier', '【药库】药库退货单',
       'supplyDocumentReportLoader',
       '[{"field":"busNo","label":"单据号"},{"field":"typeEnum","label":"类型"},{"field":"totalPrice","label":"金额"}]',
       TRUE, FALSE, NULL, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'supply_cabinet_return_to_supplier' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    created_at, is_deleted
)
SELECT 920001000020, 0, 'supply_cabinet_stock_out', '【药库】药库出库单',
       'supplyDocumentReportLoader',
       '[{"field":"busNo","label":"单据号"},{"field":"typeEnum","label":"类型"},{"field":"totalPrice","label":"金额"}]',
       TRUE, FALSE, NULL, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'supply_cabinet_stock_out' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    created_at, is_deleted
)
SELECT 920001000021, 0, 'supply_cabinet_return_from_downstream', '【药库】药库退库单',
       'supplyDocumentReportLoader',
       '[{"field":"busNo","label":"单据号"},{"field":"typeEnum","label":"类型"},{"field":"totalPrice","label":"金额"}]',
       TRUE, FALSE, NULL, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'supply_cabinet_return_from_downstream' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    created_at, is_deleted
)
SELECT 920001000022, 0, 'supply_cabinet_gain_loss', '【药库】药库损益单',
       'supplyDocumentReportLoader',
       '[{"field":"busNo","label":"单据号"},{"field":"typeEnum","label":"类型"},{"field":"totalPrice","label":"金额"}]',
       TRUE, FALSE, NULL, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'supply_cabinet_gain_loss' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    created_at, is_deleted
)
SELECT 920001000023, 0, 'supply_cabinet_stocktake', '【药库】药库盘点单',
       'supplyDocumentReportLoader',
       '[{"field":"busNo","label":"单据号"},{"field":"typeEnum","label":"类型"},{"field":"totalPrice","label":"金额"}]',
       TRUE, FALSE, NULL, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'supply_cabinet_stocktake' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    created_at, is_deleted
)
SELECT 920001000024, 0, 'supply_cabinet_transfer_in', '【药库】药库调入单',
       'supplyDocumentReportLoader',
       '[{"field":"busNo","label":"单据号"},{"field":"typeEnum","label":"类型"},{"field":"totalPrice","label":"金额"}]',
       TRUE, FALSE, NULL, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'supply_cabinet_transfer_in' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    created_at, is_deleted
)
SELECT 920001000025, 0, 'supply_cabinet_transfer_out', '【药库】药库调出单',
       'supplyDocumentReportLoader',
       '[{"field":"busNo","label":"单据号"},{"field":"typeEnum","label":"类型"},{"field":"totalPrice","label":"金额"}]',
       TRUE, FALSE, NULL, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'supply_cabinet_transfer_out' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    created_at, is_deleted
)
SELECT 920001000026, 0, 'supply_cabinet_transfer_in_request', '【药库】药库调入申请单',
       'supplyDocumentReportLoader',
       '[{"field":"busNo","label":"单据号"},{"field":"typeEnum","label":"类型"},{"field":"totalPrice","label":"金额"}]',
       TRUE, FALSE, NULL, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'supply_cabinet_transfer_in_request' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    created_at, is_deleted
)
SELECT 920001000027, 0, 'supply_pharmacy_requisition', '【药房】药房请领单',
       'supplyDocumentReportLoader',
       '[{"field":"busNo","label":"单据号"},{"field":"typeEnum","label":"类型"},{"field":"totalPrice","label":"金额"}]',
       TRUE, FALSE, NULL, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'supply_pharmacy_requisition' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    created_at, is_deleted
)
SELECT 920001000028, 0, 'supply_pharmacy_stock_in', '【药房】药房入库单',
       'supplyDocumentReportLoader',
       '[{"field":"busNo","label":"单据号"},{"field":"typeEnum","label":"类型"},{"field":"totalPrice","label":"金额"}]',
       TRUE, FALSE, NULL, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'supply_pharmacy_stock_in' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    created_at, is_deleted
)
SELECT 920001000029, 0, 'supply_pharmacy_return_to_upstream', '【药房】药房退库单',
       'supplyDocumentReportLoader',
       '[{"field":"busNo","label":"单据号"},{"field":"typeEnum","label":"类型"},{"field":"totalPrice","label":"金额"}]',
       TRUE, FALSE, NULL, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'supply_pharmacy_return_to_upstream' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    created_at, is_deleted
)
SELECT 920001000030, 0, 'supply_pharmacy_gain_loss', '【药房】药房损益单',
       'supplyDocumentReportLoader',
       '[{"field":"busNo","label":"单据号"},{"field":"typeEnum","label":"类型"},{"field":"totalPrice","label":"金额"}]',
       TRUE, FALSE, NULL, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'supply_pharmacy_gain_loss' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    created_at, is_deleted
)
SELECT 920001000031, 0, 'supply_pharmacy_stocktake', '【药房】药房盘点单',
       'supplyDocumentReportLoader',
       '[{"field":"busNo","label":"单据号"},{"field":"typeEnum","label":"类型"},{"field":"totalPrice","label":"金额"}]',
       TRUE, FALSE, NULL, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'supply_pharmacy_stocktake' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    created_at, is_deleted
)
SELECT 920001000032, 0, 'supply_pharmacy_transfer_in', '【药房】药房调入单',
       'supplyDocumentReportLoader',
       '[{"field":"busNo","label":"单据号"},{"field":"typeEnum","label":"类型"},{"field":"totalPrice","label":"金额"}]',
       TRUE, FALSE, NULL, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'supply_pharmacy_transfer_in' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    created_at, is_deleted
)
SELECT 920001000033, 0, 'supply_pharmacy_transfer_out', '【药房】药房调出单',
       'supplyDocumentReportLoader',
       '[{"field":"busNo","label":"单据号"},{"field":"typeEnum","label":"类型"},{"field":"totalPrice","label":"金额"}]',
       TRUE, FALSE, NULL, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'supply_pharmacy_transfer_out' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    created_at, is_deleted
)
SELECT 920001000034, 0, 'supply_pharmacy_transfer_in_request', '【药房】药房调入申请单',
       'supplyDocumentReportLoader',
       '[{"field":"busNo","label":"单据号"},{"field":"typeEnum","label":"类型"},{"field":"totalPrice","label":"金额"}]',
       TRUE, FALSE, NULL, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'supply_pharmacy_transfer_in_request' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    created_at, is_deleted
)
SELECT 920001000035, 0, 'supply_dept_requisition', '【科室】科室请领单',
       'supplyDocumentReportLoader',
       '[{"field":"busNo","label":"单据号"},{"field":"typeEnum","label":"类型"},{"field":"totalPrice","label":"金额"}]',
       TRUE, FALSE, NULL, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'supply_dept_requisition' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    created_at, is_deleted
)
SELECT 920001000036, 0, 'supply_dept_stock_in', '【科室】科室入库单',
       'supplyDocumentReportLoader',
       '[{"field":"busNo","label":"单据号"},{"field":"typeEnum","label":"类型"},{"field":"totalPrice","label":"金额"}]',
       TRUE, FALSE, NULL, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'supply_dept_stock_in' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    created_at, is_deleted
)
SELECT 920001000037, 0, 'supply_dept_return', '【科室】科室退库单',
       'supplyDocumentReportLoader',
       '[{"field":"busNo","label":"单据号"},{"field":"typeEnum","label":"类型"},{"field":"totalPrice","label":"金额"}]',
       TRUE, FALSE, NULL, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'supply_dept_return' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    created_at, is_deleted
)
SELECT 920001000038, 0, 'supply_dept_gain_loss', '【科室】科室损益单',
       'supplyDocumentReportLoader',
       '[{"field":"busNo","label":"单据号"},{"field":"typeEnum","label":"类型"},{"field":"totalPrice","label":"金额"}]',
       TRUE, FALSE, NULL, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'supply_dept_gain_loss' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    created_at, is_deleted
)
SELECT 920001000039, 0, 'supply_dept_stocktake', '【科室】科室盘点单',
       'supplyDocumentReportLoader',
       '[{"field":"busNo","label":"单据号"},{"field":"typeEnum","label":"类型"},{"field":"totalPrice","label":"金额"}]',
       TRUE, FALSE, NULL, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'supply_dept_stocktake' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    created_at, is_deleted
)
SELECT 920001000040, 0, 'supply_dept_transfer_in', '【科室】科室调入单',
       'supplyDocumentReportLoader',
       '[{"field":"busNo","label":"单据号"},{"field":"typeEnum","label":"类型"},{"field":"totalPrice","label":"金额"}]',
       TRUE, FALSE, NULL, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'supply_dept_transfer_in' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    created_at, is_deleted
)
SELECT 920001000041, 0, 'supply_dept_transfer_out', '【科室】科室调出单',
       'supplyDocumentReportLoader',
       '[{"field":"busNo","label":"单据号"},{"field":"typeEnum","label":"类型"},{"field":"totalPrice","label":"金额"}]',
       TRUE, FALSE, NULL, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'supply_dept_transfer_out' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    created_at, is_deleted
)
SELECT 920001000042, 0, 'supply_dept_device_stocktake', '【科室】科室耗材盘点单',
       'supplyDocumentReportLoader',
       '[{"field":"busNo","label":"单据号"},{"field":"typeEnum","label":"类型"},{"field":"totalPrice","label":"金额"}]',
       TRUE, FALSE, NULL, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'supply_dept_device_stocktake' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    created_at, is_deleted
)
SELECT 920001000043, 0, 'supply_medication_summary_order', '【汇总】药品汇总单',
       'supplyDocumentReportLoader',
       '[{"field":"busNo","label":"单据号"},{"field":"typeEnum","label":"类型"},{"field":"totalPrice","label":"金额"}]',
       TRUE, FALSE, NULL, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'supply_medication_summary_order' AND d.is_deleted = FALSE
);

-- ----- V20__Report_catalog_sql_seed.sql -----
-- V20：08-常用报表清单 全部 SQL 类报表预制种子（draft，设计器完善 SQL 后发布）

-- 已有 Loader 报表（V2/V17）不在此重复插入

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000044, 0, 'outpatient_registration_summary', '门诊挂号量统计', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"门诊挂号量统计","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §2 outpatient_registration_summary',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'outpatient_registration_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000045, 0, 'outpatient_revenue_by_category', '门诊收入分类汇总', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"门诊收入分类汇总","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §2 outpatient_revenue_by_category',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'outpatient_revenue_by_category' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000046, 0, 'outpatient_refund_detail', '门诊退费明细', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"门诊退费明细","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §2 outpatient_refund_detail',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'outpatient_refund_detail' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000047, 0, 'outpatient_arrears_list', '门诊欠费/挂账清单', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"门诊欠费/挂账清单","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §2 outpatient_arrears_list',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'outpatient_arrears_list' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000048, 0, 'outpatient_appointment_arrival_rate', '门诊预约到诊率', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"门诊预约到诊率","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §2 outpatient_appointment_arrival_rate',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'outpatient_appointment_arrival_rate' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000049, 0, 'inpatient_in_hospital_list', '在院患者清单', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"在院患者清单","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §3 inpatient_in_hospital_list',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'inpatient_in_hospital_list' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000050, 0, 'inpatient_discharge_summary', '出院患者统计', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"出院患者统计","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §3 inpatient_discharge_summary',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'inpatient_discharge_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000051, 0, 'inpatient_deposit_balance', '预交金余额汇总', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"预交金余额汇总","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §3 inpatient_deposit_balance',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'inpatient_deposit_balance' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000052, 0, 'inpatient_charge_detail_query', '住院费用明细查询', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"住院费用明细查询","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §3 inpatient_charge_detail_query',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'inpatient_charge_detail_query' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000053, 0, 'inpatient_bed_occupancy_rate', '床位使用率统计', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"床位使用率统计","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §3 inpatient_bed_occupancy_rate',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'inpatient_bed_occupancy_rate' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000054, 0, 'inpatient_insurance_settlement_summary', '住院医保结算汇总', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"住院医保结算汇总","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §3 inpatient_insurance_settlement_summary',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'inpatient_insurance_settlement_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000055, 0, 'physician_workload_summary', '医生工作量统计', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"医生工作量统计","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §4 physician_workload_summary',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'physician_workload_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000056, 0, 'charge_item_revenue_ranking', '收费项目收入排名', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"收费项目收入排名","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §4 charge_item_revenue_ranking',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'charge_item_revenue_ranking' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000057, 0, 'payment_method_summary', '支付方式汇总', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"支付方式汇总","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §4 payment_method_summary',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'payment_method_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000058, 0, 'dept_drug_ratio_report', '科室药占比统计', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"科室药占比统计","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"month","label":"月份","type":"month"},{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"}]', '08 §4 dept_drug_ratio_report',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'dept_drug_ratio_report' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000059, 0, 'arrears_patient_summary', '欠费患者汇总', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"欠费患者汇总","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §4 arrears_patient_summary',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'arrears_patient_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000060, 0, 'daily_revenue_trend', '日收入趋势（按日）', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"日收入趋势（按日）","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"month","label":"月份","type":"month"},{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"}]', '08 §4 daily_revenue_trend',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'daily_revenue_trend' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000061, 0, 'inventory_near_expiry', '近效期药品', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"近效期药品","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"locationId","label":"库房","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §5.3 inventory_near_expiry',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'inventory_near_expiry' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000062, 0, 'inventory_low_stock_alert', '低库存预警', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"低库存预警","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"locationId","label":"库房","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §5.3 inventory_low_stock_alert',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'inventory_low_stock_alert' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000063, 0, 'medication_consumption_ranking', '药品消耗排名', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"药品消耗排名","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"locationId","label":"库房","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §5.3 medication_consumption_ranking',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'medication_consumption_ranking' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000064, 0, 'antibiotic_usage_ddd', '抗菌药物使用强度', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"抗菌药物使用强度","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"month","label":"月份","type":"month"},{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"}]', '08 §5.3 antibiotic_usage_ddd',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'antibiotic_usage_ddd' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000065, 0, 'controlled_drug_register', '麻精药品专册', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"麻精药品专册","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §5.3 controlled_drug_register',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'controlled_drug_register' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000066, 0, 'purchase_inbound_summary', '采购入库汇总', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"采购入库汇总","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §5.3 purchase_inbound_summary',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'purchase_inbound_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000067, 0, 'ward_requisition_summary', '病区领药汇总', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"病区领药汇总","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §5.3 ward_requisition_summary',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'ward_requisition_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000068, 0, 'nurse_workload_summary', '护士工作量统计', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"护士工作量统计","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §6.1 RPT-NRS-01',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'nurse_workload_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000069, 0, 'nurse_workload_detail', '护士工作量明细', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"护士工作量明细","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §6.1 RPT-NRS-02',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'nurse_workload_detail' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000070, 0, 'nurse_execute_by_category', '护士执行分类统计', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"护士执行分类统计","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §6.1 RPT-NRS-03',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'nurse_execute_by_category' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000071, 0, 'nursing_ward_workload_summary', '护理工作量统计（病区）', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"护理工作量统计（病区）","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §6.1 RPT-NRS-04',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'nursing_ward_workload_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000072, 0, 'nurse_verify_workload_summary', '护士核对工作量', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"护士核对工作量","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §6.1 RPT-NRS-05',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'nurse_verify_workload_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000073, 0, 'outpatient_nurse_workload_summary', '门急诊护士工作量', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"门急诊护士工作量","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §6.1 RPT-NRS-06',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'outpatient_nurse_workload_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000074, 0, 'nursing_order_execute_query', '医嘱执行记录查询', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"医嘱执行记录查询","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §6.2 RPT-NRS-10',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'nursing_order_execute_query' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000075, 0, 'nursing_order_pending_list', '未执行医嘱清单', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"未执行医嘱清单","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §6.2 RPT-NRS-11',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'nursing_order_pending_list' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000076, 0, 'iv_infusion_execute_summary', '输液执行统计', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"输液执行统计","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §6.2 RPT-NRS-12',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'iv_infusion_execute_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000077, 0, 'specimen_collect_summary', '采血执行统计', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"采血执行统计","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §6.2 RPT-NRS-13',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'specimen_collect_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000078, 0, 'skin_test_result_summary', '皮试结果统计', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"皮试结果统计","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §6.2 RPT-NRS-14',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'skin_test_result_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000079, 0, 'ward_dispense_receive_summary', '统领摆药/领药汇总', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"统领摆药/领药汇总","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §6.2 RPT-NRS-15',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'ward_dispense_receive_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000080, 0, 'temperature_chart_entry_summary', '体温单录入统计', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"体温单录入统计","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §6.3 RPT-NRS-20',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'temperature_chart_entry_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000081, 0, 'nursing_record_completion_rate', '护理记录完成率', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"护理记录完成率","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §6.3 RPT-NRS-21',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'nursing_record_completion_rate' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000082, 0, 'nursing_care_level_distribution', '护理等级分布', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"护理等级分布","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §6.3 RPT-NRS-22',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'nursing_care_level_distribution' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000083, 0, 'nursing_adverse_event_report', '压疮/跌倒不良事件', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"压疮/跌倒不良事件","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"month","label":"月份","type":"month"},{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"}]', '08 §6.3 RPT-NRS-23',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'nursing_adverse_event_report' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000084, 0, 'nurse_shift_handover_query', '交接班记录查询', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"交接班记录查询","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §6.3 RPT-NRS-24',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'nurse_shift_handover_query' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000085, 0, 'discharge_nursing_days_summary', '出院患者护理日数', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"出院患者护理日数","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §6.3 RPT-NRS-25',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'discharge_nursing_days_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000086, 0, 'outpatient_prescription_summary', '门诊处方统计', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"门诊处方统计","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §7 outpatient_prescription_summary',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'outpatient_prescription_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000087, 0, 'inpatient_order_detail', '住院医嘱明细', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"住院医嘱明细","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §7 inpatient_order_detail',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'inpatient_order_detail' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000088, 0, 'critical_value_report', '危急值处理统计', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"危急值处理统计","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §7 critical_value_report',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'critical_value_report' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000089, 0, 'surgery_case_summary', '手术台次统计', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"手术台次统计","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §7 surgery_case_summary',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'surgery_case_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000090, 0, 'blood_transfusion_detail', '输血记录明细', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"输血记录明细","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §7 blood_transfusion_detail',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'blood_transfusion_detail' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000091, 0, 'antibiotic_prescription_review', '抗菌药物处方点评', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"抗菌药物处方点评","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §7 antibiotic_prescription_review',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'antibiotic_prescription_review' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000092, 0, 'clinical_pathway_enrollment', '临床路径入径统计', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"临床路径入径统计","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"month","label":"月份","type":"month"},{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"}]', '08 §7 clinical_pathway_enrollment',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'clinical_pathway_enrollment' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000093, 0, 'lab_apply_summary', '检验申请统计', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"检验申请统计","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §8 lab_apply_summary',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'lab_apply_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000094, 0, 'exam_appointment_arrival', '检查预约到检统计', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"检查预约到检统计","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §8 exam_appointment_arrival',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'exam_appointment_arrival' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000095, 0, 'lab_positive_rate', '检验阳性率统计', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"检验阳性率统计","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §8 lab_positive_rate',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'lab_positive_rate' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000096, 0, 'exam_device_workload', '检查工作量（设备）', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"检查工作量（设备）","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §8 exam_device_workload',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'exam_device_workload' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000097, 0, 'lab_tat_report', 'TAT 周转时间', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"TAT 周转时间","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §8 lab_tat_report',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'lab_tat_report' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000098, 0, 'health_check_appointment_summary', '体检预约汇总', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"体检预约汇总","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §9 health_check_appointment_summary',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'health_check_appointment_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000099, 0, 'health_check_group_progress', '团检到检进度', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"团检到检进度","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §9 health_check_group_progress',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'health_check_group_progress' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000100, 0, 'emergency_triage_volume', '急诊分诊量统计', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"急诊分诊量统计","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §9 emergency_triage_volume',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'emergency_triage_volume' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000101, 0, 'emergency_ed_los', '急诊抢救滞留时间', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"急诊抢救滞留时间","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §9 emergency_ed_los',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'emergency_ed_los' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000102, 0, 'hospital_daily_revenue', '全院收入日报', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"全院收入日报","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.1 hospital_daily_revenue',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'hospital_daily_revenue' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000103, 0, 'hospital_monthly_revenue', '全院收入月报', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"全院收入月报","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"month","label":"月份","type":"month"},{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"}]', '08 §10.1 hospital_monthly_revenue',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'hospital_monthly_revenue' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000104, 0, 'dept_revenue_ranking', '科室收入排名', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"科室收入排名","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.1 dept_revenue_ranking',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'dept_revenue_ranking' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000105, 0, 'outpatient_avg_cost_per_visit', '门诊次均费用', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"门诊次均费用","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.1 outpatient_avg_cost_per_visit',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'outpatient_avg_cost_per_visit' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000106, 0, 'inpatient_avg_cost_per_case', '住院次均费用', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"住院次均费用","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.1 inpatient_avg_cost_per_case',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'inpatient_avg_cost_per_case' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000107, 0, 'revenue_structure_analysis', '收入结构分析', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"收入结构分析","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.1 revenue_structure_analysis',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'revenue_structure_analysis' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000108, 0, 'dept_performance_export', '科室绩效数据包', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"科室绩效数据包","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"month","label":"月份","type":"month"},{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"}]', '08 §10.1 dept_performance_export',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'dept_performance_export' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000109, 0, 'campus_comparison_report', '院区对比报表', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"院区对比报表","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"month","label":"月份","type":"month"},{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"}]', '08 §10.1 campus_comparison_report',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'campus_comparison_report' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000110, 0, 'outpatient_visit_volume_summary', '门诊人次统计', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"门诊人次统计","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.2 outpatient_visit_volume_summary',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'outpatient_visit_volume_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000111, 0, 'registration_volume_operational', '挂号量统计（运营）', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"挂号量统计（运营）","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.2 registration_volume_operational',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'registration_volume_operational' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000112, 0, 'inpatient_admission_discharge_summary', '住院出入院统计', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"住院出入院统计","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.2 inpatient_admission_discharge_summary',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'inpatient_admission_discharge_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000113, 0, 'surgery_volume_operational', '手术量统计（运营）', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"手术量统计（运营）","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.2 surgery_volume_operational',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'surgery_volume_operational' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000114, 0, 'lab_exam_volume_hospital', '检验检查工作量（全院）', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"检验检查工作量（全院）","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.2 lab_exam_volume_hospital',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'lab_exam_volume_hospital' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000115, 0, 'health_check_volume_summary', '体检业务量统计', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"体检业务量统计","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.2 health_check_volume_summary',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'health_check_volume_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000116, 0, 'emergency_volume_operational', '急诊业务量统计', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"急诊业务量统计","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.2 emergency_volume_operational',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'emergency_volume_operational' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000117, 0, 'appointment_conversion_analysis', '预约转化分析', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"预约转化分析","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.2 appointment_conversion_analysis',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'appointment_conversion_analysis' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000118, 0, 'bed_occupancy_operational_report', '床位使用率', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"床位使用率","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.3 bed_occupancy_operational_report',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'bed_occupancy_operational_report' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000119, 0, 'average_length_of_stay_report', '平均住院日', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"平均住院日","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.3 average_length_of_stay_report',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'average_length_of_stay_report' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000120, 0, 'bed_turnover_rate_report', '床位周转次数', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"床位周转次数","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.3 bed_turnover_rate_report',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'bed_turnover_rate_report' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000121, 0, 'inpatient_census_daily', '在院人数日报', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"在院人数日报","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.3 inpatient_census_daily',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'inpatient_census_daily' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000122, 0, 'prolonged_stay_patient_list', '超长住院患者清单', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"超长住院患者清单","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.3 prolonged_stay_patient_list',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'prolonged_stay_patient_list' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000123, 0, 'pending_discharge_settlement_summary', '预出院/待结算统计', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"预出院/待结算统计","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"wardId","label":"病区","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.3 pending_discharge_settlement_summary',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'pending_discharge_settlement_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000124, 0, 'drug_ratio_operational_report', '药占比报表', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"药占比报表","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.4 drug_ratio_operational_report',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'drug_ratio_operational_report' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000125, 0, 'material_ratio_operational_report', '耗占比报表', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"耗占比报表","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.4 material_ratio_operational_report',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'material_ratio_operational_report' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000126, 0, 'drug_material_ratio_report', '药占比耗占比合并', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"药占比耗占比合并","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.4 drug_material_ratio_report',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'drug_material_ratio_report' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000127, 0, 'insurance_fund_payment_summary', '医保基金支付统计', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"医保基金支付统计","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.4 insurance_fund_payment_summary',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'insurance_fund_payment_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000128, 0, 'drg_data_export', 'DRG 分组结果导出', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"DRG 分组结果导出","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"month","label":"月份","type":"month"},{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"}]', '08 §10.4 drg_data_export',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'drg_data_export' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000129, 0, 'drg_profit_loss_analysis', 'DRG 盈亏分析', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"DRG 盈亏分析","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"month","label":"月份","type":"month"},{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"}]', '08 §10.4 drg_profit_loss_analysis',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'drg_profit_loss_analysis' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000130, 0, 'high_value_consumable_usage', '高值耗材使用统计', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"高值耗材使用统计","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.4 high_value_consumable_usage',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'high_value_consumable_usage' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000131, 0, 'insurance_rejection_detail', '医保拒付/违规明细', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"医保拒付/违规明细","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.4 insurance_rejection_detail',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'insurance_rejection_detail' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000132, 0, 'mr_homepage_quality', '病案首页质量统计', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"病案首页质量统计","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"month","label":"月份","type":"month"},{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"}]', '08 §10.5 mr_homepage_quality',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'mr_homepage_quality' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000133, 0, 'readmission_rate_report', '再入院率统计', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"再入院率统计","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"month","label":"月份","type":"month"},{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"}]', '08 §10.5 readmission_rate_report',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'readmission_rate_report' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000134, 0, 'antibiotic_ddd_hospital', '抗菌药物使用强度（全院）', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"抗菌药物使用强度（全院）","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"month","label":"月份","type":"month"},{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"}]', '08 §10.5 antibiotic_ddd_hospital',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'antibiotic_ddd_hospital' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000135, 0, 'clinical_pathway_coverage_rate', '临床路径覆盖率', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"临床路径覆盖率","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"month","label":"月份","type":"month"},{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"}]', '08 §10.5 clinical_pathway_coverage_rate',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'clinical_pathway_coverage_rate' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000136, 0, 'critical_value_closure_rate', '危急值闭环率', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"危急值闭环率","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.5 critical_value_closure_rate',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'critical_value_closure_rate' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000137, 0, 'surgery_complication_summary', '手术并发症统计', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"手术并发症统计","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"month","label":"月份","type":"month"},{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"}]', '08 §10.5 surgery_complication_summary',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'surgery_complication_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000138, 0, 'adverse_event_report_summary', '不良事件上报统计', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"不良事件上报统计","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"month","label":"月份","type":"month"},{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"}]', '08 §10.5 adverse_event_report_summary',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'adverse_event_report_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000139, 0, 'slot_utilization_report', '号源利用率', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"号源利用率","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"deptId","label":"科室","type":"text"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.6 slot_utilization_report',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'slot_utilization_report' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000140, 0, 'exam_device_utilization', '设备检查利用率', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"设备检查利用率","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.6 exam_device_utilization',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'exam_device_utilization' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000141, 0, 'print_event_daily_summary', '打印日志汇总', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"打印日志汇总","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.6 print_event_daily_summary',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'print_event_daily_summary' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000142, 0, 'audit_report_access', '报表访问审计', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"报表访问审计","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.6 audit_report_access',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'audit_report_access' AND d.is_deleted = FALSE
);

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean, source_type, status,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_template_code,
    query_sql, header_json, footer_json, filter_json, remark,
    created_at, is_deleted
)
SELECT 920001000143, 0, 'system_active_user_summary', '系统活跃用户统计', 'sqlReportLoader', 'sql', 'draft',
       '[]', TRUE, FALSE, NULL,
       '-- 请完善 SQL，参考 whale-doc/report/08-常用报表清单.md\nSELECT NULL::text AS placeholder WHERE 1=0', '{"title":"系统活跃用户统计","subtitle":"{{dateRange}}","leftText":"","rightText":""}', '{"notes":"","lines":["制表人：________","审核人：________"]}', '[{"field":"dateFrom","label":"开始日期","type":"date"},{"field":"dateTo","label":"结束日期","type":"date"},{"field":"keyword","label":"关键词","type":"text"}]', '08 §10.6 system_active_user_summary',
       CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM rpt_definition d
    WHERE d.tenant_id = 0 AND d.report_code = 'system_active_user_summary' AND d.is_deleted = FALSE
);

-- ----- V21__Report_header_lines.sql -----
-- V21：表头 JSON 升级为 lines[] 动态行（兼容旧 leftText/rightText）

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

-- ----- V22__Report_definition_default_tenant_seed.sql -----
-- V22：将平台出厂报表定义（tenant_id=0）复制到默认院点租户（whale_tenants.id=1）
-- 清库重跑后业务登录 tenant=1，列表只查当前租户，需院点侧有一份可编辑副本。

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
    pdf_template_code,
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
    src.pdf_template_code,
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

-- ----- V27 种子修正（DDL 已并入 V1）-----
UPDATE rpt_definition SET status = 'published' WHERE is_deleted = FALSE AND (status IS NULL OR status = '');
UPDATE rpt_definition SET source_type = 'loader' WHERE is_deleted = FALSE AND (source_type IS NULL OR source_type = '');
