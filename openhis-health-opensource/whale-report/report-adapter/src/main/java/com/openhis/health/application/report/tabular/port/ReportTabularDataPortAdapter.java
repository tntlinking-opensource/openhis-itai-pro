/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.report.tabular.port;
import com.openhis.health.application.report.tabular.dataset.ClinicalCClassReportDatasetService;
import com.openhis.health.application.report.tabular.dataset.InventoryLedgerReportDatasetService;
import com.openhis.health.application.report.tabular.dataset.WarehouseExtendedReportDatasetService;
import com.openhis.health.application.report.tabular.dataset.WarehouseDocumentReportDatasetService;
import com.openhis.health.application.report.tabular.dataset.FinancialCClassReportDatasetService;
import com.openhis.health.application.report.tabular.dataset.MedicationDispenseSummaryReportDatasetService;
import com.openhis.health.application.report.tabular.dataset.InpatientDailySettlementReportDatasetService;
import com.openhis.health.application.report.tabular.dataset.OutpatientDailySettlementReportDatasetService;
import com.openhis.health.application.report.support.ReportDatasetParamSupport;
import com.openhis.whale.report.contracts.supply.SupplyDocumentReportCatalog;

import com.openhis.whale.report.domain.port.tabular.ReportTabularDataPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

@Component
@ConditionalOnClass(ReportTabularDataPort.class)
@ConditionalOnProperty(name = "openhis.modules.report.enabled", havingValue = "true")
public class ReportTabularDataPortAdapter implements ReportTabularDataPort {

    private static final String REPORT_OUTPATIENT_DAILY_SETTLEMENT = "outpatient_daily_settlement";
    private static final String REPORT_INPATIENT_DAILY_SETTLEMENT = "inpatient_daily_settlement";
    private static final String REPORT_MEDICATION_DISPENSE_SUMMARY = "medication_dispense_summary";
    private static final String REPORT_DEPT_REVENUE = "dept_revenue_report";
    private static final String REPORT_CASHIER_DAILY = "cashier_daily_report";
    private static final String REPORT_INVOICE_USAGE = "invoice_usage_report";
    private static final String REPORT_INSURANCE_RECONCILIATION = "insurance_reconciliation";
    private static final String REPORT_WAREHOUSE_DOCUMENT = "warehouse_document";
    private static final String REPORT_WAREHOUSE_DOCUMENT_DETAIL = "warehouse_document_detail";
    private static final String REPORT_WAREHOUSE_TRANSFER = "warehouse_transfer";
    private static final String REPORT_INVENTORY_LEDGER = "inventory_ledger";
    private static final String REPORT_INVENTORY_STOCKTAKE = "inventory_stocktake";
    private static final String REPORT_ORDER_EXECUTE_BATCH = "order_execute_batch";
    private static final String REPORT_LAB_WORKLIST = "lab_worklist";
    private static final String REPORT_PIVAS_BATCH_SUMMARY = "pivas_batch_summary";

    private final OutpatientDailySettlementReportDatasetService outpatientDailySettlementDatasetService;
    private final InpatientDailySettlementReportDatasetService inpatientDailySettlementDatasetService;
    private final MedicationDispenseSummaryReportDatasetService medicationDispenseSummaryDatasetService;
    private final FinancialCClassReportDatasetService financialCClassReportDatasetService;
    private final WarehouseDocumentReportDatasetService warehouseDocumentDatasetService;
    private final WarehouseExtendedReportDatasetService warehouseExtendedReportDatasetService;
    private final InventoryLedgerReportDatasetService inventoryLedgerDatasetService;
    private final ClinicalCClassReportDatasetService clinicalCClassReportDatasetService;

