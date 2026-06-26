package com.openhis.whale.report.application.report.service;

import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import com.openhis.whale.core.infrastructure.multitenancy.TenantContext;
import com.openhis.whale.core.application.util.TabularColumnDef;
import com.openhis.whale.core.application.util.TabularExcelExporter;
import com.openhis.whale.report.application.report.request.ReportColumnOverrideQueryRequest;
import com.openhis.whale.report.application.report.request.SaveReportColumnOverrideRequest;
import com.openhis.whale.report.application.report.response.ReportColumnDto;
import com.openhis.whale.report.application.report.response.ReportColumnOverrideResultDto;
import com.openhis.whale.report.application.report.support.ReportColumnMergeSupport;
import com.openhis.whale.report.application.report.support.ReportDefinitionColumnSupport;
import com.openhis.whale.report.application.report.request.ApplySqlPresetRequest;
import com.openhis.whale.report.application.report.request.CreateReportDefinitionRequest;
import com.openhis.whale.report.application.report.request.CopyReportDefinitionRequest;
import com.openhis.whale.report.application.report.request.GetReportDefinitionRequest;
import com.openhis.whale.report.application.report.request.ListReportDefinitionsRequest;
import com.openhis.whale.report.application.report.request.PublishReportDefinitionRequest;
import com.openhis.whale.report.application.report.request.ReportQueryPageRequest;
import com.openhis.whale.report.application.report.request.SaveReportDefinitionRequest;
import com.openhis.whale.report.application.report.request.SaveReportDesignRequest;
import com.openhis.whale.report.application.report.request.UpdateReportDefinitionRequest;
import com.openhis.whale.report.application.report.response.ReportDefinitionItemDto;
import com.openhis.whale.report.application.report.response.ReportExportResultDto;
import com.openhis.whale.report.application.report.response.ReportQueryPageResultDto;
import com.openhis.whale.report.application.report.response.ReportSqlMetadataTableDto;
import com.openhis.whale.report.application.report.response.ReportSqlPresetItemDto;
import com.openhis.whale.report.domain.entity.ReportDefinition;
import com.openhis.whale.report.contracts.constants.ReportDatasetLimits;
import com.openhis.whale.report.domain.service.ReportColumnOverrideManager;
import com.openhis.whale.report.domain.service.ReportDefinitionManager;
import jakarta.validation.Valid;
import org.springframework.stereotype.Service;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * C 类报表 AppService → {@code POST /api/app/report/*}
 *
 * <p>Gate：queryPage/export 要求 definition 已发布（{@code designPreview} 除外）。
 * Cmd：定义 CRUD、列 override、SQL 预设；查询委托 {@link ReportQueryExecutionService}。</p>
 */
@Validated
@Service
public class ReportAppService {

    private final ReportDefinitionCatalogService definitionCatalogService;
    private final ReportDefinitionManager definitionManager;
    private final ReportQueryExecutionService queryExecutionService;
    private final TenantContext tenantContext;
    private final ReportColumnOverrideManager columnOverrideManager;
    private final ReportColumnMergeSupport columnMergeSupport;
    private final ReportDefinitionColumnSupport columnSupport;
    private final CurrentOperatorResolver operatorResolver;

    public ReportAppService(
            ReportDefinitionCatalogService definitionCatalogService,
            ReportDefinitionManager definitionManager,
            ReportQueryExecutionService queryExecutionService,
            TenantContext tenantContext,
            ReportColumnOverrideManager columnOverrideManager,
            ReportColumnMergeSupport columnMergeSupport,
            ReportDefinitionColumnSupport columnSupport,
            CurrentOperatorResolver operatorResolver) {
        this.definitionCatalogService = definitionCatalogService;
        this.definitionManager = definitionManager;
        this.queryExecutionService = queryExecutionService;
        this.tenantContext = tenantContext;
        this.columnOverrideManager = columnOverrideManager;
        this.columnMergeSupport = columnMergeSupport;
        this.columnSupport = columnSupport;
        this.operatorResolver = operatorResolver;
    }

    /**
     * 列出报表定义（含平台回退合并）。
     *
     * @param request 筛选条件；null 时返回全部
     * @return 报表定义列表
     */
    public List<ReportDefinitionItemDto> listDefinitions(ListReportDefinitionsRequest request) {
        // Pre：request 缺省时补空筛选，避免 NPE
        ListReportDefinitionsRequest effectiveRequest = new ListReportDefinitionsRequest();
        if (request != null) {
            effectiveRequest = request;
        }
        // Cmd：委托目录服务合并租户与平台定义
        return definitionCatalogService.list(effectiveRequest);
    }

    /**
     * 按主键获取报表定义详情。
     *
     * @param request 含定义主键
     * @return 定义 DTO（含列与设计 JSON）
     */
    public ReportDefinitionItemDto getDefinition(@Valid GetReportDefinitionRequest request) {
        // Cmd：按租户权限加载并装配 DTO
        return definitionCatalogService.requireDto(request.getId());
    }

    /**
     * 新建 SQL 报表定义（可选套用平台预制）。
     *
     * @param request 编码、名称与可选预制编码
     * @return 新建后的定义 DTO
     */
    public ReportDefinitionItemDto createDefinition(@Valid CreateReportDefinitionRequest request) {
        // Pre：当前租户上下文
        Long tenantId = tenantContext.getCurrentTenantId();
        // Cmd：创建草稿定义
        ReportDefinition created = definitionManager.createSqlDefinition(
                tenantId, request.getReportCode(), request.getName(), request.getRemark());
        // Cmd：若指定预制则复制平台 SQL 设计
        if (StringUtils.isNotEmpty(request.getPresetReportCode())) {
            created = definitionManager.applyPlatformSqlPreset(
                    created.getId(), tenantId, request.getPresetReportCode().trim());
        }
        // Asm：映射出参
        return definitionCatalogService.requireDto(created.getId());
    }

    /**
     * 列出平台 SQL 预制报表目录。
     *
     * @return 预制项列表（含优先级）
     */
    public List<ReportSqlPresetItemDto> listSqlPresets() {
        return definitionCatalogService.listSqlPresets();
    }

    /**
     * 列出 SQL 设计器可用的元数据表。
     *
     * @return 表与列元数据
     */
    public List<ReportSqlMetadataTableDto> listSqlMetadataTables() {
        return definitionCatalogService.listSqlMetadataTables();
    }

    /**
     * 获取同 ReportCode 的平台出厂基线（供设计器对比）。
     *
     * @param request 当前院点定义主键
     * @return 平台基线 DTO；无基线时 null
     */
    public ReportDefinitionItemDto getPlatformBaseline(@Valid GetReportDefinitionRequest request) {
        return definitionCatalogService.getPlatformBaseline(request.getId());
    }

    /**
     * 将平台预制 SQL 设计应用到当前草稿。
     *
     * @param request 目标定义与预制编码
     * @return 更新后的定义 DTO
     */
    public ReportDefinitionItemDto applySqlPreset(@Valid ApplySqlPresetRequest request) {
        return definitionCatalogService.applySqlPreset(request.getId(), request.getPresetReportCode());
    }

    /**
     * 复制已有定义为新的 SQL 报表。
     *
     * @param request 源定义、新编码与名称
     * @return 新定义 DTO
     */
    public ReportDefinitionItemDto copyDefinition(@Valid CopyReportDefinitionRequest request) {
        return definitionCatalogService.copyDefinition(
                request.getSourceId(),
                request.getReportCode(),
                request.getName(),
                request.getRemark());
    }

    /**
     * 更新报表基本信息（名称、备注）。
     *
     * @param request 定义主键与待改字段
     * @return 更新后的定义 DTO
     */
    public ReportDefinitionItemDto updateDefinition(@Valid UpdateReportDefinitionRequest request) {
        // Pre：租户上下文
        Long tenantId = tenantContext.getCurrentTenantId();
        // Cmd：仅 SQL 报表可改基本信息
        ReportDefinition updated =
                definitionManager.updateBasic(request.getId(), tenantId, request.getName(), request.getRemark());
        return definitionCatalogService.requireDto(updated.getId());
    }

    /**
     * 软删报表定义（须先撤回发布）。
     *
     * @param request 定义主键
     */
    public void deleteDefinition(@Valid PublishReportDefinitionRequest request) {
        // Pre：租户上下文
        Long tenantId = tenantContext.getCurrentTenantId();
        // Cmd：软删并校验 Loader 报表不可删
        definitionManager.deleteReport(request.getId(), tenantId);
    }

    /**
     * 保存导出相关元数据（PDF 开关与模板编码）。
     *
     * @param request 定义主键与 PDF 配置
     * @return 更新后的定义 DTO
     */
    public ReportDefinitionItemDto saveDefinition(@Valid SaveReportDefinitionRequest request) {
        // Pre：当前租户
        Long tenantId = tenantContext.getCurrentTenantId();
        // Cmd：更新名称与 PDF 导出元数据
        ReportDefinition saved = definitionManager.saveMetadata(
                request.getId(),
                tenantId,
                request.getName(),
                request.getExportPdfEnabled(),
                request.getPdfTemplateCode());
        return definitionCatalogService.requireDto(saved.getId());
    }

    /**
     * 保存报表设计（SQL、表头/表底/筛选器、默认列）。
     *
     * @param request 设计内容与 PDF 配置
     * @return 更新后的定义 DTO
     */
    public ReportDefinitionItemDto saveReportDesign(@Valid SaveReportDesignRequest request) {
        // Pre：租户上下文
        Long tenantId = tenantContext.getCurrentTenantId();
        // Cmd：持久化设计 JSON 与 SQL（含白名单校验）
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

    /**
     * 发布报表，允许 queryPage/export。
     *
     * @param request 定义主键
     * @return 发布后的定义 DTO
     */
    public ReportDefinitionItemDto publishDefinition(@Valid PublishReportDefinitionRequest request) {
        // Pre：当前租户
        Long tenantId = tenantContext.getCurrentTenantId();
        // Cmd：校验 SQL/PDF 配置后改状态为 published
        ReportDefinition published = definitionManager.publish(request.getId(), tenantId);
        return definitionCatalogService.requireDto(published.getId());
    }

    /**
     * 撤回发布，恢复为草稿。
     *
     * @param request 定义主键
     * @return 草稿状态定义 DTO
     */
    public ReportDefinitionItemDto unpublishDefinition(@Valid PublishReportDefinitionRequest request) {
        // Pre：当前租户
        Long tenantId = tenantContext.getCurrentTenantId();
        // Cmd：状态改回草稿
        ReportDefinition draft = definitionManager.unpublish(request.getId(), tenantId);
        return definitionCatalogService.requireDto(draft.getId());
    }

    /**
     * 分页查询报表数据。
     *
     * @param request 报表编码、筛选与分页
     * @return 列、行、表头表底
     */
    public ReportQueryPageResultDto queryPage(@Valid ReportQueryPageRequest request) {
        // Gate：非设计预览时要求报表已发布
        requirePublishedIfNeeded(request.getReportCode(), request.getDesignPreview());
        // Cmd：委托执行层分页查询
        return queryExecutionService.queryPage(request);
    }

    /**
     * 导出报表为 xlsx（动态列）。
     *
     * @param request 报表编码与筛选（忽略分页，拉全量至多 {@link ReportDatasetLimits#MAX_SQL_ROWS} 行）
     * @return Base64 编码的 xlsx 与文件名
     */
    public ReportExportResultDto exportExcel(@Valid ReportQueryPageRequest request) {
        // Gate：未发布报表禁止导出（设计预览除外）
        requirePublishedIfNeeded(request.getReportCode(), Boolean.FALSE);
        // Pre：导出按最大页容量拉全量
        ReportQueryPageRequest exportRequest = copyForExport(request);
        // Cmd：分页查询与列装配
        ReportQueryPageResultDto page = queryPage(exportRequest);
        // Asm：动态列 xlsx
        ReportExportResultDto result = new ReportExportResultDto();
        result.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        result.setFileName(buildExportFileName(page.getReportCode()));
        List<TabularColumnDef> tabularColumns = toTabularColumns(page.getColumns());
        byte[] xlsxBytes = TabularExcelExporter.exportToXlsxBytes(
                page.getReportCode(), tabularColumns, page.getRows());
        result.setBodyBase64(Base64.getEncoder().encodeToString(xlsxBytes));
        return result;
    }

    /**
     * 获取当前用户的列定制配置（含隐藏列，供列设置弹窗）。
     *
     * @param request 报表编码
     * @return 列配置与是否已定制
     */
    public ReportColumnOverrideResultDto getColumnOverride(@Valid ReportColumnOverrideQueryRequest request) {
        // Gate：报表编码必填
        String reportCode = request.getReportCode().trim();
        Long tenantId = tenantContext.getCurrentTenantId();
        ReportDefinition definition = definitionManager.findByCode(tenantId, reportCode)
                .orElseThrow(() -> new ValidationException("报表不存在: " + reportCode));
        // Pre：默认列 + override（含隐藏列，供列设置弹窗）
        List<ReportColumnDto> defaults = columnSupport.parseColumns(definition.getDefaultColumnsJson());
        Long userId = operatorResolver.findOperatorIdOrNull();
        List<ReportColumnOverrideManager.ColumnSettingView> settings = columnOverrideManager.resolveColumnsForSettings(
                columnMergeSupport.toColumnViews(defaults),
                tenantId,
                reportCode,
                userId,
                null);
        // Asm：返回列配置与是否定制
        ReportColumnOverrideResultDto result = new ReportColumnOverrideResultDto();
        result.setReportCode(reportCode);
        result.setColumns(columnMergeSupport.toColumnDtos(settings));
        result.setCustomized(columnOverrideManager.findUserOverride(tenantId, reportCode, userId).isPresent());
        return result;
    }

    /**
     * 保存当前用户的列定制。
     *
     * @param request 报表编码与列 visible/sortOrder/width
     * @return merge 后的列配置
     */
    public ReportColumnOverrideResultDto saveColumnOverride(@Valid SaveReportColumnOverrideRequest request) {
        // Gate：须允许列定制
        String reportCode = request.getReportCode().trim();
        Long tenantId = tenantContext.getCurrentTenantId();
        ReportDefinition definition = definitionManager.findByCode(tenantId, reportCode)
                .orElseThrow(() -> new ValidationException("报表不存在: " + reportCode));
        if (Boolean.FALSE.equals(definition.getAllowColumnCustomize())) {
            // 出厂报表禁止列定制，防止破坏 Loader 字段绑定
            throw new ValidationException("该报表不允许列定制");
        }
        // Pre：当前操作人
        Long userId = operatorResolver.requireOperatorId("RPT_AUTH_REQUIRED", "请先登录后再保存列设置");
        // Cmd：持久化 override
        columnOverrideManager.saveUserOverride(
                tenantId, reportCode, userId, columnMergeSupport.toOverrideItems(request.getColumns()));
        // Asm：返回 merge 后列
        return getColumnOverride(newReportColumnOverrideQuery(reportCode));
    }

    /**
     * 重置当前用户的列定制，恢复默认列。
     *
     * @param request 报表编码
     */
    public void resetColumnOverride(@Valid ReportColumnOverrideQueryRequest request) {
        // Gate：登录用户
        Long userId = operatorResolver.requireOperatorId("RPT_AUTH_REQUIRED", "请先登录后再重置列设置");
        // Cmd：软删用户 override
        columnOverrideManager.resetUserOverride(
                tenantContext.getCurrentTenantId(), request.getReportCode().trim(), userId);
    }

    private static ReportColumnOverrideQueryRequest newReportColumnOverrideQuery(String reportCode) {
        ReportColumnOverrideQueryRequest query = new ReportColumnOverrideQueryRequest();
        query.setReportCode(reportCode);
        return query;
    }

    private static List<TabularColumnDef> toTabularColumns(List<ReportColumnDto> columns) {
        List<TabularColumnDef> result = new ArrayList<>();
        if (columns == null) {
            return result;
        }
        for (ReportColumnDto column : columns) {
            if (column == null || !StringUtils.isNotEmpty(column.getField())) {
                continue;
            }
            String header = column.getField().trim();
            if (StringUtils.isNotEmpty(column.getLabel())) {
                header = column.getLabel().trim();
            }
            result.add(new TabularColumnDef(column.getField().trim(), header));
        }
        return result;
    }

    private void requirePublishedIfNeeded(String reportCode, Boolean designPreview) {
        // 设计器试查跳过发布校验
        if (Boolean.TRUE.equals(designPreview)) {
            return;
        }
        if (!StringUtils.isNotEmpty(reportCode)) {
            return;
        }
        Long tenantId = tenantContext.getCurrentTenantId();
        // 未发布报表拒绝查询/导出，引导用户先发布
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
        copy.setPageSize(ReportDatasetLimits.MAX_SQL_ROWS);
        return copy;
    }

    private static String buildExportFileName(String reportCode) {
        String code = "report";
        if (StringUtils.isNotEmpty(reportCode)) {
            code = reportCode.trim();
        }
        return code + ".xlsx";
    }
}
