package com.openhis.whale.report.domain.service;

import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.report.domain.constants.ReportDefinitionSourceType;
import com.openhis.whale.report.domain.constants.ReportTenantScope;
import com.openhis.whale.report.domain.entity.ReportDefinition;
import com.openhis.whale.report.domain.repository.ReportDefinitionRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ReportDefinitionManager {

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_PUBLISHED = "published";

    private final ReportDefinitionRepository repository;

    public ReportDefinitionManager(ReportDefinitionRepository repository) {
        this.repository = repository;
    }

    public List<ReportDefinition> listForTenant(Long tenantId, String status) {
        long effectiveTenant = ReportTenantScope.effectiveTenant(tenantId);
        List<ReportDefinition> tenantDefs = queryTenantDefinitions(effectiveTenant, status);
        if (effectiveTenant == ReportTenantScope.PLATFORM_TENANT_ID) {
            return tenantDefs;
        }
        Map<String, ReportDefinition> merged = new LinkedHashMap<>();
        for (ReportDefinition platform : queryTenantDefinitions(ReportTenantScope.PLATFORM_TENANT_ID, null)) {
            if (platform != null && StringUtils.hasText(platform.getReportCode())) {
                merged.put(platform.getReportCode().trim(), platform);
            }
        }
        for (ReportDefinition tenant : tenantDefs) {
            if (tenant != null && StringUtils.hasText(tenant.getReportCode())) {
                merged.put(tenant.getReportCode().trim(), tenant);
            }
        }
        List<ReportDefinition> result = new ArrayList<>(merged.values());
        if (StringUtils.hasText(status)) {
            String normalizedStatus = status.trim();
            result = result.stream()
                    .filter(def -> normalizedStatus.equalsIgnoreCase(def.getStatus()))
                    .toList();
        }
        result.sort(Comparator.comparing(
                def -> def.getName() == null ? "" : def.getName(), String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public Optional<ReportDefinition> findByCode(Long tenantId, String reportCode) {
        if (!StringUtils.hasText(reportCode)) {
            return Optional.empty();
        }
        String code = reportCode.trim();
        long effectiveTenant = ReportTenantScope.effectiveTenant(tenantId);
        Optional<ReportDefinition> tenantDef =
                repository.findByTenantIdAndReportCodeAndIsDeletedFalse(effectiveTenant, code);
        if (tenantDef.isPresent() || effectiveTenant == ReportTenantScope.PLATFORM_TENANT_ID) {
            return tenantDef;
        }
        return repository.findByTenantIdAndReportCodeAndIsDeletedFalse(
                ReportTenantScope.PLATFORM_TENANT_ID, code);
    }

    public ReportDefinition requireById(Long id, Long tenantId) {
        ReportDefinition definition = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ValidationException("报表定义不存在: " + id));
        long effectiveTenant = ReportTenantScope.effectiveTenant(tenantId);
        if (Long.valueOf(effectiveTenant).equals(definition.getTenantId())) {
            return definition;
        }
        if (ReportTenantScope.PLATFORM_TENANT_ID == definition.getTenantId()) {
            return resolveTenantDefinition(definition, effectiveTenant);
        }
        throw new ValidationException("无权操作该报表定义");
    }

    private ReportDefinition resolveTenantDefinition(ReportDefinition platformDef, long effectiveTenant) {
        if (platformDef == null || !StringUtils.hasText(platformDef.getReportCode())) {
            throw new ValidationException("报表定义无效");
        }
        String code = platformDef.getReportCode().trim();
        return repository.findByTenantIdAndReportCodeAndIsDeletedFalse(effectiveTenant, code)
                .orElseGet(() -> repository.save(copyFromPlatform(platformDef, effectiveTenant)));
    }

    private List<ReportDefinition> queryTenantDefinitions(long tenantId, String status) {
        if (StringUtils.hasText(status)) {
            return repository.findByTenantIdAndStatusAndIsDeletedFalseOrderByNameAsc(tenantId, status.trim());
        }
        return repository.findByTenantIdAndIsDeletedFalseOrderByNameAsc(tenantId);
    }

    /**
     * 将平台出厂定义（tenant_id=0）复制到当前租户，缺失 reportCode 时插入。
     * 院点登录后首次打开报表模板/中心时调用，使 Flyway 种子对业务租户可见。
     */
    public int syncPlatformDefinitions(Long tenantId) {
        long effectiveTenant = ReportTenantScope.effectiveTenant(tenantId);
        if (effectiveTenant == ReportTenantScope.PLATFORM_TENANT_ID) {
            return 0;
        }
        List<ReportDefinition> platformDefs = repository.findByTenantIdAndIsDeletedFalseOrderByNameAsc(
                ReportTenantScope.PLATFORM_TENANT_ID);
        int synced = 0;
        for (ReportDefinition source : platformDefs) {
            if (source == null || !StringUtils.hasText(source.getReportCode())) {
                continue;
            }
            String code = source.getReportCode().trim();
            if (repository.findByTenantIdAndReportCodeAndIsDeletedFalse(effectiveTenant, code).isPresent()) {
                continue;
            }
            repository.save(copyFromPlatform(source, effectiveTenant));
            synced++;
        }
        return synced;
    }

    public ReportDefinition ensureFromLoader(
            Long tenantId,
            String reportCode,
            String reportName,
            String loaderBean,
            String defaultColumnsJson) {
        long effectiveTenant = ReportTenantScope.effectiveTenant(tenantId);
        Optional<ReportDefinition> existing =
                repository.findByTenantIdAndReportCodeAndIsDeletedFalse(effectiveTenant, reportCode);
        if (existing.isPresent()) {
            return existing.get();
        }
        ReportDefinition created = new ReportDefinition();
        created.setTenantId(effectiveTenant);
        created.setReportCode(reportCode);
        created.setName(reportName);
        created.setLoaderBean(StringUtils.hasText(loaderBean) ? loaderBean.trim() : "reportLoader");
        created.setDefaultColumnsJson(defaultColumnsJson);
        created.setAllowColumnCustomize(Boolean.TRUE);
        created.setExportPdfEnabled(Boolean.FALSE);
        created.setSourceType(ReportDefinitionSourceType.LOADER);
        created.setStatus(STATUS_DRAFT);
        created.setCreatedAt(LocalDateTime.now());
        created.setIsDeleted(Boolean.FALSE);
        return repository.save(created);
    }

    public ReportDefinition saveMetadata(
            Long id,
            Long tenantId,
            String name,
            Boolean exportPdfEnabled,
            String pdfTemplateCode) {
        ReportDefinition definition = requireById(id, tenantId);
        if (StringUtils.hasText(name)) {
            definition.setName(name.trim());
        }
        if (exportPdfEnabled != null) {
            definition.setExportPdfEnabled(exportPdfEnabled);
        }
        if (pdfTemplateCode != null) {
            definition.setPdfTemplateCode(StringUtils.hasText(pdfTemplateCode) ? pdfTemplateCode.trim() : null);
        }
        definition.setUpdatedAt(LocalDateTime.now());
        return repository.save(definition);
    }

    public ReportDefinition publish(Long id, Long tenantId) {
        ReportDefinition definition = requireById(id, tenantId);
        validateBeforePublish(definition);
        definition.setStatus(STATUS_PUBLISHED);
        definition.setUpdatedAt(LocalDateTime.now());
        return repository.save(definition);
    }

    public ReportDefinition createSqlReport(Long tenantId, String reportCode, String name, String remark) {
        if (!StringUtils.hasText(reportCode)) {
            throw new ValidationException("报表编码不能为空");
        }
        if (!StringUtils.hasText(name)) {
            throw new ValidationException("报表名称不能为空");
        }
        String code = reportCode.trim();
        long effectiveTenant = ReportTenantScope.effectiveTenant(tenantId);
        if (repository.findByTenantIdAndReportCodeAndIsDeletedFalse(effectiveTenant, code).isPresent()) {
            throw new ValidationException("报表编码已存在: " + code);
        }
        ReportDefinition created = new ReportDefinition();
        created.setTenantId(effectiveTenant);
        created.setReportCode(code);
        created.setName(name.trim());
        created.setRemark(StringUtils.hasText(remark) ? remark.trim() : null);
        created.setLoaderBean("sqlReportLoader");
        created.setSourceType(ReportDefinitionSourceType.SQL);
        created.setAllowColumnCustomize(Boolean.TRUE);
        created.setExportPdfEnabled(Boolean.FALSE);
        created.setStatus(STATUS_DRAFT);
        created.setDefaultColumnsJson("[]");
        created.setFilterJson(defaultDateRangeFilterJson());
        created.setHeaderJson(defaultHeaderJson(name.trim()));
        created.setFooterJson(defaultFooterJson());
        created.setCreatedAt(LocalDateTime.now());
        created.setIsDeleted(Boolean.FALSE);
        return repository.save(created);
    }

    public ReportDefinition updateBasic(Long id, Long tenantId, String name, String remark) {
        ReportDefinition definition = requireById(id, tenantId);
        requireSqlEditable(definition);
        if (StringUtils.hasText(name)) {
            definition.setName(name.trim());
        }
        if (remark != null) {
            definition.setRemark(StringUtils.hasText(remark) ? remark.trim() : null);
        }
        definition.setUpdatedAt(LocalDateTime.now());
        return repository.save(definition);
    }

    public ReportDefinition saveDesign(
            Long id,
            Long tenantId,
            String name,
            String querySql,
            String headerJson,
            String footerJson,
            String filterJson,
            String defaultColumnsJson,
            Boolean exportPdfEnabled,
            String pdfTemplateCode) {
        ReportDefinition definition = requireById(id, tenantId);
        if (StringUtils.hasText(name)) {
            definition.setName(name.trim());
        }
        if (querySql != null) {
            definition.setQuerySql(StringUtils.hasText(querySql) ? querySql.trim() : null);
        }
        if (headerJson != null) {
            definition.setHeaderJson(StringUtils.hasText(headerJson) ? headerJson.trim() : null);
        }
        if (footerJson != null) {
            definition.setFooterJson(StringUtils.hasText(footerJson) ? footerJson.trim() : null);
        }
        if (filterJson != null) {
            definition.setFilterJson(StringUtils.hasText(filterJson) ? filterJson.trim() : null);
        }
        if (defaultColumnsJson != null) {
            definition.setDefaultColumnsJson(
                    StringUtils.hasText(defaultColumnsJson) ? defaultColumnsJson.trim() : "[]");
        }
        if (exportPdfEnabled != null) {
            definition.setExportPdfEnabled(exportPdfEnabled);
        }
        if (pdfTemplateCode != null) {
            definition.setPdfTemplateCode(StringUtils.hasText(pdfTemplateCode) ? pdfTemplateCode.trim() : null);
        }
        definition.setUpdatedAt(LocalDateTime.now());
        return repository.save(definition);
    }

    public void deleteReport(Long id, Long tenantId) {
        ReportDefinition definition = requireById(id, tenantId);
        requireSqlEditable(definition);
        if (STATUS_PUBLISHED.equalsIgnoreCase(definition.getStatus())) {
            throw new ValidationException("已发布报表请先撤回再删除");
        }
        definition.setIsDeleted(Boolean.TRUE);
        definition.setDeletedAt(LocalDateTime.now());
        repository.save(definition);
    }

    private static ReportDefinition copyFromPlatform(ReportDefinition source, long tenantId) {
        ReportDefinition copy = new ReportDefinition();
        copy.setTenantId(tenantId);
        copy.setReportCode(source.getReportCode());
        copy.setName(source.getName());
        copy.setLoaderBean(source.getLoaderBean());
        copy.setDefaultColumnsJson(source.getDefaultColumnsJson());
        copy.setAllowColumnCustomize(source.getAllowColumnCustomize());
        copy.setExportPdfEnabled(source.getExportPdfEnabled());
        copy.setPdfTemplateCode(source.getPdfTemplateCode());
        copy.setStatus(source.getStatus());
        copy.setSourceType(source.getSourceType());
        copy.setQuerySql(source.getQuerySql());
        copy.setHeaderJson(source.getHeaderJson());
        copy.setFooterJson(source.getFooterJson());
        copy.setFilterJson(source.getFilterJson());
        copy.setRemark(source.getRemark());
        copy.setCreatedAt(LocalDateTime.now());
        copy.setIsDeleted(Boolean.FALSE);
        return copy;
    }

    private static void validateBeforePublish(ReportDefinition definition) {
        if (ReportDefinitionSourceType.SQL.equalsIgnoreCase(definition.getSourceType())) {
            if (!StringUtils.hasText(definition.getQuerySql())) {
                throw new ValidationException("SQL 报表未配置查询语句，无法发布");
            }
        }
        if (Boolean.TRUE.equals(definition.getExportPdfEnabled())
                && !StringUtils.hasText(definition.getPdfTemplateCode())) {
            throw new ValidationException("已启用 PDF 导出但未配置 PDF 模板编码");
        }
    }

    private static void requireSqlEditable(ReportDefinition definition) {
        if (!ReportDefinitionSourceType.SQL.equalsIgnoreCase(definition.getSourceType())) {
            throw new ValidationException("出厂 Loader 报表不可编辑或删除，请复制为 SQL 报表后定制");
        }
    }

    private static String defaultDateRangeFilterJson() {
        return """
                [
                  {"field":"dateFrom","label":"开始日期","type":"date"},
                  {"field":"dateTo","label":"结束日期","type":"date"},
                  {"field":"keyword","label":"关键词","type":"text"}
                ]
                """;
    }

    private static String defaultHeaderJson(String title) {
        return """
                {"title":"%s","subtitle":"{{dateRange}}","lines":[{"type":"split","left":"编制单位：________","right":"打印日期：{{dateRange}}"}]}
                """
                .formatted(title.replace("\"", "\\\""));
    }

    private static String defaultFooterJson() {
        return """
                {"notes":"","lines":["制表人：________","审核人：________"]}
                """;
    }

    public ReportDefinition unpublish(Long id, Long tenantId) {
        ReportDefinition definition = requireById(id, tenantId);
        definition.setStatus(STATUS_DRAFT);
        definition.setUpdatedAt(LocalDateTime.now());
        return repository.save(definition);
    }

    public boolean isPublished(Long tenantId, String reportCode) {
        return findByCode(tenantId, reportCode)
                .map(def -> STATUS_PUBLISHED.equalsIgnoreCase(def.getStatus()))
                .orElse(false);
    }

    public static String deriveLoaderBeanName(String reportCode) {
        if (!StringUtils.hasText(reportCode)) {
            return "reportLoader";
        }
        return reportCode.trim() + "Loader";
    }

    public static Map<String, ReportDefinition> indexByCode(List<ReportDefinition> definitions) {
        Map<String, ReportDefinition> map = new LinkedHashMap<>();
        if (definitions == null) {
            return map;
        }
        for (ReportDefinition definition : definitions) {
            if (definition == null || !StringUtils.hasText(definition.getReportCode())) {
                continue;
            }
            map.putIfAbsent(definition.getReportCode().trim(), definition);
        }
        return map;
    }
}
