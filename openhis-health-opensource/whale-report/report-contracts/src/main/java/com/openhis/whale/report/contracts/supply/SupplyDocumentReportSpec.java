package com.openhis.whale.report.contracts.supply;

/**
 * 供应单据 C 类报表元数据（reportCode ↔ {@code wkf_supply_request.type_enum}）。
 */
public record SupplyDocumentReportSpec(
        String reportCode,
        String reportName,
        String supplyTypeCode,
        int sortOrder) {}
