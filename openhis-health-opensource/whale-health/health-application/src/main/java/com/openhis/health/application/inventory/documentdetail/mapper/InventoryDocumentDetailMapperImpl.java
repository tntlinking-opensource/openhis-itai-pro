/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.documentdetail.mapper;

import com.openhis.health.application.inventory.documentdetail.dto.DocumentDetailExportRowDto;
import com.openhis.health.application.inventory.documentdetail.dto.DocumentDetailRowDto;
import com.openhis.health.application.inventory.documentdetail.request.DocumentDetailFilterRequest;
import com.openhis.health.application.inventory.documentdetail.request.PageDocumentDetailsRequest;
import com.openhis.health.application.inventory.documentdetail.response.ExportDocumentDetailsResponse;
import com.openhis.health.application.inventory.documentdetail.response.PageDocumentDetailsResponse;
import com.openhis.health.application.inventory.documentdetail.response.SummarizeDocumentDetailsResponse;
import com.openhis.health.application.inventory.documentdetail.support.DocumentDetailDomainScope;
import com.openhis.health.domain.share.constants.DocumentDetailExportConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.InventoryWarehouseDetailExportConstants;
import com.openhis.whale.core.application.util.TabularColumnDef;
import com.openhis.whale.core.application.util.TabularExcelExporter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link InventoryDocumentDetailMapper} 实现：字段搬运与 Tabular Excel 写出。
 */
@Component
public class InventoryDocumentDetailMapperImpl implements InventoryDocumentDetailMapper {

    /**
     * 分页请求筛选段 → 通用筛选入参。
     *
     * @param request 分页请求；可为 null
     * @return 筛选入参
     */
    @Override
    public DocumentDetailFilterRequest toFilter(PageDocumentDetailsRequest request) {
        DocumentDetailFilterRequest filter = new DocumentDetailFilterRequest();
        if (request == null) {
            return filter;
        }
        filter.setDomainScope(request.getDomainScope());
        filter.setTypeEnum(request.getTypeEnum());
        filter.setSourceLocationId(request.getSourceLocationId());
        filter.setPurposeLocationId(request.getPurposeLocationId());
        filter.setApprovalTimeStart(request.getApprovalTimeStart());
        filter.setApprovalTimeEnd(request.getApprovalTimeEnd());
        filter.setProductKeyword(request.getProductKeyword());
        filter.setLotNumberKeyword(request.getLotNumberKeyword());
        filter.setBusNo(request.getBusNo());
        return filter;
    }

    /**
     * 装配分页响应。
     *
     * @param records  明细行
     * @param total    总行数
     * @param pageNo   页码
     * @param pageSize 页大小
     * @return 分页响应
     */
    @Override
    public PageDocumentDetailsResponse toPageDocumentDetailsResponse(
        List<DocumentDetailRowDto> records,
        long total,
        int pageNo,
        int pageSize
    ) {
        PageDocumentDetailsResponse response = new PageDocumentDetailsResponse();
        response.setTotal(total);
        response.setPageNo(pageNo);
        response.setPageSize(pageSize);
        if (records == null) {
            response.setRecords(new ArrayList<>());
        } else {
            response.setRecords(records);
        }
        return response;
    }

    /**
     * 装配合计响应。
     *
     * @param totalPriceSum   金额合计
     * @param itemQuantitySum 展示数量合计
     * @param rowCount        行数
     * @return 合计响应
     */
    @Override
    public SummarizeDocumentDetailsResponse toSummarizeDocumentDetailsResponse(
        BigDecimal totalPriceSum,
        BigDecimal itemQuantitySum,
        long rowCount
    ) {
        SummarizeDocumentDetailsResponse response = new SummarizeDocumentDetailsResponse();
        response.setTotalPriceSum(totalPriceSum);
        response.setItemQuantitySum(itemQuantitySum);
        response.setRowCount(rowCount);
        return response;
    }