    public ReportTabularDataPortAdapter(
            OutpatientDailySettlementReportDatasetService outpatientDailySettlementDatasetService,
            InpatientDailySettlementReportDatasetService inpatientDailySettlementDatasetService,
            MedicationDispenseSummaryReportDatasetService medicationDispenseSummaryDatasetService,
            FinancialCClassReportDatasetService financialCClassReportDatasetService,
            WarehouseDocumentReportDatasetService warehouseDocumentDatasetService,
            WarehouseExtendedReportDatasetService warehouseExtendedReportDatasetService,
            InventoryLedgerReportDatasetService inventoryLedgerDatasetService,
            ClinicalCClassReportDatasetService clinicalCClassReportDatasetService) {
        this.outpatientDailySettlementDatasetService = outpatientDailySettlementDatasetService;
        this.inpatientDailySettlementDatasetService = inpatientDailySettlementDatasetService;
        this.medicationDispenseSummaryDatasetService = medicationDispenseSummaryDatasetService;
        this.financialCClassReportDatasetService = financialCClassReportDatasetService;
        this.warehouseDocumentDatasetService = warehouseDocumentDatasetService;
        this.warehouseExtendedReportDatasetService = warehouseExtendedReportDatasetService;
        this.inventoryLedgerDatasetService = inventoryLedgerDatasetService;
        this.clinicalCClassReportDatasetService = clinicalCClassReportDatasetService;
    }

    @Override
    public Map<String, Object> load(String reportCode, Map<String, Object> params) {
        String code = ReportDatasetParamSupport.trimReportCode(reportCode);
        if (!StringUtils.hasText(code)) {
            return Map.of();
        }
        Map<String, Object> scoped = ReportDatasetParamSupport.copyParams(params);
        if (REPORT_OUTPATIENT_DAILY_SETTLEMENT.equals(code)) {
            return outpatientDailySettlementDatasetService.build(scoped);
        }
        if (REPORT_INPATIENT_DAILY_SETTLEMENT.equals(code)) {
            return inpatientDailySettlementDatasetService.build(scoped);
        }
        if (REPORT_MEDICATION_DISPENSE_SUMMARY.equals(code)) {
            return medicationDispenseSummaryDatasetService.build(scoped);
        }
        if (REPORT_DEPT_REVENUE.equals(code)) {
            return financialCClassReportDatasetService.buildDeptRevenue(scoped);
        }
        if (REPORT_CASHIER_DAILY.equals(code)) {
            return financialCClassReportDatasetService.buildCashierDaily(scoped);
        }
        if (REPORT_INVOICE_USAGE.equals(code)) {
            return financialCClassReportDatasetService.buildInvoiceUsage(scoped);
        }
        if (REPORT_INSURANCE_RECONCILIATION.equals(code)) {
            return financialCClassReportDatasetService.buildInsuranceReconciliation(scoped);
        }
        if (REPORT_WAREHOUSE_DOCUMENT.equals(code)) {
            return warehouseDocumentDatasetService.build(scoped);
        }
        if (REPORT_WAREHOUSE_DOCUMENT_DETAIL.equals(code)) {
            return warehouseExtendedReportDatasetService.buildDocumentDetail(scoped);
        }
        if (REPORT_WAREHOUSE_TRANSFER.equals(code)) {
            return warehouseExtendedReportDatasetService.buildTransfer(scoped);
        }
        if (REPORT_INVENTORY_LEDGER.equals(code)) {
            return inventoryLedgerDatasetService.build(scoped);
        }
        if (REPORT_INVENTORY_STOCKTAKE.equals(code)) {
            return warehouseExtendedReportDatasetService.buildStocktake(scoped);
        }
        if (REPORT_ORDER_EXECUTE_BATCH.equals(code)) {
            return clinicalCClassReportDatasetService.buildOrderExecuteBatch(scoped);
        }
        if (REPORT_LAB_WORKLIST.equals(code)) {
            return clinicalCClassReportDatasetService.buildLabWorklist(scoped);
        }
        if (REPORT_PIVAS_BATCH_SUMMARY.equals(code)) {
            return clinicalCClassReportDatasetService.buildPivasBatchSummary(scoped);
        }
        return SupplyDocumentReportCatalog.findByReportCode(code)
                .map(spec -> warehouseDocumentDatasetService.buildBySupplyType(scoped, spec.supplyTypeCode()))
                .orElseGet(Map::of);
    }
}
