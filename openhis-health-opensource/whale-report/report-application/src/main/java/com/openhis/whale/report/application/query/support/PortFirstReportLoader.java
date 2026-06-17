package com.openhis.whale.report.application.query.support;

import com.openhis.whale.report.application.query.response.ReportColumnDto;
import com.openhis.whale.report.application.query.response.ReportQueryPageResultDto;
import com.openhis.whale.report.application.query.request.ReportQueryPageRequest;

import java.util.List;
import java.util.Map;

/**
 * C 类 Loader 基类：优先 {@link ReportLoaderDataSupport} Port 数据，否则走样例兜底。
 */
public abstract class PortFirstReportLoader implements ReportLoader {

    private final ReportLoaderDataSupport dataSupport;

    protected PortFirstReportLoader(ReportLoaderDataSupport dataSupport) {
        this.dataSupport = dataSupport;
    }

    @Override
    public final ReportQueryPageResultDto load(ReportQueryPageRequest request) {
        ReportQueryPageResultDto result = new ReportQueryPageResultDto();
        result.setReportCode(reportCode());
        result.setReportName(reportName());
        result.setColumns(columns());
        if (dataSupport.applyPortDataIfPresent(reportCode(), request, result)) {
            return result;
        }
        return pageResult(request, fallbackRows(request), fallbackSummary(request));
    }

    protected abstract List<Map<String, Object>> fallbackRows(ReportQueryPageRequest request);

    protected abstract Map<String, Object> fallbackSummary(ReportQueryPageRequest request);

    protected ReportQueryPageResultDto pageResult(
            ReportQueryPageRequest request,
            List<Map<String, Object>> allRows,
            Map<String, Object> summary) {
        int pageNo = Math.max(request.getPageNo(), 1);
        int pageSize = Math.max(request.getPageSize(), 1);
        int from = (pageNo - 1) * pageSize;
        int to = Math.min(from + pageSize, allRows.size());

        ReportQueryPageResultDto result = new ReportQueryPageResultDto();
        result.setReportCode(reportCode());
        result.setReportName(reportName());
        result.setColumns(columns());
        result.setRows(ReportQueryPaging.slice(allRows, from, to));
        result.setTotalCount(allRows.size());
        result.setPageNo(pageNo);
        result.setPageSize(pageSize);
        result.setSummary(summary);
        return result;
    }

    protected static ReportColumnDto col(String field, String label, String align, int width) {
        ReportColumnDto dto = new ReportColumnDto();
        dto.setField(field);
        dto.setLabel(label);
        dto.setAlign(align);
        dto.setWidth(width);
        return dto;
    }
}
