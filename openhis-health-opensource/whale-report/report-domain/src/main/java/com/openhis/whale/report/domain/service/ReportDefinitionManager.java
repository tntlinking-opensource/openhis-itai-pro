package com.openhis.whale.report.domain.service;

import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.report.domain.constants.ReportDefinitionSourceType;
import com.openhis.whale.report.domain.constants.ReportDefinitionStatus;
import com.openhis.whale.report.domain.constants.ReportTenantScope;
import com.openhis.whale.report.domain.support.ReportTextSupport;
import com.openhis.whale.report.domain.entity.ReportDefinition;
import com.openhis.whale.report.domain.repository.ReportDefinitionRepository;
import com.openhis.whale.report.domain.support.ReportQueryValidator;
import org.springframework.stereotype.Service;
import com.openhis.whale.core.application.util.StringUtils;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * C 类报表定义生命周期与租户合并。
 *
 * <p>{@link #listForTenant} / {@link #findByCode} 均先查院点租户，缺失时回退
 * {@code tenant_id=0} 平台出厂定义；院点同 ReportCode 覆盖平台行。</p>
 *
 * <p>发布态：{@link ReportDefinitionStatus#DRAFT} / {@link ReportDefinitionStatus#PUBLISHED}；未发布报表在 queryPage 被拒绝
 *（设计预览 {@code designPreview=true} 除外）。</p>
 */
@Service
public class ReportDefinitionManager {

    private final ReportDefinitionRepository repository;

    public ReportDefinitionManager(ReportDefinitionRepository repository) {
        this.repository = repository;
    }

    /**
     * 租户可见定义列表（平台回退 + 院点覆盖合并）。
     *
     * @param tenantId 租户主键
     * @param status   可选状态筛选
     */
    public List<ReportDefinition> listForTenant(Long tenantId, String status) {
        long effectiveTenant = ReportTenantScope.effectiveTenant(tenantId);
        List<ReportDefinition> tenantDefs = queryTenantDefinitions(effectiveTenant, status);
        if (effectiveTenant == ReportTenantScope.PLATFORM_TENANT_ID) {
            // 平台租户无需与 tenant_id=0 合并
            return tenantDefs;
        }
        Map<String, ReportDefinition> merged = new LinkedHashMap<>();
        for (ReportDefinition platform : queryTenantDefinitions(ReportTenantScope.PLATFORM_TENANT_ID, null)) {
            if (platform != null && StringUtils.isNotEmpty(platform.getReportCode())) {
                merged.put(platform.getReportCode().trim(), platform);
            }
        }
        for (ReportDefinition tenant : tenantDefs) {
            if (tenant != null && StringUtils.isNotEmpty(tenant.getReportCode())) {
                merged.put(tenant.getReportCode().trim(), tenant);
            }
        }
        List<ReportDefinition> result = new ArrayList<>(merged.values());
        if (StringUtils.isNotEmpty(status)) {
            String normalizedStatus = status.trim();
            List<ReportDefinition> filtered = new ArrayList<>();
            for (ReportDefinition def : result) {
                if (statusEquals(def.getStatus(), normalizedStatus)) {
                    filtered.add(def);
                }
            }
            result = filtered;
        }
        result.sort(Comparator.comparing(
                def -> ReportTextSupport.nullToEmpty(def.getName()), String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    /**
     * 按 ReportCode 解析定义（院点优先，缺失回退平台 tenant_id=0）。
     */
    public Optional<ReportDefinition> findByCode(Long tenantId, String reportCode) {
        if (!StringUtils.isNotEmpty(reportCode)) {
            return Optional.empty();
        }
        String code = reportCode.trim();
        long effectiveTenant = ReportTenantScope.effectiveTenant(tenantId);
        Optional<ReportDefinition> tenantDef =
                repository.findByTenantIdAndReportCodeAndIsDeletedFalse(effectiveTenant, code);
        if (tenantDef.isPresent() || effectiveTenant == ReportTenantScope.PLATFORM_TENANT_ID) {
            return tenantDef;
        }
        // 院点无覆盖时回退平台出厂定义
        return repository.findByTenantIdAndReportCodeAndIsDeletedFalse(
                ReportTenantScope.PLATFORM_TENANT_ID, code);
    }

    /**
     * 按主键加载并校验租户操作权限。
     *
     * @throws com.openhis.whale.core.application.exception.ValidationException 不存在或无权
     */
    public ReportDefinition requireById(Long id, Long tenantId) {
        ReportDefinition definition = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ValidationException("报表定义不存在: " + id));
        long effectiveTenant = ReportTenantScope.effectiveTenant(tenantId);
        if (Long.valueOf(effectiveTenant).equals(definition.getTenantId())) {
            return definition;
        }
        if (ReportTenantScope.PLATFORM_TENANT_ID == definition.getTenantId()) {
            // 操作平台行时自动复制到院点租户
            return resolveTenantDefinition(definition, effectiveTenant);
        }
        throw new ValidationException("无权操作该报表定义");
    }

    private ReportDefinition resolveTenantDefinition(ReportDefinition platformDef, long effectiveTenant) {
        if (platformDef == null || !StringUtils.isNotEmpty(platformDef.getReportCode())) {
            throw new ValidationException("报表定义无效");
        }
        String code = platformDef.getReportCode().trim();
        return repository.findByTenantIdAndReportCodeAndIsDeletedFalse(effectiveTenant, code)
                .orElseGet(() -> repository.save(copyFromPlatform(platformDef, effectiveTenant)));
    }

    private List<ReportDefinition> queryTenantDefinitions(long tenantId, String status) {
        List<ReportDefinition> all = repository.findByTenantIdAndIsDeletedFalseOrderByNameAsc(tenantId);
        if (!StringUtils.isNotEmpty(status)) {
            return all;
        }
        String normalizedStatus = status.trim();
        return all.stream()
                .filter(def -> statusEquals(def.getStatus(), normalizedStatus))
                .toList();
    }

    private static boolean statusEquals(String actual, String expected) {
        return StringUtils.isNotEmpty(actual) && expected.equalsIgnoreCase(actual.trim());
    }

    /**
     * 将平台出厂定义（tenant_id=0）复制到当前租户，缺失 ReportCode 时插入。
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
            if (source == null || !StringUtils.isNotEmpty(source.getReportCode())) {
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

    /**
     * 从 Loader 注册信息确保院点存在对应 C 类报表定义（幂等插入）。
     *
     * @param tenantId            租户主键
     * @param reportCode          报表编码
     * @param reportName          展示名称
     * @param loaderBean          Spring Loader Bean 名，空则默认 reportLoader
     * @param defaultColumnsJson  默认列 JSON
     * @return 已存在或新创建的草稿定义
     */
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
        String resolvedLoaderBean = "reportLoader";
        if (StringUtils.isNotEmpty(loaderBean)) {
            resolvedLoaderBean = loaderBean.trim();
        }
        created.setLoaderBean(resolvedLoaderBean);
        created.setDefaultColumnsJson(defaultColumnsJson);
        created.setAllowColumnCustomize(Boolean.TRUE);
        created.setExportPdfEnabled(Boolean.FALSE);
        created.setSourceType(ReportDefinitionSourceType.LOADER);
        created.setStatus(ReportDefinitionStatus.DRAFT);
        created.setIsDeleted(Boolean.FALSE);
        return repository.save(created);
    }

    /**
     * 更新报表元数据（名称、PDF 导出开关与模板编码）。
     *
     * @param id               定义主键
     * @param tenantId         租户主键
     * @param name             新名称，空则跳过
     * @param exportPdfEnabled PDF 导出开关，null 则跳过
     * @param pdfPrintCode     PDF 模板编码，null 表示清空
     * @return 保存后的定义
     */
    public ReportDefinition saveMetadata(
            Long id,
            Long tenantId,
            String name,
            Boolean exportPdfEnabled,
            String pdfPrintCode) {
        ReportDefinition definition = requireById(id, tenantId);
        if (StringUtils.isNotEmpty(name)) {
            definition.setName(name.trim());
        }
        if (exportPdfEnabled != null) {
            definition.setExportPdfEnabled(exportPdfEnabled);
        }
        if (pdfPrintCode != null) {
            definition.setPdfPrintCode(trimToNull(pdfPrintCode));
        }
        return repository.save(definition);
    }

    /**
     * 发布报表：校验 SQL/PDF 配置后置为 published。
     *
     * @param id       定义主键
     * @param tenantId 租户主键
     * @return 发布后的定义
     */
    public ReportDefinition publish(Long id, Long tenantId) {
        ReportDefinition definition = requireById(id, tenantId);
        validateBeforePublish(definition);
        // 通过校验后切换为已发布
        definition.setStatus(ReportDefinitionStatus.PUBLISHED);
        return repository.save(definition);
    }

    /**
     * 新建 SQL 类型报表定义（草稿，含默认筛选/页眉页脚 JSON）。
     *
     * @param tenantId   租户主键
     * @param reportCode 唯一报表编码
     * @param name       展示名称
     * @param remark     备注，可为 null
     * @return 新创建的草稿定义
     */
    public ReportDefinition createSqlDefinition(Long tenantId, String reportCode, String name, String remark) {
        if (!StringUtils.isNotEmpty(reportCode)) {
            throw new ValidationException("报表编码不能为空");
        }
        if (!StringUtils.isNotEmpty(name)) {
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
        created.setRemark(trimToNull(remark));
        created.setLoaderBean("reportSqlLoader");
        created.setSourceType(ReportDefinitionSourceType.SQL);
        created.setAllowColumnCustomize(Boolean.TRUE);
        created.setExportPdfEnabled(Boolean.FALSE);
        created.setStatus(ReportDefinitionStatus.DRAFT);
        created.setDefaultColumnsJson("[]");
        created.setFilterJson(defaultDateRangeFilterJson());
        created.setHeaderJson(defaultHeaderJson(name.trim()));
        created.setFooterJson(defaultFooterJson());
        created.setIsDeleted(Boolean.FALSE);
        return repository.save(created);
    }

    /**
     * 更新 SQL 报表基础信息（名称、备注），仅限 SQL 类型。
     *
     * @param id       定义主键
     * @param tenantId 租户主键
     * @param name     新名称，空则跳过
     * @param remark   新备注，null 表示清空
     * @return 保存后的定义
     */
    public ReportDefinition updateBasic(Long id, Long tenantId, String name, String remark) {
        ReportDefinition definition = requireById(id, tenantId);
        requireSqlEditable(definition);
        if (StringUtils.isNotEmpty(name)) {
            definition.setName(name.trim());
        }
        if (remark != null) {
            definition.setRemark(trimToNull(remark));
        }
        return repository.save(definition);
    }

    /**
     * 保存 SQL 报表设计内容（查询语句、列、筛选、页眉页脚、PDF 配置）。
     *
     * @param id                  定义主键
     * @param tenantId            租户主键
     * @param name                名称，空则跳过
     * @param querySql            查询 SQL，null 跳过
     * @param headerJson          页眉 JSON
     * @param footerJson          页脚 JSON
     * @param filterJson          筛选器 JSON
     * @param defaultColumnsJson  列定义 JSON
     * @param exportPdfEnabled    PDF 开关
     * @param pdfPrintCode        PDF 模板编码
     * @return 保存后的定义
     */
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
            String pdfPrintCode) {
        ReportDefinition definition = requireById(id, tenantId);
        requireSqlEditable(definition);
        if (StringUtils.isNotEmpty(name)) {
            definition.setName(name.trim());
        }
        if (querySql != null) {
            String normalized = trimToNull(querySql);
            // SQL 类型须通过白名单校验，防止设计器写入危险语句
            if (normalized != null && ReportDefinitionSourceType.SQL.equalsIgnoreCase(definition.getSourceType())) {
                ReportQueryValidator.validate(normalized);
            }
            definition.setQuerySql(normalized);
        }
        if (headerJson != null) {
            definition.setHeaderJson(trimToNull(headerJson));
        }
        if (footerJson != null) {
            definition.setFooterJson(trimToNull(footerJson));
        }
        if (filterJson != null) {
            definition.setFilterJson(trimToNull(filterJson));
        }
        if (defaultColumnsJson != null) {
            String resolvedColumnsJson = "[]";
            if (StringUtils.isNotEmpty(defaultColumnsJson)) {
                resolvedColumnsJson = defaultColumnsJson.trim();
            }
            definition.setDefaultColumnsJson(resolvedColumnsJson);
        }
        if (exportPdfEnabled != null) {
            definition.setExportPdfEnabled(exportPdfEnabled);
        }
        if (pdfPrintCode != null) {
            definition.setPdfPrintCode(trimToNull(pdfPrintCode));
        }
        return repository.save(definition);
    }

    /**
     * 按编码查找平台出厂定义（tenant_id=0）。
     *
     * @param reportCode 报表编码
     * @return 平台定义，编码空或不存在时 empty
     */
    public Optional<ReportDefinition> findPlatformByCode(String reportCode) {
        if (!StringUtils.isNotEmpty(reportCode)) {
            return Optional.empty();
        }
        return repository.findByTenantIdAndReportCodeAndIsDeletedFalse(
                ReportTenantScope.PLATFORM_TENANT_ID, reportCode.trim());
    }

    /**
     * 软删除 SQL 报表（须先撤回发布）。
     *
     * @param id       定义主键
     * @param tenantId 租户主键
     */
    public void deleteReport(Long id, Long tenantId) {
        ReportDefinition definition = requireById(id, tenantId);
        requireSqlEditable(definition);
        if (statusEquals(definition.getStatus(), ReportDefinitionStatus.PUBLISHED)) {
            // 已发布须先 unpublish，防止线上报表被误删
            throw new ValidationException("已发布报表请先撤回再删除");
        }
        definition.setIsDeleted(Boolean.TRUE);
        definition.setDeletedAt(LocalDateTime.now());
        repository.save(definition);
    }

    /**
     * 复制为新的 SQL 报表（含设计内容，状态为草稿）。
     *
     * @param sourceId      源定义主键
     * @param tenantId      租户主键
     * @param newReportCode 新报表编码
     * @param newName       新名称
     * @param remark        备注，空则沿用源备注
     * @return 新创建的草稿定义
     */
    public ReportDefinition copyAsNewSqlDefinition(
            Long sourceId, Long tenantId, String newReportCode, String newName, String remark) {
        if (!StringUtils.isNotEmpty(newReportCode)) {
            throw new ValidationException("报表编码不能为空");
        }
        if (!StringUtils.isNotEmpty(newName)) {
            throw new ValidationException("报表名称不能为空");
        }
        ReportDefinition source = requireById(sourceId, tenantId);
        String code = newReportCode.trim();
        long effectiveTenant = ReportTenantScope.effectiveTenant(tenantId);
        if (repository.findByTenantIdAndReportCodeAndIsDeletedFalse(effectiveTenant, code).isPresent()) {
            throw new ValidationException("报表编码已存在: " + code);
        }
        ReportDefinition created = copyDesignFields(source, effectiveTenant, code, newName.trim());
        String resolvedRemark = source.getRemark();
        if (StringUtils.isNotEmpty(remark)) {
            resolvedRemark = remark.trim();
        }
        created.setRemark(resolvedRemark);
        created.setSourceType(ReportDefinitionSourceType.SQL);
        created.setLoaderBean("reportSqlLoader");
        created.setStatus(ReportDefinitionStatus.DRAFT);
        return repository.save(created);
    }

    /**
     * 将平台预制 SQL 报表的设计内容应用到当前 SQL 报表（保持草稿）。
     *
     * @param targetId         目标定义主键
     * @param tenantId         租户主键
     * @param presetReportCode 平台预制报表编码
     * @return 应用预制后的定义
     */
    public ReportDefinition applyPlatformSqlPreset(Long targetId, Long tenantId, String presetReportCode) {
        if (!StringUtils.isNotEmpty(presetReportCode)) {
            throw new ValidationException("预制报表编码不能为空");
        }
        ReportDefinition target = requireById(targetId, tenantId);
        requireSqlEditable(target);
        ReportDefinition preset = requirePlatformPreset(presetReportCode.trim());
        String presetSql = preset.getQuerySql();
        ReportQueryValidator.validate(presetSql);
        target.setQuerySql(presetSql);
        target.setDefaultColumnsJson(preset.getDefaultColumnsJson());
        target.setFilterJson(preset.getFilterJson());
        if (StringUtils.isNotEmpty(preset.getHeaderJson())) {
            target.setHeaderJson(preset.getHeaderJson());
        }
        if (StringUtils.isNotEmpty(preset.getFooterJson())) {
            target.setFooterJson(preset.getFooterJson());
        }
        return repository.save(target);
    }

    /**
     * 列出平台侧可用的 SQL 预制模板（已配置有效 SQL）。
     *
     * @return 预制定义列表
     */
    public List<ReportDefinition> listPlatformSqlPresets() {
        return repository.findByTenantIdAndIsDeletedFalseOrderByNameAsc(ReportTenantScope.PLATFORM_TENANT_ID)
                .stream()
                .filter(def -> ReportDefinitionSourceType.SQL.equalsIgnoreCase(def.getSourceType()))
                .filter(ReportDefinitionManager::isUsableSqlPreset)
                .toList();
    }

    private ReportDefinition requirePlatformPreset(String presetReportCode) {
        ReportDefinition preset = repository
                .findByTenantIdAndReportCodeAndIsDeletedFalse(
                        ReportTenantScope.PLATFORM_TENANT_ID, presetReportCode)
                .orElseThrow(() -> new ValidationException("预制报表不存在: " + presetReportCode));
        if (!ReportDefinitionSourceType.SQL.equalsIgnoreCase(preset.getSourceType())) {
            throw new ValidationException("预制报表不是 SQL 类型: " + presetReportCode);
        }
        if (!isUsableSqlPreset(preset)) {
            throw new ValidationException("预制报表 SQL 尚未配置，无法应用: " + presetReportCode);
        }
        return preset;
    }

    /**
     * 判断平台 SQL 预制是否已配置可执行语句（排除占位 WHERE 1=0）。
     *
     * @param definition 报表定义
     * @return true 表示可作为预制应用
     */
    public static boolean isUsableSqlPreset(ReportDefinition definition) {
        if (definition == null || !StringUtils.isNotEmpty(definition.getQuerySql())) {
            return false;
        }
        String sql = definition.getQuerySql().trim();
        return !sql.contains("WHERE 1=0") && !sql.contains("where 1=0");
    }

    private static ReportDefinition copyDesignFields(
            ReportDefinition source, long tenantId, String reportCode, String name) {
        ReportDefinition copy = new ReportDefinition();
        copy.setTenantId(tenantId);
        copy.setReportCode(reportCode);
        copy.setName(name);
        copy.setLoaderBean(source.getLoaderBean());
        copy.setDefaultColumnsJson(source.getDefaultColumnsJson());
        copy.setAllowColumnCustomize(source.getAllowColumnCustomize());
        copy.setExportPdfEnabled(source.getExportPdfEnabled());
        copy.setPdfPrintCode(source.getPdfPrintCode());
        copy.setSourceType(source.getSourceType());
        copy.setQuerySql(source.getQuerySql());
        copy.setHeaderJson(source.getHeaderJson());
        copy.setFooterJson(source.getFooterJson());
        copy.setFilterJson(source.getFilterJson());
        copy.setIsDeleted(Boolean.FALSE);
        return copy;
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
        copy.setPdfPrintCode(source.getPdfPrintCode());
        String resolvedStatus = ReportDefinitionStatus.DRAFT;
        if (StringUtils.isNotEmpty(source.getStatus())) {
            resolvedStatus = source.getStatus().trim();
        }
        copy.setStatus(resolvedStatus);
        copy.setSourceType(source.getSourceType());
        copy.setQuerySql(source.getQuerySql());
        copy.setHeaderJson(source.getHeaderJson());
        copy.setFooterJson(source.getFooterJson());
        copy.setFilterJson(source.getFilterJson());
        copy.setRemark(source.getRemark());
        copy.setIsDeleted(Boolean.FALSE);
        return copy;
    }

    private static void validateBeforePublish(ReportDefinition definition) {
        if (ReportDefinitionSourceType.SQL.equalsIgnoreCase(definition.getSourceType())) {
            if (!StringUtils.isNotEmpty(definition.getQuerySql())) {
                throw new ValidationException("SQL 报表未配置查询语句，无法发布");
            }
            ReportQueryValidator.validate(definition.getQuerySql().trim());
        }
        if (Boolean.TRUE.equals(definition.getExportPdfEnabled())
                && !StringUtils.isNotEmpty(definition.getPdfPrintCode())) {
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

    /**
     * 撤回发布，将状态置为草稿。
     *
     * @param id       定义主键
     * @param tenantId 租户主键
     * @return 撤回后的定义
     */
    public ReportDefinition unpublish(Long id, Long tenantId) {
        ReportDefinition definition = requireById(id, tenantId);
        definition.setStatus(ReportDefinitionStatus.DRAFT);
        return repository.save(definition);
    }

    /**
     * 判断指定报表编码在租户下是否已发布。
     *
     * @param tenantId   租户主键
     * @param reportCode 报表编码
     * @return true 表示已发布
     */
    public boolean isPublished(Long tenantId, String reportCode) {
        return findByCode(tenantId, reportCode)
                .map(def -> statusEquals(def.getStatus(), ReportDefinitionStatus.PUBLISHED))
                .orElse(false);
    }

    /**
     * 由报表编码推导 Loader Spring Bean 名（reportCode + Loader）。
     *
     * @param reportCode 报表编码
     * @return Bean 名，编码空时返回 reportLoader
     */
    public static String deriveLoaderBeanName(String reportCode) {
        if (!StringUtils.isNotEmpty(reportCode)) {
            return "reportLoader";
        }
        return reportCode.trim() + "Loader";
    }

    /**
     * 将定义列表按 reportCode 建索引（同编码保留首次出现）。
     *
     * @param definitions 定义列表，可为 null
     * @return reportCode → 定义 的有序 Map
     */
    public static Map<String, ReportDefinition> indexByCode(List<ReportDefinition> definitions) {
        Map<String, ReportDefinition> map = new LinkedHashMap<>();
        if (definitions == null) {
            return map;
        }
        for (ReportDefinition definition : definitions) {
            if (definition == null || !StringUtils.isNotEmpty(definition.getReportCode())) {
                continue;
            }
            map.putIfAbsent(definition.getReportCode().trim(), definition);
        }
        return map;
    }
}
