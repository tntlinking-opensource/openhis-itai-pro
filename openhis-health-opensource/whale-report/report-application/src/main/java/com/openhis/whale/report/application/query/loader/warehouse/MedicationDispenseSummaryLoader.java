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
 * 发药汇总单（PH-01）C 类报表 Loader。
 */
@Component
public class MedicationDispenseSummaryLoader implements ReportLoader {

    public static final String REPORT_CODE = "medication_dispense_summary";

    private final ReportLoaderDataSupport dataSupport;

    public MedicationDispenseSummaryLoader(ReportLoaderDataSupport dataSupport) {
        this.dataSupport = dataSupport;
    }

    @Override
    public String reportCode() {
        return REPORT_CODE;
    }

    @Override
    public String reportName() {
        return "发药汇总单";
    }

    @Override
    public List<ReportColumnDto> columns() {
        return List.of(
                col("locationId", "药房/库位", "left", 140),
                col("dispenseCount", "发药笔数", "right", 100),
                col("totalQuantity", "发药数量", "right", 120),
                col("unit", "单位", "center", 64));
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
        summary.put("slipCount", 0);
        summary.put("totalQuantity", "0");

        List<Map<String, Object>> rows = List.of(stubRow("—", "0", "0"));
        return pageResult(request, rows, summary);
    }

    private static ReportColumnDto col(String field, String label, String align, int width) {
        ReportColumnDto dto = new ReportColumnDto();
        dto.setField(field);
        dto.setLabel(label);
        dto.setAlign(align);
        dto.setWidth(width);
        return dto;
    }

    private static Map<String, Object> stubRow(String locationId, String count, String qty) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("locationId", locationId);
        row.put("dispenseCount", count);
        row.put("totalQuantity", qty);
        row.put("unit", "—");
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
        List<Map<String, Object>> pageRows = ReportQueryPaging.slice(allRows, from, to);

        ReportQueryPageResultDto result = new ReportQueryPageResultDto();
        result.setReportCode(REPORT_CODE);
        result.setReportName("发药汇总单");
        result.setRows(pageRows);
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
