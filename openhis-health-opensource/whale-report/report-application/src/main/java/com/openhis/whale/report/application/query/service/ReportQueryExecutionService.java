package com.openhis.whale.report.application.query.service;

import com.openhis.whale.core.infrastructure.multitenancy.TenantContext;
import com.openhis.whale.report.application.query.request.ReportQueryPageRequest;
import com.openhis.whale.report.application.query.response.ReportQueryPageResultDto;
import com.openhis.whale.report.application.query.support.ReportHeaderSupport;
import com.openhis.whale.report.application.query.support.ReportLoader;
import com.openhis.whale.report.application.query.support.ReportLoaderRegistry;
import com.openhis.whale.report.application.query.support.ReportQueryPaging;
import com.openhis.whale.report.domain.constants.ReportDefinitionSourceType;
import com.openhis.whale.report.domain.entity.ReportDefinition;
import com.openhis.whale.report.domain.service.ReportDefinitionManager;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class ReportQueryExecutionService {

    private final ReportDefinitionManager definitionManager;
    private final ReportLoaderRegistry loaderRegistry;
    private final SqlReportQueryService sqlReportQueryService;
    private final TenantContext tenantContext;

    public ReportQueryExecutionService(
            ReportDefinitionManager definitionManager,
            ReportLoaderRegistry loaderRegistry,
            SqlReportQueryService sqlReportQueryService,
            TenantContext tenantContext) {
        this.definitionManager = definitionManager;
        this.loaderRegistry = loaderRegistry;
        this.sqlReportQueryService = sqlReportQueryService;
        this.tenantContext = tenantContext;
    }

    public ReportQueryPageResultDto queryPage(ReportQueryPageRequest request) {
        String reportCode = request.getReportCode().trim();
        Long tenantId = tenantContext.getCurrentTenantId();
        Optional<ReportDefinition> definition = definitionManager.findByCode(tenantId, reportCode);
        if (definition.isPresent()
                && ReportDefinitionSourceType.SQL.equalsIgnoreCase(definition.get().getSourceType())) {
            return sqlReportQueryService.queryPage(definition.get(), request);
        }
        ReportLoader loader = loaderRegistry.require(reportCode);
        ReportQueryPageResultDto result = loader.load(request);
        definition.ifPresent(def -> applyDefinitionHeaderFooter(def, request, result));
        return result;
    }

    private void applyDefinitionHeaderFooter(
            ReportDefinition definition, ReportQueryPageRequest request, ReportQueryPageResultDto result) {
        if (definition == null || result == null) {
            return;
        }
        long rowCount = result.getTotalCount();
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("dateRange", ReportQueryPaging.formatDateRange(request.getDateFrom(), request.getDateTo()));
        placeholders.put("reportTitle", definition.getName() == null ? "" : definition.getName());
        placeholders.put("reportCode", definition.getReportCode() == null ? "" : definition.getReportCode());
        placeholders.put("rowCount", String.valueOf(rowCount));
        if (StringUtils.hasText(definition.getHeaderJson())) {
            result.setHeader(ReportHeaderSupport.resolve(definition.getHeaderJson(), placeholders));
        }
        if (StringUtils.hasText(definition.getFooterJson()) && result.getFooter() == null) {
            result.setFooter(ReportHeaderSupport.parseJsonMap(definition.getFooterJson()));
        }
    }
}
