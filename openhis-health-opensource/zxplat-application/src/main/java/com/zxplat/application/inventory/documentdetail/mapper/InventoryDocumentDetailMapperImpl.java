/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.documentdetail.mapper;

import com.zxplat.application.inventory.documentdetail.dto.DocumentDetailExportRowDto;
import com.zxplat.application.inventory.documentdetail.dto.DocumentDetailItemSummaryRowDto;
import com.zxplat.application.inventory.documentdetail.dto.DocumentDetailRowDto;
import com.zxplat.application.inventory.documentdetail.request.DocumentDetailFilterRequest;
import com.zxplat.application.inventory.documentdetail.request.PageDocumentDetailsRequest;
import com.zxplat.application.inventory.documentdetail.response.ExportDocumentDetailsResponse;
import com.zxplat.application.inventory.documentdetail.response.PageDocumentDetailsResponse;
import com.zxplat.application.inventory.documentdetail.response.SummarizeDocumentDetailsResponse;
import com.zxplat.application.inventory.documentdetail.support.DocumentDetailDomainScope;
import com.zxplat.domain.share.constants.ChargeItemAmountScaleConstants;
import com.zxplat.domain.share.utils.MonetaryAmountSupport;
import com.zxplat.domain.share.constants.DocumentDetailExportConstants;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.share.constants.InventoryWarehouseDetailExportConstants;
import com.zxplat.framework.core.application.dto.ExcelExportColumnDto;
import com.zxplat.framework.core.application.dto.OptionDto;
import com.zxplat.framework.core.application.util.StringUtils;
import com.zxplat.framework.core.application.util.TabularColumnDef;
import com.zxplat.framework.core.application.util.TabularExcelExporter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
        // 供应商等值筛；空则该维不过滤
        filter.setSupplierId(request.getSupplierId());
        filter.setApprovalTimeStart(request.getApprovalTimeStart());
        filter.setApprovalTimeEnd(request.getApprovalTimeEnd());
        filter.setItemTable(request.getItemTable());
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
     * 装配合计响应（含按品规汇总行）。
     *
     * @param totalPriceSum   金额合计
     * @param itemQuantitySum 展示数量合计
     * @param rowCount        明细行数
     * @param summaryRecords  按品规汇总行
     * @return 合计响应
     */
    @Override
    public SummarizeDocumentDetailsResponse toSummarizeDocumentDetailsResponse(
        BigDecimal totalPriceSum,
        BigDecimal itemQuantitySum,
        long rowCount,
        List<DocumentDetailItemSummaryRowDto> summaryRecords
    ) {
        SummarizeDocumentDetailsResponse response = new SummarizeDocumentDetailsResponse();
        response.setTotalPriceSum(totalPriceSum);
        response.setItemQuantitySum(itemQuantitySum);
        response.setRowCount(rowCount);
        if (summaryRecords == null) {
            response.setRecords(new ArrayList<>());
        } else {
            response.setRecords(summaryRecords);
        }
        return response;
    }

    /**
     * 构建汇总维度下拉（本期仅按品规）。
     *
     * @return 选项列表
     */
    @Override
    public List<OptionDto> buildSummaryDimensionOptions() {
        List<OptionDto> options = new ArrayList<>();
        OptionDto itemOption = new OptionDto();
        itemOption.setValue(DocumentDetailExportConstants.SUMMARY_DIMENSION_ITEM);
        itemOption.setLabel(DocumentDetailExportConstants.SUMMARY_DIMENSION_ITEM_LABEL);
        options.add(itemOption);
        return options;
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
            // 操作/包装/最小：数量 NUMERIC + 各自单位列（禁止合并文案替代）
            exportRow.setItemQuantity(this.toExportQuantity(row.getItemQuantity()));
            exportRow.setUnitCodeDictText(row.getUnitCodeDictText());
            exportRow.setPackageQuantity(this.toExportQuantity(row.getPackageQuantity()));
            exportRow.setPackageUnitName(row.getPackageUnitName());
            exportRow.setMinUnitQuantity(this.toExportQuantity(row.getMinUnitQuantity()));
            exportRow.setMinUnitName(row.getMinUnitName());
            exportRow.setPrice(this.toExportMoney(row.getPrice()));
            exportRow.setTotalPrice(this.toExportMoney(row.getTotalPrice()));
            exportRow.setBookQuantity(this.toExportQuantity(row.getBookQuantity()));
            exportRow.setActualQuantity(this.toExportQuantity(row.getActualQuantity()));
            exportRow.setGainLossQuantity(this.toExportQuantity(row.getGainLossQuantity()));
            exportRow.setActualAmount(this.toExportMoney(row.getActualAmount()));
            exportRow.setGainLossAmount(this.toExportMoney(row.getGainLossAmount()));
            exportRow.setRemake(row.getRemake());
            exportRows.add(exportRow);
        }
        return exportRows;
    }

    /**
     * 按品规汇总行 → Excel 导出行（并集 DTO 稀疏填列；无售价）。
     *
     * @param summaryRows 汇总行
     * @return 导出行
     */
    @Override
    public List<DocumentDetailExportRowDto> toSummaryExportRows(
        List<DocumentDetailItemSummaryRowDto> summaryRows
    ) {
        List<DocumentDetailExportRowDto> exportRows = new ArrayList<>();
        if (summaryRows == null) {
            return exportRows;
        }
        for (DocumentDetailItemSummaryRowDto summaryRow : summaryRows) {
            if (summaryRow == null) {
                continue;
            }
            DocumentDetailExportRowDto exportRow = new DocumentDetailExportRowDto();
            // 汇总：维度名落入品名列，与页面「维度/品名」同构
            exportRow.setProductName(summaryRow.getDimensionName());
            exportRow.setProductBusNo(summaryRow.getProductBusNo());
            exportRow.setSpecification(summaryRow.getSpecification());
            exportRow.setPackageQuantity(this.toExportQuantity(summaryRow.getPackageQuantity()));
            exportRow.setPackageUnitName(summaryRow.getPackageUnitName());
            exportRow.setMinUnitQuantity(this.toExportQuantity(summaryRow.getMinUnitQuantity()));
            exportRow.setMinUnitName(summaryRow.getMinUnitName());
            // 汇总不导出单价（同品规多价无统一单价口径）
            exportRow.setTotalPrice(this.toExportMoney(summaryRow.getTotalPrice()));
            exportRows.add(exportRow);
        }
        return exportRows;
    }

    /**
     * 写出 xlsx 字节（可按前端列清单裁剪）。
     *
     * @param exportRows 导出行
     * @param columns    前端自选列；null/空则全部动态列
     * @param sheetName  工作表名
     * @return xlsx 字节
     */
    @Override
    public byte[] buildExcelBytes(
        List<DocumentDetailExportRowDto> exportRows,
        List<ExcelExportColumnDto> columns,
        String sheetName
    ) {
        List<TabularColumnDef> tabularColumns = this.buildExportColumns();
        List<Map<String, Object>> rowMaps = new ArrayList<>();
        if (exportRows != null) {
            for (DocumentDetailExportRowDto exportRow : exportRows) {
                rowMaps.add(this.toRowMap(exportRow, tabularColumns));
            }
        }
        String resolvedSheetName = sheetName;
        // 未传表名时回落明细表名，兼容旧调用
        if (StringUtils.isEmpty(resolvedSheetName)) {
            resolvedSheetName = DocumentDetailExportConstants.SHEET_NAME;
        }
        return TabularExcelExporter.exportToXlsxBytes(
            resolvedSheetName, tabularColumns, rowMaps, columns);
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
        // 数量在前、单位紧随：操作 / 包装 / 最小各一对
        columns.add(new TabularColumnDef("itemQuantity", "操作单位数量", 0));
        columns.add(new TabularColumnDef("unitCodeDictText", "操作单位"));
        columns.add(new TabularColumnDef("packageQuantity", "包装单位数量", 0));
        columns.add(new TabularColumnDef("packageUnitName", "包装单位"));
        columns.add(new TabularColumnDef("minUnitQuantity", "最小单位数量", 0));
        columns.add(new TabularColumnDef("minUnitName", "最小单位"));
        columns.add(new TabularColumnDef(
            "price",
            "单价",
            ChargeItemAmountScaleConstants.CHARGE_MONETARY_TCM_UNIT_PRICE_DISPLAY_SCALE,
            RoundingMode.DOWN));
        columns.add(new TabularColumnDef(
            "totalPrice",
            "金额",
            ChargeItemAmountScaleConstants.CHARGE_MONETARY_TCM_UNIT_PRICE_DISPLAY_SCALE,
            RoundingMode.DOWN));
        columns.add(new TabularColumnDef("bookQuantity", "账面数量", 0));
        columns.add(new TabularColumnDef("actualQuantity", "实盘数量", 0));
        columns.add(new TabularColumnDef("gainLossQuantity", "盈亏数量", 0));
        columns.add(new TabularColumnDef(
            "actualAmount",
            "实盘金额",
            ChargeItemAmountScaleConstants.CHARGE_MONETARY_TCM_UNIT_PRICE_DISPLAY_SCALE,
            RoundingMode.DOWN));
        columns.add(new TabularColumnDef(
            "gainLossAmount",
            "盈亏金额",
            ChargeItemAmountScaleConstants.CHARGE_MONETARY_TCM_UNIT_PRICE_DISPLAY_SCALE,
            RoundingMode.DOWN));
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
        map.put("itemQuantity", exportRow.getItemQuantity());
        map.put("unitCodeDictText", exportRow.getUnitCodeDictText());
        map.put("packageQuantity", exportRow.getPackageQuantity());
        map.put("packageUnitName", exportRow.getPackageUnitName());
        map.put("minUnitQuantity", exportRow.getMinUnitQuantity());
        map.put("minUnitName", exportRow.getMinUnitName());
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
     * 导出数量：整数刻度；null 保持空单元格。
     *
     * @param value 数量
     * @return 整数 BigDecimal 或 null
     */
    private BigDecimal toExportQuantity(BigDecimal value) {
        // 空数量：保持空单元格，避免写成 0 混淆「无值」
        if (value == null) {
            return null;
        }
        return value.setScale(0, RoundingMode.HALF_UP);
    }

    /**
     * 导出进价金额：3 位 DOWN（中药进价/进总额）；null 保持空单元格。
     *
     * @param value 金额
     * @return 3 位截断 BigDecimal 或 null
     */
    private BigDecimal toExportMoney(BigDecimal value) {
        return MonetaryAmountSupport.scaleTcmPurchaseExport(value);
    }
}
