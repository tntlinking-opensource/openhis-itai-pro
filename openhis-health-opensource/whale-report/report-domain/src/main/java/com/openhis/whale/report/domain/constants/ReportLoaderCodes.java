package com.openhis.whale.report.domain.constants;

/**
 * C 类出厂 Loader 报表编码（与 {@code rpt_definition.report_code}、Port 路由一致）。
 */
public final class ReportLoaderCodes {

    /** 门诊日结（OP-05）。 */
    public static final String OUTPATIENT_DAILY_SETTLEMENT = "outpatient_daily_settlement";
    /** 住院日结（IP-34）。 */
    public static final String INPATIENT_DAILY_SETTLEMENT = "inpatient_daily_settlement";
    /** 发药汇总（PH-01）。 */
    public static final String MEDICATION_DISPENSE_SUMMARY = "medication_dispense_summary";
    /** 科室收入（FIN-01）。 */
    public static final String DEPT_REVENUE = "dept_revenue_report";
    /** 收费员缴款日报（OP-06）。 */
    public static final String CASHIER_DAILY = "cashier_daily_report";
    /** 发票使用明细（FIN-02）。 */
    public static final String INVOICE_USAGE = "invoice_usage_report";
    /** 医保对账（FIN-03）。 */
    public static final String INSURANCE_RECONCILIATION = "insurance_reconciliation";
    /** 出库/入库单（PH-02）。 */
    public static final String WAREHOUSE_DOCUMENT = "warehouse_document";
    /** 库房单据明细（PH-09）。 */
    public static final String WAREHOUSE_DOCUMENT_DETAIL = "warehouse_document_detail";
    /** 库房调拨（PH-10）。 */
    public static final String WAREHOUSE_TRANSFER = "warehouse_transfer";
    /** 库存台账（PH-03）。 */
    public static final String INVENTORY_LEDGER = "inventory_ledger";
    /** 库存盘点（PH-05）。 */
    public static final String INVENTORY_STOCKTAKE = "inventory_stocktake";
    /** 医嘱执行批次（IP-09）。 */
    public static final String ORDER_EXECUTE_BATCH = "order_execute_batch";
    /** 检验工作清单（LAB-05）。 */
    public static final String LAB_WORKLIST = "lab_worklist";
    /** 静配批次汇总（PIVAS-02）。 */
    public static final String PIVAS_BATCH_SUMMARY = "pivas_batch_summary";

    private ReportLoaderCodes() {
    }
}
