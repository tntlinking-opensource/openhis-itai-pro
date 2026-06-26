package com.openhis.whale.report.application.report.service;

import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantContext;
import com.openhis.whale.report.application.report.request.ReportQueryPageRequest;
import com.openhis.whale.report.application.report.response.ReportColumnDto;
import com.openhis.whale.report.application.report.response.ReportQueryPageResultDto;
import com.openhis.whale.report.application.report.support.ReportDefinitionColumnSupport;
import com.openhis.whale.report.application.report.support.ReportHeaderSupport;
import com.openhis.whale.report.application.report.support.ReportQueryPaging;
import com.openhis.whale.report.application.report.support.ReportQueryExecutor;
import com.openhis.whale.report.contracts.constants.ReportDatasetLimits;
import com.openhis.whale.report.domain.constants.ReportDefinitionSourceType;
import com.openhis.whale.report.domain.entity.ReportDefinition;
import com.openhis.whale.report.domain.support.ReportTextSupport;
import org.springframework.stereotype.Service;
import com.openhis.whale.core.application.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * C 类 SQL 报表查询（{@code source_type=sql}）。
 *
 * <p>与 Loader 路径对称：校验 definition → 绑定命名参数 → 执行 SQL → 分页切片 → 填充 header/footer。</p>
 *
 * <p>列定义优先 {@code default_columns_json}；未配置时由 {@link ReportQueryExecutor} 返回的
 * ResultSet 列标签推断。单次查询最多 {@link ReportDatasetLimits#MAX_SQL_ROWS} 行（内存分页，非 DB LIMIT）。</p>
 */
@Service
public class ReportQueryService {

    private final ReportQueryExecutor sqlExecutor;
    private final ReportDefinitionColumnSupport columnSupport;
    private final TenantContext tenantContext;

    public ReportQueryService(
            ReportQueryExecutor sqlExecutor,
            ReportDefinitionColumnSupport columnSupport,
            TenantContext tenantContext) {
        this.sqlExecutor = sqlExecutor;
        this.columnSupport = columnSupport;
        this.tenantContext = tenantContext;
    }

    /**
     * 执行 SQL 报表分页查询（内存切片，最多 {@link ReportDatasetLimits#MAX_SQL_ROWS} 行）。
     *
     * @param definition 已发布的 SQL 定义
     * @param request      筛选与分页
     * @return 结构化 header/footer，非 JSON 串
     */
    public ReportQueryPageResultDto queryPage(ReportDefinition definition, ReportQueryPageRequest request) {
        // 仅 SQL 双轨路径调用本服务
        if (definition == null || !ReportDefinitionSourceType.SQL.equalsIgnoreCase(definition.getSourceType())) {
            throw new ValidationException("非 SQL 报表");
        }
        if (!StringUtils.isNotEmpty(definition.getQuerySql())) {
            throw new ValidationException("报表未配置 SQL");
        }

        // Pre：绑定命名参数（含 tenantId、日期、keyword）
        Map<String, Object> params = buildSqlParams(request);
        ReportQueryExecutor.ReportSqlQueryResult sqlResult =
                sqlExecutor.execute(definition.getQuerySql(), params, ReportDatasetLimits.MAX_SQL_ROWS);

        List<ReportColumnDto> columns = resolveColumns(definition, sqlResult.fields());
        List<Map<String, Object>> allRows = sqlResult.rows();

        int pageNo = Math.max(request.getPageNo(), 1);
        int pageSize = Math.max(request.getPageSize(), 1);
        int from = (pageNo - 1) * pageSize;
        int to = Math.min(from + pageSize, allRows.size());

        // Asm：分页切片 + 表头占位符解析
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
        placeholders.put("reportTitle", ReportTextSupport.nullToEmpty(definition.getName()));
        placeholders.put("ReportCode", ReportTextSupport.nullToEmpty(definition.getReportCode()));
        placeholders.put("rowCount", String.valueOf(rowCount));
        return placeholders;
    }

    private List<ReportColumnDto> resolveColumns(ReportDefinition definition, List<String> sqlFields) {
        List<ReportColumnDto> configured = columnSupport.parseColumns(definition.getDefaultColumnsJson());
        if (!configured.isEmpty()) {
            return configured;
        }
        // 未配置 default_columns_json 时按 ResultSet 列标签推断
        List<ReportColumnDto> inferred = new ArrayList<>();
        for (String field : sqlFields) {
            if (!StringUtils.isNotEmpty(field)) {
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

    private Map<String, Object> buildSqlParams(ReportQueryPageRequest request) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (request.getParams() != null) {
            for (Map.Entry<String, Object> entry : request.getParams().entrySet()) {
                String key = entry.getKey();
                if (!StringUtils.isNotEmpty(key) || isReservedSqlParamKey(key)) {
                    continue;
                }
                Object value = entry.getValue();
                if (value instanceof String && !StringUtils.isNotEmpty((String) value)) {
                    continue;
                }
                params.put(key.trim(), value);
            }
        }
        Long tenantId = tenantContext.getCurrentTenantId();
        if (tenantId != null) {
            params.put("tenantId", tenantId);
        }
        if (StringUtils.isNotEmpty(request.getDateFrom())) {
            params.put("dateFrom", request.getDateFrom().trim());
        }
        if (StringUtils.isNotEmpty(request.getDateTo())) {
            params.put("dateTo", request.getDateTo().trim());
        }
        if (StringUtils.isNotEmpty(request.getKeyword())) {
            params.put("keyword", request.getKeyword().trim());
        }
        return params;
    }

    private static boolean isReservedSqlParamKey(String key) {
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        return "tenantid".equals(normalized)
                || "tenant_id".equals(normalized)
                || "datefrom".equals(normalized)
                || "dateto".equals(normalized)
                || "keyword".equals(normalized);
    }
}