    /**
     * 列表行 → Excel 导出行。
     *
     * @param rows 展示行
     * @return 导出行
     */
    @Override
    public List<DocumentDetailExportRowDto> toExportRows(List<DocumentDetailRowDto> rows) {
        List<DocumentDetailExportRowDto> exportRows = new ArrayList<>();
        if (rows == null) {
            return exportRows;
        }
        for (DocumentDetailRowDto row : rows) {
            if (row == null) {
                continue;
            }
            DocumentDetailExportRowDto exportRow = new DocumentDetailExportRowDto();
            exportRow.setBusNo(row.getBusNo());
            exportRow.setOriginalBusNo(row.getOriginalBusNo());
            exportRow.setTypeEnumText(row.getTypeEnumText());
            exportRow.setSourceLocationName(row.getSourceLocationName());
            exportRow.setPurposeLocationName(row.getPurposeLocationName());
            exportRow.setConsumingOrganizationName(row.getConsumingOrganizationName());
            exportRow.setSupplierName(row.getSupplierName());
            exportRow.setApplicantName(row.getApplicantName());
            exportRow.setApproverName(row.getApproverName());
            exportRow.setPractitionerName(row.getPractitionerName());
            exportRow.setApplyTime(this.formatDateTime(row.getApplyTime()));
            exportRow.setApprovalTime(this.formatDateTime(row.getApprovalTime()));
            exportRow.setProductName(row.getProductName());
            exportRow.setProductBusNo(row.getProductBusNo());
            exportRow.setSpecification(row.getSpecification());
            exportRow.setManufacturer(row.getManufacturer());
            exportRow.setLotNumber(row.getLotNumber());
            exportRow.setUnitCodeDictText(row.getUnitCodeDictText());
            exportRow.setItemQuantity(this.formatDecimal(row.getItemQuantity()));
            exportRow.setPrice(this.formatDecimal(row.getPrice()));
            exportRow.setTotalPrice(this.formatDecimal(row.getTotalPrice()));
            exportRow.setBookQuantity(this.formatDecimal(row.getBookQuantity()));
            exportRow.setActualQuantity(this.formatDecimal(row.getActualQuantity()));
            exportRow.setGainLossQuantity(this.formatDecimal(row.getGainLossQuantity()));
            exportRow.setActualAmount(this.formatDecimal(row.getActualAmount()));
            exportRow.setGainLossAmount(this.formatDecimal(row.getGainLossAmount()));
            exportRow.setRemake(row.getRemake());
            exportRows.add(exportRow);
        }
        return exportRows;
    }

    /**
     * 写出 xlsx 字节。
     *
     * @param exportRows 导出行
     * @return xlsx 字节
     */
    @Override
    public byte[] buildExcelBytes(List<DocumentDetailExportRowDto> exportRows) {
        List<TabularColumnDef> columns = this.buildExportColumns();
        List<Map<String, Object>> rowMaps = new ArrayList<>();
        if (exportRows != null) {
            for (DocumentDetailExportRowDto exportRow : exportRows) {
                rowMaps.add(this.toRowMap(exportRow, columns));
            }
        }
        return TabularExcelExporter.exportToXlsxBytes(DocumentDetailExportConstants.SHEET_NAME, columns, rowMaps);
    }

    /**
     * 构建导出文件名（含时间戳）。
     *
     * @param scope 业务域
     * @return 文件名
     */
    @Override
    public String buildExportFileName(DocumentDetailDomainScope scope) {
        String prefix = scope.getExportFileNamePrefix();
        String ts = LocalDateTime.now().format(GeneralSharedConstants.EXPORT_FILE_TIMESTAMP_FORMATTER);
        return prefix + "_" + ts + ".xlsx";
    }

    /**
     * 装配导出响应。
     *
     * @param fileName  文件名
     * @param fileBytes 字节
     * @return 导出响应
     */
    @Override
    public ExportDocumentDetailsResponse toExportDocumentDetailsResponse(String fileName, byte[] fileBytes) {
        ExportDocumentDetailsResponse response = new ExportDocumentDetailsResponse();
        response.setFileName(fileName);
        response.setContentType(InventoryWarehouseDetailExportConstants.XLSX_CONTENT_TYPE);
        response.setFileBytes(fileBytes);
        return response;
    }

