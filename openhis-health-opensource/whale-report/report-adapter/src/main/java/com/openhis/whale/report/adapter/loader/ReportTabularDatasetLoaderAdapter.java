/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.report.adapter.loader;
import com.openhis.whale.report.adapter.dataset.ClinicalCClassReportDatasetService;
import com.openhis.whale.report.adapter.dataset.InventoryLedgerReportDatasetService;
import com.openhis.whale.report.adapter.dataset.WarehouseExtendedReportDatasetService;
import com.openhis.whale.report.adapter.dataset.WarehouseDocumentReportDatasetService;
import com.openhis.whale.report.adapter.dataset.FinancialCClassReportDatasetService;
import com.openhis.whale.report.adapter.dataset.MedicationDispenseSummaryReportDatasetService;
import com.openhis.whale.report.adapter.dataset.InpatientDailySettlementReportDatasetService;
import com.openhis.whale.report.adapter.dataset.OutpatientDailySettlementReportDatasetService;
import com.openhis.whale.report.adapter.support.ReportDatasetParamSupport;
import com.openhis.whale.report.contracts.catalog.supply.SupplyDocumentReportCatalog;

import com.openhis.whale.report.domain.constants.ReportLoaderCodes;
import com.openhis.whale.report.contracts.loader.ReportTabularDatasetLoader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import com.openhis.whale.core.application.util.StringUtils;

import java.util.Map;

/**
 * C 类 {@link ReportTabularDatasetLoader} 实现：按 {@code reportCode} 路由至各 ReportDatasetService。
 *
 * <p>Loader 层（application）不直接依赖 HIS；本类在 adapter 内查库组装 rows/columns/summary。
 * 未匹配的 ReportCode 尝试 {@link SupplyDocumentReportCatalog} 出入库单据动态码。</p>
 */
@Component
@ConditionalOnClass(ReportTabularDatasetLoader.class)
@ConditionalOnProperty(name = "openhis.modules.report.enabled", havingValue = "true")
public class ReportTabularDatasetLoaderAdapter implements ReportTabularDatasetLoader {

    private final OutpatientDailySettlementReportDatasetService outpatientDailySettlementDatasetService;
    private final InpatientDailySettlementReportDatasetService inpatientDailySettlementDatasetService;
    private final MedicationDispenseSummaryReportDatasetService medicationDispenseSummaryDatasetService;
    private final FinancialCClassReportDatasetService financialCClassReportDatasetService;
    private final WarehouseDocumentReportDatasetService warehouseDocumentDatasetService;
    private final WarehouseExtendedReportDatasetService warehouseExtendedReportDatasetService;
    private final InventoryLedgerReportDatasetService inventoryLedgerDatasetService;
    private final ClinicalCClassReportDatasetService clinicalCClassReportDatasetService;

    public ReportTabularDatasetLoaderAdapter(
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

    /**
     * 按 reportCode 路由至 adapter 内 DatasetService。
     *
     * @param reportCode 报表编码
     * @param params     筛选参数（dateFrom/dateTo/keyword 等）
     * @return rows + summary；未匹配返回空 Map
     */
    @Override
    public Map<String, Object> load(String reportCode, Map<String, Object> params) {
        String code = ReportDatasetParamSupport.trimReportCode(reportCode);
        if (!StringUtils.isNotEmpty(code)) {
            // 空编码直接返回，避免下游 NPE
            return Map.of();
        }
        Map<String, Object> scoped = ReportDatasetParamSupport.copyParams(params);
        if (ReportLoaderCodes.OUTPATIENT_DAILY_SETTLEMENT.equals(code)) {
            return outpatientDailySettlementDatasetService.build(scoped);
        }
        if (ReportLoaderCodes.INPATIENT_DAILY_SETTLEMENT.equals(code)) {
            return inpatientDailySettlementDatasetService.build(scoped);
        }
        if (ReportLoaderCodes.MEDICATION_DISPENSE_SUMMARY.equals(code)) {
            return medicationDispenseSummaryDatasetService.build(scoped);
        }
        if (ReportLoaderCodes.DEPT_REVENUE.equals(code)) {
            return financialCClassReportDatasetService.buildDeptRevenue(scoped);
        }
        if (ReportLoaderCodes.CASHIER_DAILY.equals(code)) {
            return financialCClassReportDatasetService.buildCashierDaily(scoped);
        }
        if (ReportLoaderCodes.INVOICE_USAGE.equals(code)) {
            return financialCClassReportDatasetService.buildInvoiceUsage(scoped);
        }
        if (ReportLoaderCodes.INSURANCE_RECONCILIATION.equals(code)) {
            return financialCClassReportDatasetService.buildInsuranceReconciliation(scoped);
        }
        if (ReportLoaderCodes.WAREHOUSE_DOCUMENT.equals(code)) {
            return warehouseDocumentDatasetService.build(scoped);
        }
        if (ReportLoaderCodes.WAREHOUSE_DOCUMENT_DETAIL.equals(code)) {
            return warehouseExtendedReportDatasetService.buildDocumentDetail(scoped);
        }
        if (ReportLoaderCodes.WAREHOUSE_TRANSFER.equals(code)) {
            return warehouseExtendedReportDatasetService.buildTransfer(scoped);
        }
        if (ReportLoaderCodes.INVENTORY_LEDGER.equals(code)) {
            return inventoryLedgerDatasetService.build(scoped);
        }
        if (ReportLoaderCodes.INVENTORY_STOCKTAKE.equals(code)) {
            return warehouseExtendedReportDatasetService.buildStocktake(scoped);
        }
        if (ReportLoaderCodes.ORDER_EXECUTE_BATCH.equals(code)) {
            return clinicalCClassReportDatasetService.buildOrderExecuteBatch(scoped);
        }
        if (ReportLoaderCodes.LAB_WORKLIST.equals(code)) {
            return clinicalCClassReportDatasetService.buildLabWorklist(scoped);
        }
        if (ReportLoaderCodes.PIVAS_BATCH_SUMMARY.equals(code)) {
            return clinicalCClassReportDatasetService.buildPivasBatchSummary(scoped);
        }
        // 动态出入库单据：按 SupplyDocumentReportCatalog 解析 supplyTypeCode
        return SupplyDocumentReportCatalog.findByReportCode(code)
                .map(spec -> warehouseDocumentDatasetService.buildBySupplyType(scoped, spec.supplyTypeCode()))
                .orElseGet(Map::of);
    }
}
