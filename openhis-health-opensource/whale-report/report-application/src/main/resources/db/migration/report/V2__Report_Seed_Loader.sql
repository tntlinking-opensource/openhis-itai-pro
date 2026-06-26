-- Whale Report V2：Loader 报表定义种子（自 report/V3 提取，不含 print 表）
-- 仅 INSERT rpt_definition；B 类画布打印种子见 health/db/migration/health（prt_*）
INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
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
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
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

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
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
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
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

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
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
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
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

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
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
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
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
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
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
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
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
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
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
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
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
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
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
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
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
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
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

INSERT INTO rpt_definition (
    id, tenant_id, report_code, name, loader_bean,
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
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
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
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
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
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
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
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
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
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
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
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
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
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
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
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
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
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
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
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
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
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
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
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
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
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
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
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
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
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
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
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
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
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
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
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
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
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
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
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
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
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
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
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
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
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
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
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
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
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
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
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
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
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
    default_columns_json, allow_column_customize, export_pdf_enabled, pdf_print_code,
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
