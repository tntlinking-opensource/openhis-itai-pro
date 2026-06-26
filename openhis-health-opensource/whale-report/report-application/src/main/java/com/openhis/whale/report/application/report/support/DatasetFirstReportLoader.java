package com.openhis.whale.report.application.report.support;

import com.openhis.whale.report.application.report.response.ReportColumnDto;
import com.openhis.whale.report.application.report.response.ReportQueryPageResultDto;
import com.openhis.whale.report.application.report.request.ReportQueryPageRequest;

import java.util.List;
import java.util.Map;

/**
 * C 类 Loader 基类：优先 {@link ReportLoaderDataSupport} Port 数据，否则返回空结果。
 */
public abstract class DatasetFirstReportLoader implements ReportLoader {

    private final ReportLoaderDataSupport dataSupport;

    protected DatasetFirstReportLoader(ReportLoaderDataSupport dataSupport) {
        this.dataSupport = dataSupport;
    }

    /**
     * 加载分页查询结果：优先 Port 真数据，否则空表。
     *
     * @param request 分页查询请求
     * @return 含列定义、行数据与 summary 的结果
     */
    @Override
    public final ReportQueryPageResultDto load(ReportQueryPageRequest request) {
        ReportQueryPageResultDto result = new ReportQueryPageResultDto();
        result.setReportCode(reportCode());
        result.setReportName(reportName());
        result.setColumns(columns());
        // 优先 HIS Port 真数据；无 adapter 或空结果时返回空表
        if (dataSupport.applyPortDataIfPresent(reportCode(), request, result)) {
            return result;
        }
        return pageResult(request, fallbackRows(request), fallbackSummary(request));
    }

    /**
     * Port 无数据时的行列表（默认空表）。
     *
     * @param request 分页查询请求
     * @return 全量行列表
     */
    protected List<Map<String, Object>> fallbackRows(ReportQueryPageRequest request) {
        return List.of();
    }

    /**
     * Port 无数据时的 summary 块（默认空）。
     *
     * @param request 分页查询请求
     * @return summary 键值对
     */
    protected Map<String, Object> fallbackSummary(ReportQueryPageRequest request) {
        return Map.of();
    }

    /**
     * 将全量行按请求分页切片并组装结果 DTO。
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

    /**
     * 构建默认列 DTO（Loader 子类共用）。
     *
     * @param field  列字段名（SQL/Port 绑定键）
     * @param label  列标题
     * @param align  对齐：left/center/right
     * @param width  列宽像素
     * @return 列元数据 DTO
     */
    protected static ReportColumnDto col(String field, String label, String align, int width) {
        ReportColumnDto dto = new ReportColumnDto();
        dto.setField(field);
        dto.setLabel(label);
        dto.setAlign(align);
        dto.setWidth(width);
        return dto;
    }
}
