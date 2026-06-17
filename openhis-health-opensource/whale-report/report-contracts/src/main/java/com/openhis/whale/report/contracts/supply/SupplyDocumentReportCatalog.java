package com.openhis.whale.report.contracts.supply;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 库房 / 药房 / 科室供应单据 C 类报表目录（与 {@code SupplyType} code 一一对应）。
 */
public final class SupplyDocumentReportCatalog {

    private static final List<SupplyDocumentReportSpec> ALL = List.of(
            spec("supply_cabinet_purchase_order", "【药库】药库订货单", "CABINET_PURCHASE_ORDER", 110),
            spec("supply_cabinet_inbound_receipt", "【药库】药库进货单", "CABINET_INBOUND_RECEIPT", 120),
            spec(
                    "supply_cabinet_inbound_receipt_inspection",
                    "【药库】药库进货验收单",
                    "CABINET_INBOUND_RECEIPT_INSPECTION",
                    130),
            spec("supply_cabinet_return_to_supplier", "【药库】药库退货单", "CABINET_RETURN_TO_SUPPLIER", 140),
            spec("supply_cabinet_stock_out", "【药库】药库出库单", "CABINET_STOCK_OUT", 150),
            spec(
                    "supply_cabinet_return_from_downstream",
                    "【药库】药库退库单",
                    "CABINET_RETURN_FROM_DOWNSTREAM",
                    160),
            spec("supply_cabinet_gain_loss", "【药库】药库损益单", "CABINET_GAIN_LOSS", 170),
            spec("supply_cabinet_stocktake", "【药库】药库盘点单", "CABINET_STOCKTAKE", 180),
            spec("supply_cabinet_transfer_in", "【药库】药库调入单", "CABINET_TRANSFER_IN", 190),
            spec("supply_cabinet_transfer_out", "【药库】药库调出单", "CABINET_TRANSFER_OUT", 200),
            spec(
                    "supply_cabinet_transfer_in_request",
                    "【药库】药库调入申请单",
                    "CABINET_TRANSFER_IN_REQUEST",
                    210),
            spec("supply_pharmacy_requisition", "【药房】药房请领单", "PHARMACY_REQUISITION", 310),
            spec("supply_pharmacy_stock_in", "【药房】药房入库单", "PHARMACY_STOCK_IN", 320),
            spec("supply_pharmacy_return_to_upstream", "【药房】药房退库单", "PHARMACY_RETURN_TO_UPSTREAM", 330),
            spec("supply_pharmacy_gain_loss", "【药房】药房损益单", "PHARMACY_GAIN_LOSS", 340),
            spec("supply_pharmacy_stocktake", "【药房】药房盘点单", "PHARMACY_STOCKTAKE", 350),
            spec("supply_pharmacy_transfer_in", "【药房】药房调入单", "PHARMACY_TRANSFER_IN", 360),
            spec("supply_pharmacy_transfer_out", "【药房】药房调出单", "PHARMACY_TRANSFER_OUT", 370),
            spec(
                    "supply_pharmacy_transfer_in_request",
                    "【药房】药房调入申请单",
                    "PHARMACY_TRANSFER_IN_REQUEST",
                    380),
            spec("supply_dept_requisition", "【科室】科室请领单", "DEPT_REQUISITION", 410),
            spec("supply_dept_stock_in", "【科室】科室入库单", "DEPT_STOCK_IN", 420),
            spec("supply_dept_return", "【科室】科室退库单", "DEPT_RETURN", 430),
            spec("supply_dept_gain_loss", "【科室】科室损益单", "DEPT_GAIN_LOSS", 440),
            spec("supply_dept_stocktake", "【科室】科室盘点单", "DEPT_STOCKTAKE", 450),
            spec("supply_dept_transfer_in", "【科室】科室调入单", "DEPT_TRANSFER_IN", 460),
            spec("supply_dept_transfer_out", "【科室】科室调出单", "DEPT_TRANSFER_OUT", 470),
            spec("supply_dept_device_stocktake", "【科室】科室耗材盘点单", "DEPT_DEVICE_STOCKTAKE", 480),
            spec("supply_medication_summary_order", "【汇总】药品汇总单", "MEDICATION_SUMMARY_ORDER", 510));

    private SupplyDocumentReportCatalog() {}

    public static List<SupplyDocumentReportSpec> all() {
        return ALL;
    }

    public static Optional<SupplyDocumentReportSpec> findByReportCode(String reportCode) {
        if (reportCode == null || reportCode.isBlank()) {
            return Optional.empty();
        }
        String code = reportCode.trim();
        return ALL.stream().filter(spec -> spec.reportCode().equals(code)).findFirst();
    }

    public static boolean isSupplyDocumentReport(String reportCode) {
        return findByReportCode(reportCode).isPresent();
    }

    public static List<SupplyDocumentReportSpec> sorted() {
        return ALL.stream().sorted(Comparator.comparingInt(SupplyDocumentReportSpec::sortOrder)).toList();
    }

    private static SupplyDocumentReportSpec spec(
            String reportCode, String reportName, String supplyTypeCode, int sortOrder) {
        return new SupplyDocumentReportSpec(reportCode, reportName, supplyTypeCode, sortOrder);
    }
}
