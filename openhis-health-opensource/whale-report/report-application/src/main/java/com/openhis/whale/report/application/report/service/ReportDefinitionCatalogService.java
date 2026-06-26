package com.openhis.whale.report.application.report.service;

import com.openhis.whale.core.infrastructure.multitenancy.TenantContext;
import com.openhis.whale.report.application.report.request.ListReportDefinitionsRequest;
import com.openhis.whale.report.application.report.response.ReportColumnDto;
import com.openhis.whale.report.application.report.response.ReportDefinitionItemDto;
import com.openhis.whale.report.application.report.response.ReportSqlPresetItemDto;
import com.openhis.whale.report.application.report.support.ReportDefinitionColumnSupport;
import com.openhis.whale.report.application.report.support.ReportDesignJsonSupport;
import com.openhis.whale.report.application.report.response.ReportSqlMetadataTableDto;
import com.openhis.whale.report.application.report.support.ReportSqlMetadataCatalog;
import com.openhis.whale.report.application.report.support.ReportLoader;
import com.openhis.whale.report.application.report.support.ReportLoaderRegistry;
import com.openhis.whale.report.domain.constants.ReportSqlPresetConstants;
import com.openhis.whale.report.domain.entity.ReportDefinition;
import com.openhis.whale.report.domain.service.ReportDefinitionManager;
import com.openhis.whale.report.domain.support.ReportTextSupport;
import org.springframework.stereotype.Service;
import com.openhis.whale.core.application.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;

/**
 * 报表定义目录：租户合并、Loader 种子同步、DTO 装配。
 *
 * <p>首次 list 时为租户同步平台出厂定义与 Loader 注册表；院点同 ReportCode 覆盖平台行。</p>
 */
@Service
public class ReportDefinitionCatalogService {

    private final ReportDefinitionManager definitionManager;
    private final ReportLoaderRegistry loaderRegistry;
    private final ReportDefinitionColumnSupport columnSupport;
    private final TenantContext tenantContext;
    private final ConcurrentHashMap<Long, Boolean> syncedTenants = new ConcurrentHashMap<>();

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

    /**
     * 分页列出当前租户可见的报表定义（含平台回退合并）。
     *
     * @param request 状态与关键词筛选；null 时视为无筛选
     * @return 按名称排序的定义列表
     */
    public List<ReportDefinitionItemDto> list(ListReportDefinitionsRequest request) {
        Long tenantId = tenantContext.getCurrentTenantId();
        syncMissingDefinitions(tenantId);

        String status = null;
        String keyword = null;
        if (request != null) {
            status = request.getStatus();
            keyword = request.getKeyword();
        }
        List<ReportDefinition> definitions = definitionManager.listForTenant(tenantId, status);
        Map<String, ReportLoader> loaderIndex = loaderRegistry.indexByCode();

        List<ReportDefinitionItemDto> items = new ArrayList<>();
        for (ReportDefinition definition : definitions) {
            if (!matchesKeyword(definition, keyword)) {
                // 关键词不匹配时跳过
                continue;
            }
            ReportLoader loader = loaderIndex.get(definition.getReportCode());
            items.add(toDto(definition, loader));
        }
        items.sort(Comparator.comparing(
                dto -> ReportTextSupport.nullToEmpty(dto.getName()), String.CASE_INSENSITIVE_ORDER));
        return items;
    }

    /**
     * 按主键加载定义 DTO（含租户权限校验）。
     *
     * @param id 定义主键
     * @return 装配后的 DTO
     */
    public ReportDefinitionItemDto requireDto(Long id) {
        Long tenantId = tenantContext.getCurrentTenantId();
        ReportDefinition definition = definitionManager.requireById(id, tenantId);
        return toDto(definition, loaderRegistry.find(definition.getReportCode()).orElse(null));
    }

    /**
     * 平台 SQL 预制报表目录。
     *
     * @return 含优先级解析的预制项列表
     */
    public List<ReportSqlPresetItemDto> listSqlPresets() {
        List<ReportSqlPresetItemDto> items = new ArrayList<>();
        for (ReportDefinition definition : definitionManager.listPlatformSqlPresets()) {
            ReportSqlPresetItemDto dto = new ReportSqlPresetItemDto();
            dto.setReportCode(definition.getReportCode());
            dto.setName(definition.getName());
            dto.setRemark(definition.getRemark());
            dto.setPriority(parsePresetPriority(definition.getRemark()));
            items.add(dto);
        }
        return items;
    }

    /**
     * SQL 设计器表/列元数据（静态目录）。
     *
     * @return 表与列元数据
     */
    public List<ReportSqlMetadataTableDto> listSqlMetadataTables() {
        return ReportSqlMetadataCatalog.tables();
    }

    /**
     * 获取同 ReportCode 的平台基线（设计器 diff 用）。
     *
     * @param id 当前院点定义主键
     * @return 平台基线 DTO；无可用基线时 null
     */
    public ReportDefinitionItemDto getPlatformBaseline(Long id) {
        Long tenantId = tenantContext.getCurrentTenantId();
        ReportDefinition current = definitionManager.requireById(id, tenantId);
        if (!StringUtils.isNotEmpty(current.getReportCode())) {
            return null;
        }
        Optional<ReportDefinition> platform =
                definitionManager.findPlatformByCode(current.getReportCode().trim());
        if (platform.isEmpty()) {
            return null;
        }
        ReportDefinition baseline = platform.get();
        // 预制 SQL 不可用且表头也为空时，不提供 diff 基线
        if (!ReportDefinitionManager.isUsableSqlPreset(baseline)
                && !StringUtils.isNotEmpty(baseline.getHeaderJson())) {
            return null;
        }
        return toDto(baseline, loaderRegistry.find(baseline.getReportCode()).orElse(null));
    }

