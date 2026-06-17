package com.openhis.whale.report.application.query.service;

import com.openhis.whale.core.infrastructure.multitenancy.TenantContext;
import com.openhis.whale.report.application.query.request.ListReportDefinitionsRequest;
import com.openhis.whale.report.application.query.response.ReportColumnDto;
import com.openhis.whale.report.application.query.response.ReportDefinitionItemDto;
import com.openhis.whale.report.application.query.support.ReportDefinitionColumnSupport;
import com.openhis.whale.report.application.query.support.ReportLoader;
import com.openhis.whale.report.application.query.support.ReportLoaderRegistry;
import com.openhis.whale.report.domain.entity.ReportDefinition;
import com.openhis.whale.report.domain.service.ReportDefinitionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ReportDefinitionCatalogService {

    private final ReportDefinitionManager definitionManager;
    private final ReportLoaderRegistry loaderRegistry;
    private final ReportDefinitionColumnSupport columnSupport;
    private final TenantContext tenantContext;

    public ReportDefinitionCatalogService(
            ReportDefinitionManager definitionManager,
            ReportLoaderRegistry loaderRegistry,
            ReportDefinitionColumnSupport columnSupport,
            TenantContext tenantContext) {
        this.definitionManager = definitionManager;
        this.loaderRegistry = loaderRegistry;
        this.columnSupport = columnSupport;
        this.tenantContext = tenantContext;
    }

    @Transactional
    public List<ReportDefinitionItemDto> list(ListReportDefinitionsRequest request) {
        Long tenantId = tenantContext.getCurrentTenantId();
        syncMissingDefinitions(tenantId);

        String status = request == null ? null : request.getStatus();
        String keyword = request == null ? null : request.getKeyword();
        List<ReportDefinition> definitions = definitionManager.listForTenant(tenantId, status);
        Map<String, ReportLoader> loaderIndex = loaderRegistry.indexByCode();

        List<ReportDefinitionItemDto> items = new ArrayList<>();
        for (ReportDefinition definition : definitions) {
            if (!matchesKeyword(definition, keyword)) {
                continue;
            }
            ReportLoader loader = loaderIndex.get(definition.getReportCode());
            items.add(toDto(definition, loader));
        }
        items.sort(Comparator.comparing(
                dto -> dto.getName() == null ? "" : dto.getName(), String.CASE_INSENSITIVE_ORDER));
        return items;
    }

    public ReportDefinitionItemDto requireDto(Long id) {
        Long tenantId = tenantContext.getCurrentTenantId();
        ReportDefinition definition = definitionManager.requireById(id, tenantId);
        return toDto(definition, loaderRegistry.find(definition.getReportCode()).orElse(null));
    }

    private void syncMissingDefinitions(Long tenantId) {
        definitionManager.syncPlatformDefinitions(tenantId);
        for (ReportLoader loader : loaderRegistry.listAll()) {
            if (loader == null || !StringUtils.hasText(loader.reportCode())) {
                continue;
            }
            definitionManager.ensureFromLoader(
                    tenantId,
                    loader.reportCode().trim(),
                    loader.reportName(),
                    ReportDefinitionManager.deriveLoaderBeanName(loader.reportCode()),
                    columnSupport.buildDefaultColumnsJson(loader.columns()));
        }
    }

    private ReportDefinitionItemDto toDto(ReportDefinition definition, ReportLoader loader) {
        ReportDefinitionItemDto dto = new ReportDefinitionItemDto();
        dto.setId(definition.getId());
        dto.setReportCode(definition.getReportCode());
        dto.setName(definition.getName());
        dto.setStatus(definition.getStatus());
        dto.setExportPdfEnabled(definition.getExportPdfEnabled());
        dto.setPdfTemplateCode(definition.getPdfTemplateCode());
        dto.setAllowColumnCustomize(definition.getAllowColumnCustomize());
        dto.setSourceType(definition.getSourceType());
        dto.setQuerySql(definition.getQuerySql());
        dto.setHeaderJson(definition.getHeaderJson());
        dto.setFooterJson(definition.getFooterJson());
        dto.setFilterJson(definition.getFilterJson());
        dto.setRemark(definition.getRemark());

        List<ReportColumnDto> columns = columnSupport.parseColumns(definition.getDefaultColumnsJson());
        if (columns.isEmpty() && loader != null) {
            columns = loader.columns();
        }
        dto.setColumns(enrichColumns(columns, loader));
        return dto;
    }

    private static List<ReportColumnDto> enrichColumns(List<ReportColumnDto> columns, ReportLoader loader) {
        if (loader == null || loader.columns() == null || loader.columns().isEmpty()) {
            return columns == null ? List.of() : columns;
        }
        Map<String, ReportColumnDto> loaderIndex = ReportLoaderRegistry.indexColumns(loader.columns());
        List<ReportColumnDto> result = new ArrayList<>();
        List<ReportColumnDto> source = columns == null || columns.isEmpty() ? loader.columns() : columns;
        for (ReportColumnDto column : source) {
            if (column == null || !StringUtils.hasText(column.getField())) {
                continue;
            }
            ReportColumnDto merged = new ReportColumnDto();
            merged.setField(column.getField());
            merged.setLabel(column.getLabel());
            ReportColumnDto fallback = loaderIndex.get(column.getField().trim());
            if (fallback != null) {
                if (!StringUtils.hasText(merged.getLabel())) {
                    merged.setLabel(fallback.getLabel());
                }
                merged.setAlign(fallback.getAlign());
                merged.setWidth(fallback.getWidth());
            } else {
                merged.setAlign(column.getAlign());
                merged.setWidth(column.getWidth());
            }
            result.add(merged);
        }
        return result;
    }

    private static boolean matchesKeyword(ReportDefinition definition, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String text = keyword.trim().toLowerCase(Locale.ROOT);
        return containsIgnoreCase(definition.getName(), text)
                || containsIgnoreCase(definition.getReportCode(), text);
    }

    private static boolean containsIgnoreCase(String value, String keywordLower) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keywordLower);
    }
}
