package com.openhis.whale.report.application.query.loader.warehouse;
import com.openhis.whale.report.application.query.support.ReportQueryPaging;
import com.openhis.whale.report.application.query.support.ReportLoaderDataSupport;
import com.openhis.whale.report.application.query.support.ReportLoader;

import com.openhis.whale.report.application.query.response.ReportColumnDto;
import com.openhis.whale.report.application.query.response.ReportQueryPageResultDto;
import com.openhis.whale.report.application.query.request.ReportQueryPageRequest;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 出库/入库单（PH-02）C 类报表 Loader。
 */
@Component
public class WarehouseDocumentLoader implements ReportLoader {

    public static final String REPORT_CODE = "warehouse_document";

    private final ReportLoaderDataSupport dataSupport;

    public WarehouseDocumentLoader(ReportLoaderDataSupport dataSupport) {
        this.dataSupport = dataSupport;
    }

    @Override
    public String reportCode() {
        return REPORT_CODE;
    }

    @Override
    public String reportName() {
        return "出库/入库单";
    }

    @Override
    public List<ReportColumnDto> columns() {
        return List.of(
                col("busNo", "单据号", "left", 140),
                col("typeEnum", "类型", "left", 100),
                col("statusEnum", "状态", "center", 100),
                col("purposeLocationId", "目标库位", "left", 100),
                col("itemQuantity", "数量", "right", 100),
                col("totalPrice", "金额", "right", 120),
                col("applyTime", "申请时间", "left", 160));
    }

    @Override
    public ReportQueryPageResultDto load(ReportQueryPageRequest request) {
        ReportQueryPageResultDto result = new ReportQueryPageResultDto();
        result.setReportCode(REPORT_CODE);
        result.setReportName(reportName());
        result.setColumns(columns());

        if (dataSupport.applyPortDataIfPresent(REPORT_CODE, request, result)) {
            return result;
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("dateRange", resolveDateRangeText(request));
        summary.put("documentCount", 0);
        summary.put("totalAmount", "0.00");
        return pageResult(request, List.of(stubRow()), summary);
    }

    private static ReportColumnDto col(String field, String label, String align, int width) {
        ReportColumnDto dto = new ReportColumnDto();
        dto.setField(field);
        dto.setLabel(label);
        dto.setAlign(align);
        dto.setWidth(width);
        return dto;
    }

    private static Map<String, Object> stubRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("busNo", "—");
        row.put("typeEnum", "—");
        row.put("statusEnum", "—");
        row.put("purposeLocationId", "—");
        row.put("itemQuantity", "0");
        row.put("totalPrice", "0.00");
        row.put("unit", "元");
        row.put("applyTime", "—");
        return row;
    }

    private static ReportQueryPageResultDto pageResult(
            ReportQueryPageRequest request,
            List<Map<String, Object>> allRows,
            Map<String, Object> summary) {
        int pageNo = Math.max(request.getPageNo(), 1);
        int pageSize = Math.max(request.getPageSize(), 1);
        int from = (pageNo - 1) * pageSize;
        int to = Math.min(from + pageSize, allRows.size());

        ReportQueryPageResultDto result = new ReportQueryPageResultDto();
        result.setReportCode(REPORT_CODE);
        result.setReportName("出库/入库单");
        result.setRows(ReportQueryPaging.slice(allRows, from, to));
        result.setTotalCount(allRows.size());
        result.setPageNo(pageNo);
        result.setPageSize(pageSize);
        result.setSummary(summary);
        return result;
    }

    private static String resolveDateRangeText(ReportQueryPageRequest request) {
        return ReportQueryPaging.formatDateRange(request.getDateFrom(), request.getDateTo());
    }
}
