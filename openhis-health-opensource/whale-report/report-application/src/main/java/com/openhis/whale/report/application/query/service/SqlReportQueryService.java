package com.openhis.whale.report.application.query.service;

import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.report.application.query.request.ReportQueryPageRequest;
import com.openhis.whale.report.application.query.response.ReportColumnDto;
import com.openhis.whale.report.application.query.response.ReportQueryPageResultDto;
import com.openhis.whale.report.application.query.support.ReportDefinitionColumnSupport;
import com.openhis.whale.report.application.query.support.ReportHeaderSupport;
import com.openhis.whale.report.application.query.support.ReportQueryPaging;
import com.openhis.whale.report.application.query.support.SqlReportQueryExecutor;
import com.openhis.whale.report.domain.constants.ReportDefinitionSourceType;
import com.openhis.whale.report.domain.entity.ReportDefinition;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SqlReportQueryService {

    private static final int MAX_ROWS = 5000;

    private final SqlReportQueryExecutor sqlExecutor;
    private final ReportDefinitionColumnSupport columnSupport;

    public SqlReportQueryService(
            SqlReportQueryExecutor sqlExecutor,
            ReportDefinitionColumnSupport columnSupport) {
        this.sqlExecutor = sqlExecutor;
        this.columnSupport = columnSupport;
    }

    public ReportQueryPageResultDto queryPage(ReportDefinition definition, ReportQueryPageRequest request) {
        if (definition == null || !ReportDefinitionSourceType.SQL.equalsIgnoreCase(definition.getSourceType())) {
            throw new ValidationException("非 SQL 报表");
        }
        if (!StringUtils.hasText(definition.getQuerySql())) {
            throw new ValidationException("报表未配置 SQL");
        }

        Map<String, Object> params = buildSqlParams(request);
        SqlReportQueryExecutor.SqlReportQueryResult sqlResult =
                sqlExecutor.execute(definition.getQuerySql(), params, MAX_ROWS);

        List<ReportColumnDto> columns = resolveColumns(definition, sqlResult.fields());
        List<Map<String, Object>> allRows = sqlResult.rows();

        int pageNo = Math.max(request.getPageNo(), 1);
        int pageSize = Math.max(request.getPageSize(), 1);
        int from = (pageNo - 1) * pageSize;
        int to = Math.min(from + pageSize, allRows.size());

        ReportQueryPageResultDto result = new ReportQueryPageResultDto();
        result.setReportCode(definition.getReportCode());
        result.setReportName(definition.getName());
        result.setColumns(columns);
        result.setRows(ReportQueryPaging.slice(allRows, from, to));
        result.setTotalCount(allRows.size());
        result.setPageNo(pageNo);
        result.setPageSize(pageSize);
        result.setSummary(buildSummary(definition, request, allRows.size()));
        Map<String, String> placeholders = buildPlaceholders(definition, request, allRows.size());
        result.setHeader(ReportHeaderSupport.resolve(definition.getHeaderJson(), placeholders));
        result.setFooter(ReportHeaderSupport.parseJsonMap(definition.getFooterJson()));
        return result;
    }

    private static Map<String, String> buildPlaceholders(
            ReportDefinition definition, ReportQueryPageRequest request, int rowCount) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("dateRange", ReportQueryPaging.formatDateRange(request.getDateFrom(), request.getDateTo()));
        placeholders.put("reportTitle", definition.getName() == null ? "" : definition.getName());
        placeholders.put("reportCode", definition.getReportCode() == null ? "" : definition.getReportCode());
        placeholders.put("rowCount", String.valueOf(rowCount));
        return placeholders;
    }

    private List<ReportColumnDto> resolveColumns(ReportDefinition definition, List<String> sqlFields) {
        List<ReportColumnDto> configured = columnSupport.parseColumns(definition.getDefaultColumnsJson());
        if (!configured.isEmpty()) {
            return configured;
        }
        List<ReportColumnDto> inferred = new ArrayList<>();
        for (String field : sqlFields) {
            if (!StringUtils.hasText(field)) {
                continue;
            }
            ReportColumnDto column = new ReportColumnDto();
            column.setField(field);
            column.setLabel(field);
            column.setAlign("left");
            column.setWidth(120);
            inferred.add(column);
        }
        return inferred;
    }

    private static Map<String, Object> buildSummary(
            ReportDefinition definition, ReportQueryPageRequest request, int rowCount) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("dateRange", ReportQueryPaging.formatDateRange(request.getDateFrom(), request.getDateTo()));
        summary.put("rowCount", rowCount);
        summary.put("reportTitle", definition.getName());
        return summary;
    }

    private static Map<String, Object> buildSqlParams(ReportQueryPageRequest request) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (request.getDateFrom() != null) {
            params.put("dateFrom", request.getDateFrom());
        }
        if (request.getDateTo() != null) {
            params.put("dateTo", request.getDateTo());
        }
        if (StringUtils.hasText(request.getKeyword())) {
            params.put("keyword", request.getKeyword().trim());
        }
        if (request.getParams() != null) {
            params.putAll(request.getParams());
        }
        return params;
    }
}
