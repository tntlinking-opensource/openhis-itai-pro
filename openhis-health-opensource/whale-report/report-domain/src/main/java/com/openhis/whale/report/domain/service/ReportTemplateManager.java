package com.openhis.whale.report.domain.service;

import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.report.domain.constants.ReportTenantScope;
import com.openhis.whale.report.domain.entity.ReportTemplate;
import com.openhis.whale.report.domain.repository.ReportTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 打印模板生命周期：平台 seed、院点覆盖、草稿 fork 与发布。
 */
@Service
public class ReportTemplateManager {

    public static final String STATUS_PUBLISHED = "published";
    public static final String STATUS_DRAFT = "draft";

    private final ReportTemplateRepository repository;

    public ReportTemplateManager(ReportTemplateRepository repository) {
        this.repository = repository;
    }

    /**
     * 按租户解析已发布模板；院点未命中时回退平台 seed。
     *
     * @param code           模板编码
     * @param tenantId       院点租户，可为 null（视为平台）
     * @param organizationId 预留，当前未参与过滤
     */
    public ReportTemplate findPublished(String code, Long tenantId, Long organizationId) {
        if (!StringUtils.hasText(code)) {
            throw new BusinessException("RPT_TEMPLATE_CODE_EMPTY", "templateCode 不能为空");
        }
        long effectiveTenant = ReportTenantScope.effectiveTenant(tenantId);
        Optional<ReportTemplate> found = repository
                .findFirstByTenantIdAndCodeAndStatusAndIsDeletedFalseOrderByVersionDesc(
                        effectiveTenant, code.trim(), STATUS_PUBLISHED);
        if (found.isEmpty() && effectiveTenant != ReportTenantScope.PLATFORM_TENANT_ID) {
            found = repository.findFirstByTenantIdAndCodeAndStatusAndIsDeletedFalseOrderByVersionDesc(
                    ReportTenantScope.PLATFORM_TENANT_ID, code.trim(), STATUS_PUBLISHED);
        }
        return found.orElseThrow(() -> new BusinessException(
                "RPT_TEMPLATE_NOT_FOUND", "未找到已发布模板: " + code));
    }

    /**
     * 工作台模板列表：院点同 code 覆盖平台 seed；每个 code 取该租户下最高 version 一条。
     */
    public List<ReportTemplate> listMergedForTenant(Long tenantId) {
        long effectiveTenant = ReportTenantScope.effectiveTenant(tenantId);
        Map<String, ReportTemplate> merged = latestVersionPerCode(
                repository.findByTenantIdAndIsDeletedFalseOrderByCodeAscVersionDesc(
                        ReportTenantScope.PLATFORM_TENANT_ID));
        if (effectiveTenant != ReportTenantScope.PLATFORM_TENANT_ID) {
            latestVersionPerCode(repository.findByTenantIdAndIsDeletedFalseOrderByCodeAscVersionDesc(
                            effectiveTenant))
                    .forEach(merged::put);
        }
        return new ArrayList<>(merged.values());
    }

    /** 列表已按 code 升序、version 降序，同 code 首条即为最高版本。 */
    private static Map<String, ReportTemplate> latestVersionPerCode(List<ReportTemplate> rows) {
        Map<String, ReportTemplate> map = new LinkedHashMap<>();
        for (ReportTemplate row : rows) {
            if (!StringUtils.hasText(row.getCode())) {
                continue;
            }
            map.putIfAbsent(row.getCode().trim(), row);
        }
        return map;
    }

    public Optional<ReportTemplate> findTenantDraft(String code, Long tenantId) {
        if (!StringUtils.hasText(code) || tenantId == null || tenantId == ReportTenantScope.PLATFORM_TENANT_ID) {
            return Optional.empty();
        }
        return repository.findFirstByTenantIdAndCodeAndStatusAndIsDeletedFalseOrderByVersionDesc(
                tenantId, code.trim(), STATUS_DRAFT);
    }

    public ReportTemplate requireDraftForEdit(Long templateId, Long tenantId) {
        ReportTemplate template = requireOwnedTemplate(templateId, tenantId);
        if (!STATUS_DRAFT.equals(template.getStatus())) {
            throw new BusinessException("RPT_TEMPLATE_NOT_DRAFT", "仅草稿可编辑");
        }
        return template;
    }

