package com.openhis.whale.report.application.report.service;

import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import com.openhis.whale.core.infrastructure.multitenancy.TenantContext;
import com.openhis.whale.report.application.report.request.ReportQueryPageRequest;
import com.openhis.whale.report.application.report.response.ReportQueryPageResultDto;
import com.openhis.whale.report.application.report.support.ReportColumnMergeSupport;
import com.openhis.whale.report.application.report.support.ReportHeaderSupport;
import com.openhis.whale.report.application.report.support.ReportLoader;
import com.openhis.whale.report.application.report.support.ReportLoaderRegistry;
import com.openhis.whale.report.application.report.support.ReportQueryPaging;
import com.openhis.whale.report.domain.constants.ReportDefinitionSourceType;
import com.openhis.whale.report.domain.entity.ReportDefinition;
import com.openhis.whale.report.domain.service.ReportColumnOverrideManager;
import com.openhis.whale.report.domain.service.ReportDefinitionManager;
import com.openhis.whale.report.domain.support.ReportTextSupport;
import org.springframework.stereotype.Service;
import com.openhis.whale.core.application.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * C 类报表分页查询执行层（无 HTTP）。
 *
 * <p>在 {@link ReportAppService} 校验「已发布」等 Gate 之后调用本类，负责：</p>
 * <ol>
 *   <li>按 {@code rpt_definition.source_type} 分流 SQL / Loader 两条取数路径</li>
 *   <li>Loader 路径补全 definition 中的 header/footer 占位符</li>
 *   <li>对 queryPage / exportExcel 统一应用列定制（{@link ReportColumnOverrideManager}）</li>
 * </ol>
 *
 * <p>SQL 路径列来自 {@code default_columns_json} 或 ResultSet 元数据推断；
 * Loader 路径列由各 {@link ReportLoader} 返回。列定制只改 visible/sortOrder/width，不改 SQL 或 Port 字段绑定。</p>
 */
@Service
public class ReportQueryExecutionService {

    private final ReportDefinitionManager definitionManager;
    private final ReportLoaderRegistry loaderRegistry;
    private final ReportQueryService reportQueryService;
    private final TenantContext tenantContext;
    private final ReportColumnOverrideManager columnOverrideManager;
    private final ReportColumnMergeSupport columnMergeSupport;
    private final CurrentOperatorResolver operatorResolver;

    public ReportQueryExecutionService(
            ReportDefinitionManager definitionManager,
            ReportLoaderRegistry loaderRegistry,
            ReportQueryService reportQueryService,
            TenantContext tenantContext,
            ReportColumnOverrideManager columnOverrideManager,
            ReportColumnMergeSupport columnMergeSupport,
            CurrentOperatorResolver operatorResolver) {
        this.definitionManager = definitionManager;
        this.loaderRegistry = loaderRegistry;
        this.reportQueryService = reportQueryService;
        this.tenantContext = tenantContext;
        this.columnOverrideManager = columnOverrideManager;
        this.columnMergeSupport = columnMergeSupport;
        this.operatorResolver = operatorResolver;
    }

    /**
     * 执行分页查询并合并列 override。
     *
     * @param request 含 ReportCode、筛选 params、pageNo/pageSize
     * @return 列 + 行 + header/footer/summary；列已按当前用户 override 过滤排序
     */
    public ReportQueryPageResultDto queryPage(ReportQueryPageRequest request) {
        String reportCode = request.getReportCode().trim();
        Long tenantId = tenantContext.getCurrentTenantId();
        Optional<ReportDefinition> definition = definitionManager.findByCode(tenantId, reportCode);
        // SQL 双轨：设计器维护 query_sql，不经 Loader 注册表
        if (definition.isPresent()
                && ReportDefinitionSourceType.SQL.equalsIgnoreCase(definition.get().getSourceType())) {
            ReportQueryPageResultDto sqlResult = reportQueryService.queryPage(definition.get(), request);
            applyColumnOverrides(sqlResult);
            return sqlResult;
        }
        ReportLoader loader = loaderRegistry.require(reportCode);
        ReportQueryPageResultDto result = loader.load(request);
        // Loader 路径：用 definition 页眉页脚 JSON 填充占位符
        definition.ifPresent(def -> applyDefinitionHeaderFooter(def, request, result));
        applyColumnOverrides(result);
        return result;
    }

    /**
     * 将列 override merge 到查询结果；定义禁止定制时跳过。
     *
     * @param result 待处理的查询结果（就地修改 columns）
     */
    private void applyColumnOverrides(ReportQueryPageResultDto result) {
        if (result == null || result.getColumns() == null || result.getColumns().isEmpty()) {
            // 无列定义时无需 merge
            return;
        }
        Optional<ReportDefinition> definition = definitionManager.findByCode(
                tenantContext.getCurrentTenantId(), result.getReportCode());
        if (definition.isPresent() && Boolean.FALSE.equals(definition.get().getAllowColumnCustomize())) {
            // 出厂报表关闭列定制
            return;
        }
        Long userId = operatorResolver.findOperatorIdOrNull();
        List<ReportColumnOverrideManager.ColumnView> merged = columnOverrideManager.applyOverrides(
                columnMergeSupport.toColumnViews(result.getColumns()),
                tenantContext.getCurrentTenantId(),
                result.getReportCode(),
                userId,
                null);
        result.setColumns(columnMergeSupport.toVisibleColumnDtos(merged));
    }

    /**
     * Loader 路径：解析 definition 的 headerJson/footerJson 并填充占位符。
     *
     * @param definition 报表定义（含页眉页脚 JSON）
     * @param request      查询请求（日期范围等）
     * @param result         待填充 header/footer 的结果对象
     */
    private void applyDefinitionHeaderFooter(
            ReportDefinition definition, ReportQueryPageRequest request, ReportQueryPageResultDto result) {
        if (definition == null || result == null) {
            return;
        }
        long rowCount = result.getTotalCount();
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("dateRange", ReportQueryPaging.formatDateRange(request.getDateFrom(), request.getDateTo()));
        placeholders.put("reportTitle", ReportTextSupport.nullToEmpty(definition.getName()));
        placeholders.put("ReportCode", ReportTextSupport.nullToEmpty(definition.getReportCode()));
        placeholders.put("rowCount", String.valueOf(rowCount));
        if (StringUtils.isNotEmpty(definition.getHeaderJson())) {
            result.setHeader(ReportHeaderSupport.resolve(definition.getHeaderJson(), placeholders));
        }
        // footer 仅当 Loader 未自带时回填 definition JSON
        if (StringUtils.isNotEmpty(definition.getFooterJson()) && result.getFooter() == null) {
            result.setFooter(ReportHeaderSupport.parseJsonMap(definition.getFooterJson()));
        }
    }
}
