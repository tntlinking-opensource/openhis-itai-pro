package com.openhis.whale.report.application.query.loader.settlement;
import com.openhis.whale.report.application.query.support.ReportQueryPaging;
import com.openhis.whale.report.application.query.support.ReportLoader;

import com.openhis.whale.report.application.query.response.ReportColumnDto;
import com.openhis.whale.report.application.query.response.ReportQueryPageResultDto;
import com.openhis.whale.report.application.query.request.ReportQueryPageRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

abstract class AbstractSettlementReportLoader implements ReportLoader {

    protected static ReportColumnDto col(String field, String label, String align, int width) {
        ReportColumnDto dto = new ReportColumnDto();
        dto.setField(field);
        dto.setLabel(label);
        dto.setAlign(align);
        dto.setWidth(width);
        return dto;
    }

    protected static String money(BigDecimal value) {
        BigDecimal safe = BigDecimal.ZERO;
        if (value != null) {
            safe = value;
        }
        return safe.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    protected ReportQueryPageResultDto pageResult(
            ReportQueryPageRequest request,
            List<Map<String, Object>> allRows,
            Map<String, Object> summary) {
        int pageNo = Math.max(request.getPageNo(), 1);
        int pageSize = Math.max(request.getPageSize(), 1);
        int from = (pageNo - 1) * pageSize;
        int to = Math.min(from + pageSize, allRows.size());
        List<Map<String, Object>> pageRows = ReportQueryPaging.slice(allRows, from, to);

        ReportQueryPageResultDto result = new ReportQueryPageResultDto();
        result.setReportCode(reportCode());
        result.setReportName(reportName());
        result.setColumns(columns());
        result.setRows(pageRows);
        result.setTotalCount(allRows.size());
        result.setPageNo(pageNo);
        result.setPageSize(pageSize);
        result.setSummary(summary);
        return result;
    }

    protected static String resolveDateRangeText(ReportQueryPageRequest request) {
        return ReportQueryPaging.formatDateRange(request.getDateFrom(), request.getDateTo());
    }

    protected static String trimToEmpty(String value) {
        return ReportQueryPaging.trimToEmpty(value);
    }

    protected static List<Map<String, Object>> buildFeeCategorySummaryRows() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(summaryRow("西药", "33.55"));
        rows.add(summaryRow("中成药", "40.49"));
        rows.add(summaryRow("化验", "22.00"));
        rows.add(summaryRow("X 光费", "150.00"));
        rows.add(summaryRow("处置费", "20.00"));
        rows.add(summaryRow("挂号费", "7.00"));
        rows.add(summaryRow("医用耗材", "5.82"));
        rows.add(summaryRow("注射费", "8.00"));
        return rows;
    }

    protected static Map<String, Object> summaryRow(String category, String amount) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("category", category);
        row.put("amount", amount);
        row.put("unit", "元");
        return row;
    }

    protected static Map<String, Object> buildIncomeSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalIncome", "286.87");
        summary.put("receivableAmount", "286.87");
        summary.put("discountAmount", "0.00");
        summary.put("actualAmount", "286.87");
        summary.put("wechatAmount", "286.87");
        summary.put("cashAmount", "0.00");
        summary.put("selfPayCount", 7);
        summary.put("insuranceCount", 3);
        return summary;
    }
}