    /**
     * 设计器保存：院点草稿或已发布均可直接改 layout（已发布保存即生效）。
     */
    public ReportTemplate requireEditableForDesign(Long templateId, Long tenantId) {
        ReportTemplate template = requireOwnedTemplate(templateId, tenantId);
        if (isPlatformTemplate(template)) {
            throw new BusinessException("RPT_TEMPLATE_PLATFORM_READONLY", "出厂模板不可直接编辑，请复制为院点草稿");
        }
        if (!STATUS_DRAFT.equals(template.getStatus()) && !STATUS_PUBLISHED.equals(template.getStatus())) {
            throw new BusinessException("RPT_TEMPLATE_NOT_EDITABLE", "当前状态不可编辑");
        }
        return template;
    }

    private ReportTemplate requireOwnedTemplate(Long templateId, Long tenantId) {
        ReportTemplate template = repository.findByIdAndIsDeletedFalse(templateId)
                .orElseThrow(() -> new BusinessException("RPT_TEMPLATE_NOT_FOUND", "模板不存在"));
        long tplTenant = ReportTenantScope.entityTenant(template.getTenantId());
        long effectiveTenant = ReportTenantScope.effectiveTenant(tenantId);
        if (tplTenant != effectiveTenant) {
            throw new BusinessException("RPT_TEMPLATE_TENANT_MISMATCH", "无权编辑该租户模板");
        }
        return template;
    }

    private static boolean isPlatformTemplate(ReportTemplate template) {
        long tplTenant = ReportTenantScope.entityTenant(template.getTenantId());
        return tplTenant == ReportTenantScope.PLATFORM_TENANT_ID;
    }

    /**
     * 设计器加载：院点草稿优先，否则已发布（院点已发布可直接编辑）。
     */
    public ReportTemplate resolveForDesign(String code, Long tenantId) {
        Optional<ReportTemplate> draft = findTenantDraft(code, tenantId);
        if (draft.isPresent()) {
            return draft.get();
        }
        return findPublished(code, tenantId, null);
    }

    public boolean isEditableDraft(ReportTemplate template, Long tenantId) {
        if (!STATUS_DRAFT.equals(template.getStatus()) && !STATUS_PUBLISHED.equals(template.getStatus())) {
            return false;
        }
        long tplTenant = ReportTenantScope.entityTenant(template.getTenantId());
        long effectiveTenant = ReportTenantScope.effectiveTenant(tenantId);
        return tplTenant == effectiveTenant && tplTenant != ReportTenantScope.PLATFORM_TENANT_ID;
    }

    /**
     * 复制为院点新模板（新 code、可选 layoutMode / pipeline），初始为草稿 v1。
     */
    public ReportTemplate copyAsNewTemplate(
            String sourceCode,
            String newCode,
            String newName,
            String layoutJson,
            String pipeline,
            String datasetCode,
            Long tenantId) {
        if (tenantId == null || tenantId == ReportTenantScope.PLATFORM_TENANT_ID) {
            throw new BusinessException("RPT_TENANT_REQUIRED", "院点租户才能复制为新模板");
        }
        String trimmedSource = sourceCode.trim();
        String trimmedNew = newCode.trim();
        if (trimmedSource.equals(trimmedNew)) {
            throw new BusinessException("RPT_TEMPLATE_CODE_SAME", "新模板编码不能与源模板相同");
        }
        if (repository.existsByTenantIdAndCodeAndIsDeletedFalse(tenantId, trimmedNew)) {
            throw new BusinessException("RPT_TEMPLATE_CODE_EXISTS", "模板编码已存在: " + trimmedNew);
        }
        if (!StringUtils.hasText(layoutJson)) {
            throw new BusinessException("RPT_LAYOUT_EMPTY", "layoutJson 不能为空");
        }
        ReportTemplate source = resolveForDesign(trimmedSource, tenantId);
        ReportTemplate created = new ReportTemplate();
        created.setTenantId(tenantId);
        created.setCode(trimmedNew);
        created.setName(resolveCopyName(newName, source.getName()));
        created.setPipeline(resolveCopyPipeline(pipeline, source.getPipeline()));
        created.setDatasetCode(resolveCopyDatasetCode(datasetCode, source.getDatasetCode()));
        created.setLayoutJson(layoutJson.trim());
        created.setStatus(STATUS_DRAFT);
        created.setVersion(1);
        created.setOrganizationId(source.getOrganizationId());
        created.setCreatedAt(LocalDateTime.now());
        created.setUpdatedAt(LocalDateTime.now());
        created.setIsDeleted(false);
        return repository.save(created);
    }

