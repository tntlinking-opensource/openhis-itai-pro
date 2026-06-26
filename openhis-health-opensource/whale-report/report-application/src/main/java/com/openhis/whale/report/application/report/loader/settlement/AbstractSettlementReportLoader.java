package com.openhis.whale.report.application.report.loader.settlement;

import com.openhis.whale.report.application.report.support.ReportQueryPaging;
import com.openhis.whale.report.application.report.support.ReportLoader;

import com.openhis.whale.report.application.report.response.ReportColumnDto;
import com.openhis.whale.report.application.report.response.ReportQueryPageResultDto;
import com.openhis.whale.report.application.report.request.ReportQueryPageRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * 日结类报表 Loader 基类：分页切片与金额格式化。
 */
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

    /**
     * 将全量行按请求分页并组装结果 DTO。
     *
     * @param request  分页请求
     * @param allRows  全量数据行
     * @param summary  汇总块
     * @return 分页后的查询结果
     */
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
}