    /**
     * 导出列定义（并集稀疏列）。
     *
     * @return 列列表
     */
    private List<TabularColumnDef> buildExportColumns() {
        List<TabularColumnDef> columns = new ArrayList<>();
        columns.add(new TabularColumnDef("busNo", "单据号"));
        columns.add(new TabularColumnDef("originalBusNo", "来源单号"));
        columns.add(new TabularColumnDef("typeEnumText", "单据类型"));
        columns.add(new TabularColumnDef("sourceLocationName", "源库位"));
        columns.add(new TabularColumnDef("purposeLocationName", "目的库位"));
        columns.add(new TabularColumnDef("consumingOrganizationName", "领用科室"));
        columns.add(new TabularColumnDef("supplierName", "供应商"));
        columns.add(new TabularColumnDef("applicantName", "申请人"));
        columns.add(new TabularColumnDef("approverName", "审批人"));
        columns.add(new TabularColumnDef("practitionerName", "经手人"));
        columns.add(new TabularColumnDef("applyTime", "申请时间"));
        columns.add(new TabularColumnDef("approvalTime", "审批时间"));
        columns.add(new TabularColumnDef("productName", "品名"));
        columns.add(new TabularColumnDef("productBusNo", "编码"));
        columns.add(new TabularColumnDef("specification", "规格"));
        columns.add(new TabularColumnDef("manufacturer", "厂家"));
        columns.add(new TabularColumnDef("lotNumber", "批号"));
        columns.add(new TabularColumnDef("unitCodeDictText", "单位"));
        columns.add(new TabularColumnDef("itemQuantity", "数量"));
        columns.add(new TabularColumnDef("price", "单价"));
        columns.add(new TabularColumnDef("totalPrice", "金额"));
        columns.add(new TabularColumnDef("bookQuantity", "账面数量"));
        columns.add(new TabularColumnDef("actualQuantity", "实盘数量"));
        columns.add(new TabularColumnDef("gainLossQuantity", "盈亏数量"));
        columns.add(new TabularColumnDef("actualAmount", "实盘金额"));
        columns.add(new TabularColumnDef("gainLossAmount", "盈亏金额"));
        columns.add(new TabularColumnDef("remake", "备注"));
        return columns;
    }

    /**
     * 导出行转 Map。
     *
     * @param exportRow 导出行
     * @param columns   列定义
     * @return 行 Map
     */
    private Map<String, Object> toRowMap(DocumentDetailExportRowDto exportRow, List<TabularColumnDef> columns) {
        Map<String, Object> map = new HashMap<>();
        if (exportRow == null) {
            return map;
        }
        map.put("busNo", exportRow.getBusNo());
        map.put("originalBusNo", exportRow.getOriginalBusNo());
        map.put("typeEnumText", exportRow.getTypeEnumText());
        map.put("sourceLocationName", exportRow.getSourceLocationName());
        map.put("purposeLocationName", exportRow.getPurposeLocationName());
        map.put("consumingOrganizationName", exportRow.getConsumingOrganizationName());
        map.put("supplierName", exportRow.getSupplierName());
        map.put("applicantName", exportRow.getApplicantName());
        map.put("approverName", exportRow.getApproverName());
        map.put("practitionerName", exportRow.getPractitionerName());
        map.put("applyTime", exportRow.getApplyTime());
        map.put("approvalTime", exportRow.getApprovalTime());
        map.put("productName", exportRow.getProductName());
        map.put("productBusNo", exportRow.getProductBusNo());
        map.put("specification", exportRow.getSpecification());
        map.put("manufacturer", exportRow.getManufacturer());
        map.put("lotNumber", exportRow.getLotNumber());
        map.put("unitCodeDictText", exportRow.getUnitCodeDictText());
        map.put("itemQuantity", exportRow.getItemQuantity());
        map.put("price", exportRow.getPrice());
        map.put("totalPrice", exportRow.getTotalPrice());
        map.put("bookQuantity", exportRow.getBookQuantity());
        map.put("actualQuantity", exportRow.getActualQuantity());
        map.put("gainLossQuantity", exportRow.getGainLossQuantity());
        map.put("actualAmount", exportRow.getActualAmount());
        map.put("gainLossAmount", exportRow.getGainLossAmount());
        map.put("remake", exportRow.getRemake());
        return map;
    }

    /**
     * 时间格式化。
     *
     * @param value 时间
     * @return 字符串或 null
     */
    private String formatDateTime(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.format(GeneralSharedConstants.DATE_TIME_FORMATTER);
    }

    /**
     * 数值格式化。
     *
     * @param value 数值
     * @return 字符串或 null
     */
    private String formatDecimal(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.toPlainString();
    }
}
