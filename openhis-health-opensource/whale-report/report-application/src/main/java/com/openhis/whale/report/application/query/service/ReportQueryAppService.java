package com.openhis.whale.report.application.query.service;

import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantContext;
import com.openhis.whale.report.application.export.ReportCsvExporter;
import com.openhis.whale.report.application.query.request.CreateReportDefinitionRequest;
import com.openhis.whale.report.application.query.request.GetReportDefinitionRequest;
import com.openhis.whale.report.application.query.request.ListReportDefinitionsRequest;
import com.openhis.whale.report.application.query.request.PublishReportDefinitionRequest;
import com.openhis.whale.report.application.query.request.ReportQueryPageRequest;
import com.openhis.whale.report.application.query.request.SaveReportDefinitionRequest;
import com.openhis.whale.report.application.query.request.SaveReportDesignRequest;
import com.openhis.whale.report.application.query.request.UpdateReportDefinitionRequest;
import com.openhis.whale.report.application.query.response.ReportDefinitionItemDto;
import com.openhis.whale.report.application.query.response.ReportExportResultDto;
import com.openhis.whale.report.application.query.response.ReportQueryPageResultDto;
import com.openhis.whale.report.domain.entity.ReportDefinition;
import com.openhis.whale.report.domain.service.ReportDefinitionManager;
import jakarta.validation.Valid;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import java.util.Base64;
import java.util.List;

/**
 * C 类报表 AppService → {@code POST /api/app/reportQuery/*}
 */
@Service
@Validated
public class ReportQueryAppService {

    private final ReportDefinitionCatalogService definitionCatalogService;
    private final ReportDefinitionManager definitionManager;
    private final ReportQueryExecutionService queryExecutionService;
    private final TenantContext tenantContext;

    public ReportQueryAppService(
            ReportDefinitionCatalogService definitionCatalogService,
            ReportDefinitionManager definitionManager,
            ReportQueryExecutionService queryExecutionService,
            TenantContext tenantContext) {
        this.definitionCatalogService = definitionCatalogService;
        this.definitionManager = definitionManager;
        this.queryExecutionService = queryExecutionService;
        this.tenantContext = tenantContext;
    }

    public List<ReportDefinitionItemDto> listDefinitions(ListReportDefinitionsRequest request) {
        return definitionCatalogService.list(request == null ? new ListReportDefinitionsRequest() : request);
    }

    public ReportDefinitionItemDto getDefinition(@Valid GetReportDefinitionRequest request) {
        return definitionCatalogService.requireDto(request.getId());
    }

    public ReportDefinitionItemDto createDefinition(@Valid CreateReportDefinitionRequest request) {
        Long tenantId = tenantContext.getCurrentTenantId();
        ReportDefinition created = definitionManager.createSqlReport(
                tenantId, request.getReportCode(), request.getName(), request.getRemark());
        return definitionCatalogService.requireDto(created.getId());
    }

    public ReportDefinitionItemDto updateDefinition(@Valid UpdateReportDefinitionRequest request) {
        Long tenantId = tenantContext.getCurrentTenantId();
        ReportDefinition updated =
                definitionManager.updateBasic(request.getId(), tenantId, request.getName(), request.getRemark());
        return definitionCatalogService.requireDto(updated.getId());
    }

    public void deleteDefinition(@Valid PublishReportDefinitionRequest request) {
        Long tenantId = tenantContext.getCurrentTenantId();
        definitionManager.deleteReport(request.getId(), tenantId);
    }

    public ReportDefinitionItemDto saveDefinition(@Valid SaveReportDefinitionRequest request) {
        Long tenantId = tenantContext.getCurrentTenantId();
        ReportDefinition saved = definitionManager.saveMetadata(
                request.getId(),
                tenantId,
                request.getName(),
                request.getExportPdfEnabled(),
                request.getPdfTemplateCode());
        return definitionCatalogService.requireDto(saved.getId());
    }

    public ReportDefinitionItemDto saveReportDesign(@Valid SaveReportDesignRequest request) {
        Long tenantId = tenantContext.getCurrentTenantId();
        ReportDefinition saved = definitionManager.saveDesign(
                request.getId(),
                tenantId,
                request.getName(),
                request.getQuerySql(),
                request.getHeaderJson(),
                request.getFooterJson(),
                request.getFilterJson(),
                request.getDefaultColumnsJson(),
                request.getExportPdfEnabled(),
                request.getPdfTemplateCode());
        return definitionCatalogService.requireDto(saved.getId());
    }

    public ReportDefinitionItemDto publishDefinition(@Valid PublishReportDefinitionRequest request) {
        Long tenantId = tenantContext.getCurrentTenantId();
        ReportDefinition published = definitionManager.publish(request.getId(), tenantId);
        return definitionCatalogService.requireDto(published.getId());
    }

    public ReportDefinitionItemDto unpublishDefinition(@Valid PublishReportDefinitionRequest request) {
        Long tenantId = tenantContext.getCurrentTenantId();
        ReportDefinition draft = definitionManager.unpublish(request.getId(), tenantId);
        return definitionCatalogService.requireDto(draft.getId());
    }

    public ReportQueryPageResultDto queryPage(@Valid ReportQueryPageRequest request) {
        requirePublishedIfNeeded(request.getReportCode(), request.getDesignPreview());
        return queryExecutionService.queryPage(request);
    }

    public ReportExportResultDto exportExcel(@Valid ReportQueryPageRequest request) {
        requirePublishedIfNeeded(request.getReportCode(), Boolean.FALSE);
        ReportQueryPageRequest exportRequest = copyForExport(request);
        ReportQueryPageResultDto page = queryPage(exportRequest);

        ReportExportResultDto result = new ReportExportResultDto();
        result.setContentType("text/csv");
        result.setFileName(buildExportFileName(page.getReportCode()));
        byte[] csvBytes = ReportCsvExporter.toCsvBytes(page.getColumns(), page.getRows());
        result.setBodyBase64(Base64.getEncoder().encodeToString(csvBytes));
        return result;
    }

    private void requirePublishedIfNeeded(String reportCode, Boolean designPreview) {
        if (Boolean.TRUE.equals(designPreview)) {
            return;
        }
        if (!StringUtils.hasText(reportCode)) {
            return;
        }
        Long tenantId = tenantContext.getCurrentTenantId();
        if (!definitionManager.isPublished(tenantId, reportCode.trim())) {
            throw new ValidationException("报表未发布，请先在报表设计器中发布: " + reportCode.trim());
        }
    }

    private static ReportQueryPageRequest copyForExport(ReportQueryPageRequest request) {
        ReportQueryPageRequest copy = new ReportQueryPageRequest();
        copy.setReportCode(request.getReportCode());
        copy.setDateFrom(request.getDateFrom());
        copy.setDateTo(request.getDateTo());
        copy.setKeyword(request.getKeyword());
        copy.setParams(request.getParams());
        copy.setPageNo(1);
        copy.setPageSize(5000);
        return copy;
    }

    private static String buildExportFileName(String reportCode) {
        String code = "report";
        if (StringUtils.hasText(reportCode)) {
            code = reportCode.trim();
        }
        return code + ".csv";
    }
}