    static String parsePresetPriority(String remark) {
        if (!StringUtils.isNotEmpty(remark)) {
            return "OTHER";
        }
        Matcher matcher = ReportSqlPresetConstants.PRESET_PRIORITY.matcher(remark);
        if (matcher.find()) {
            return "P" + matcher.group(1);
        }
        if (remark.contains("P2")) {
            return "P2";
        }
        if (remark.contains("P3")) {
            return "P3";
        }
        return "OTHER";
    }

    /**
     * 应用平台预制 SQL 到目标草稿。
     *
     * @param id               目标定义主键
     * @param presetReportCode 预制编码
     * @return 更新后的定义 DTO
     */
    public ReportDefinitionItemDto applySqlPreset(Long id, String presetReportCode) {
        Long tenantId = tenantContext.getCurrentTenantId();
        ReportDefinition saved = definitionManager.applyPlatformSqlPreset(id, tenantId, presetReportCode);
        return toDto(saved, loaderRegistry.find(saved.getReportCode()).orElse(null));
    }

    /**
     * 复制定义为新 SQL 报表并返回 DTO。
     *
     * @param sourceId   源定义主键
     * @param reportCode 新报表编码
     * @param name       新名称
     * @param remark     备注，可为 null
     * @return 新创建的草稿定义 DTO
     */
    public ReportDefinitionItemDto copyDefinition(
            Long sourceId, String reportCode, String name, String remark) {
        Long tenantId = tenantContext.getCurrentTenantId();
        ReportDefinition created =
                definitionManager.copyAsNewSqlDefinition(sourceId, tenantId, reportCode, name, remark);
        return toDto(created, loaderRegistry.find(created.getReportCode()).orElse(null));
    }

    /**
     * 首次 list 时为租户同步平台定义与 Loader 种子（每租户一次）。
     *
     * @param tenantId 租户主键
     */
    private void syncMissingDefinitions(Long tenantId) {
        if (tenantId == null || syncedTenants.containsKey(tenantId)) {
            // 已同步或无效租户则跳过
            return;
        }
        definitionManager.syncPlatformDefinitions(tenantId);
        for (ReportLoader loader : loaderRegistry.listAll()) {
            if (loader == null || !StringUtils.isNotEmpty(loader.reportCode())) {
                continue;
            }
            definitionManager.ensureFromLoader(
                    tenantId,
                    loader.reportCode().trim(),
                    loader.reportName(),
                    ReportDefinitionManager.deriveLoaderBeanName(loader.reportCode()),
                    columnSupport.buildDefaultColumnsJson(loader.columns()));
        }
        syncedTenants.put(tenantId, Boolean.TRUE);
    }

    private ReportDefinitionItemDto toDto(ReportDefinition definition, ReportLoader loader) {
        ReportDefinitionItemDto dto = new ReportDefinitionItemDto();
        dto.setId(definition.getId());
        dto.setReportCode(definition.getReportCode());
        dto.setName(definition.getName());
        dto.setStatus(definition.getStatus());
        dto.setExportPdfEnabled(definition.getExportPdfEnabled());
        dto.setPdfTemplateCode(definition.getPdfPrintCode());
        dto.setAllowColumnCustomize(definition.getAllowColumnCustomize());
        dto.setSourceType(definition.getSourceType());
        dto.setQuerySql(definition.getQuerySql());
        dto.setHeader(ReportDesignJsonSupport.parseHeaderConfig(definition.getHeaderJson()));
        dto.setFooter(ReportDesignJsonSupport.parseFooterConfig(definition.getFooterJson()));
        dto.setFilters(ReportDesignJsonSupport.parseFilters(definition.getFilterJson()));
        dto.setRemark(definition.getRemark());

        List<ReportColumnDto> columns = columnSupport.parseColumns(definition.getDefaultColumnsJson());
        if (columns.isEmpty() && loader != null) {
            // 定义未存列 JSON 时回退 Loader 默认列
            columns = loader.columns();
        }
        dto.setColumns(enrichColumns(columns, loader));
        return dto;
    }

    private static List<ReportColumnDto> enrichColumns(List<ReportColumnDto> columns, ReportLoader loader) {
        if (loader == null || loader.columns() == null || loader.columns().isEmpty()) {
            if (columns == null) {
                return List.of();
            }
            return columns;
        }
        Map<String, ReportColumnDto> loaderIndex = ReportLoaderRegistry.indexColumns(loader.columns());
        List<ReportColumnDto> result = new ArrayList<>();
        List<ReportColumnDto> source = columns;
        if (columns == null || columns.isEmpty()) {
            source = loader.columns();
        }
        for (ReportColumnDto column : source) {
            if (column == null || !StringUtils.isNotEmpty(column.getField())) {
                continue;
            }
            ReportColumnDto merged = new ReportColumnDto();
            merged.setField(column.getField());
            merged.setLabel(column.getLabel());
            ReportColumnDto fallback = loaderIndex.get(column.getField().trim());
            if (fallback != null) {
                if (!StringUtils.isNotEmpty(merged.getLabel())) {
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
        if (!StringUtils.isNotEmpty(keyword)) {
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
