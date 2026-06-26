package com.openhis.whale.report.contracts.catalog.supply;

/**
 * 供应单据 C 类报表元数据（reportCode ↔ {@code wkf_supply_request.type_enum}）。
 *
 * @param reportCode     C 类报表编码
 * @param reportName     展示名称
 * @param supplyTypeCode 供应类型枚举 code（Port 查库用）
 * @param sortOrder      目录排序权重
 */
public record SupplyDocumentReportSpec(
        String reportCode,
        String reportName,
        String supplyTypeCode,
        int sortOrder) {}