    public ReportTemplate forkDraftFromPublished(String code, Long tenantId) {
        if (tenantId == null || tenantId == ReportTenantScope.PLATFORM_TENANT_ID) {
            throw new BusinessException("RPT_TENANT_REQUIRED", "院点租户才能创建草稿副本");
        }
        String trimmed = code.trim();
        Optional<ReportTemplate> existing = findTenantDraft(trimmed, tenantId);
        if (existing.isPresent()) {
            return existing.get();
        }
        ReportTemplate source = findPublished(trimmed, tenantId, null);
        int maxVersion = repository.findFirstByTenantIdAndCodeAndIsDeletedFalseOrderByVersionDesc(tenantId, trimmed)
                .map(ReportTemplate::getVersion)
                .orElse(0);
        int nextVersion = maxVersion + 1;
        ReportTemplate draft = new ReportTemplate();
        draft.setTenantId(tenantId);
        draft.setCode(source.getCode());
        draft.setName(source.getName());
        draft.setPipeline(source.getPipeline());
        draft.setDatasetCode(source.getDatasetCode());
        draft.setLayoutJson(source.getLayoutJson());
        draft.setStatus(STATUS_DRAFT);
        draft.setVersion(nextVersion);
        draft.setOrganizationId(source.getOrganizationId());
        draft.setCreatedAt(LocalDateTime.now());
        draft.setUpdatedAt(LocalDateTime.now());
        draft.setIsDeleted(false);
        return repository.save(draft);
    }

    public ReportTemplate saveDraftLayout(Long templateId, Long tenantId, String layoutJson, String pipeline) {
        ReportTemplate template = requireEditableForDesign(templateId, tenantId);
        if (!StringUtils.hasText(layoutJson)) {
            throw new BusinessException("RPT_LAYOUT_EMPTY", "layoutJson 不能为空");
        }
        template.setLayoutJson(layoutJson.trim());
        if (StringUtils.hasText(pipeline)) {
            template.setPipeline(pipeline.trim());
        }
        template.setUpdatedAt(LocalDateTime.now());
        return repository.save(template);
    }

    public ReportTemplate saveDraftLayout(Long templateId, Long tenantId, String layoutJson) {
        return saveDraftLayout(templateId, tenantId, layoutJson, null);
    }

    /**
     * 将院点草稿发布为已发布态（同 id 覆盖生效）。
     */
    public ReportTemplate publishDraft(Long templateId, Long tenantId) {
        ReportTemplate template = requireDraftForEdit(templateId, tenantId);
        template.setStatus(STATUS_PUBLISHED);
        template.setUpdatedAt(LocalDateTime.now());
        return repository.save(template);
    }

    /**
     * 预览/打印：inline layoutJson 优先，否则加载已发布模板 layout。
     */
    public String resolveLayoutJson(String templateCode, String inlineLayoutJson, Long tenantId) {
        if (StringUtils.hasText(inlineLayoutJson)) {
            return inlineLayoutJson.trim();
        }
        ReportTemplate template = findPublished(templateCode, tenantId, null);
        if (!StringUtils.hasText(template.getLayoutJson())) {
            throw new BusinessException("RPT_LAYOUT_EMPTY", "模板 layout_json 为空: " + templateCode);
        }
        return template.getLayoutJson();
    }

    private static String resolveCopyName(String newName, String sourceName) {
        if (StringUtils.hasText(newName)) {
            return newName.trim();
        }
        return sourceName + " 副本";
    }

    private static String resolveCopyPipeline(String pipeline, String sourcePipeline) {
        if (StringUtils.hasText(pipeline)) {
            return pipeline.trim();
        }
        return sourcePipeline;
    }

    private static String resolveCopyDatasetCode(String datasetCode, String sourceDatasetCode) {
        if (StringUtils.hasText(datasetCode)) {
            return datasetCode.trim();
        }
        return sourceDatasetCode;
    }
}
