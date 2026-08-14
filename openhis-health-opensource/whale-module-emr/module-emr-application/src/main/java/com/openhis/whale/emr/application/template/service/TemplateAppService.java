/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.application.template.service;

import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.core.infrastructure.security.ICurrentUser;
import com.openhis.whale.emr.application.documentType.response.DocumentTypeDto;
import com.openhis.whale.emr.application.documentType.response.DocumentTypeItemDto;
import com.openhis.whale.emr.application.template.response.TemplateDto;
import com.openhis.whale.emr.application.template.response.TemplatePageResponse;
import com.openhis.whale.emr.application.template.request.CopyTemplateRequest;
import com.openhis.whale.emr.application.template.request.CreateTemplateRequest;
import com.openhis.whale.emr.application.template.request.DeleteTemplateRequest;
import com.openhis.whale.emr.application.template.request.GetTemplateContentRequest;
import com.openhis.whale.emr.application.template.request.GetTemplateRequest;
import com.openhis.whale.emr.application.template.request.ListTemplatesForStationRequest;
import com.openhis.whale.emr.application.template.request.QueryTemplateRequest;
import com.openhis.whale.emr.application.template.request.SetTemplateEnabledRequest;
import com.openhis.whale.emr.application.template.request.UpdateTemplateRequest;
import com.openhis.whale.emr.application.documentType.request.QueryDocumentTypeRequest;
import com.openhis.whale.emr.application.documentType.service.DocumentTypeAppService;
import com.openhis.whale.emr.application.support.EmrQuerySupport;
import com.openhis.whale.emr.application.support.TemplateScopeValidator;
import com.openhis.whale.emr.domain.template.entity.Template;
import com.openhis.whale.emr.domain.enums.TemplateScopeLevel;
import com.openhis.whale.emr.domain.enums.TemplateStationKind;
import com.openhis.whale.emr.domain.template.repository.TemplateRepository;
import com.openhis.whale.emr.domain.document.service.DocumentManager;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 病历模板管理 → {@code POST /api/app/template/*}。
 * 维护模板内容、使用范围（个人/科室/全院）及医生站可见性。
 */
@Service
@Validated
public class TemplateAppService {

    private static final Map<String, String> ENCOUNTER_CLASS_DISPLAY = Map.of(
            "AMB", "门诊",
            "IMP", "住院",
            "OBSENC", "留观");

    private final TemplateRepository templateRepository;
    private final DocumentManager documentManager;
    private final DocumentTypeAppService documentTypeAppService;
    private final ICurrentUser currentUser;

    public TemplateAppService(
            TemplateRepository templateRepository,
            DocumentManager documentManager,
            DocumentTypeAppService documentTypeAppService,
            @Autowired(required = false) ICurrentUser currentUser) {
        this.templateRepository = templateRepository;
        this.documentManager = documentManager;
        this.documentTypeAppService = documentTypeAppService;
        this.currentUser = currentUser;
    }

    /**
     * 模板分页列表 → {@code POST /api/app/template/getList}
     *
     * @param request 分页与过滤条件
     * @return 模板分页（默认不含 content）
     */
    public TemplatePageResponse getList(@Valid QueryTemplateRequest request) {
        return pageTemplates(request);
    }

    /**
     * 医生站模板树：按门诊/住院场景与当前用户可见范围返回（不含 content）。
     */
    public List<TemplateDto> listForStation(@Valid ListTemplatesForStationRequest request) {
        // Gate：stationKind 须为门诊/住院枚举，否则无法映射就诊类别过滤
        TemplateStationKind stationKind = TemplateStationKind.fromCode(request.getStationKind());
        if (stationKind == null) {
            throw new ValidationException("stationKind 无效，须为 OUTPATIENT 或 INPATIENT");
        }
        // Pre：读取当前用户科室/个人 id，用于可见范围过滤
        Long practitionerId = null;
        Long organizationId = null;
        if (currentUser != null) {
            practitionerId = currentUser.getCurrentPractitionerId();
            organizationId = currentUser.getCurrentOrganizationUnitId();
        }
        // Cmd：按医生站可见性 Specification 查启用模板
        Specification<Template> spec = buildStationSpec(stationKind, practitionerId, organizationId, request);
        List<Template> templates = templateRepository.findAll(spec, Sort.by(Sort.Direction.ASC, "name"));
        // Asm：按 scope 排序并映射 DTO（不含 content）
        return templates.stream()
                .sorted(Comparator.comparingInt(t -> scopeSortOrder(t.getScopeLevel())))
                .map(t -> toTemplateDto(t, false))
                .collect(Collectors.toList());
    }

    /**
     * 模板维护页可选文书类型 → {@code POST /api/app/template/getDocumentTypes}
     *
     * @param request 透传至 documentType/getList
     * @return 精简 code/name 列表
     */
    public List<DocumentTypeItemDto> getDocumentTypes(QueryDocumentTypeRequest request) {
        QueryDocumentTypeRequest req;
        if (request != null) {
            req = request;
        } else {
            req = new QueryDocumentTypeRequest();
        }
        if (req.getEnabled() == null) {
            req.setEnabled(true);
        }
        return documentTypeAppService.getList(req).stream()
                .map(this::toDocumentTypeItemDtoFromDto)
                .collect(Collectors.toList());
    }

    /**
     * 获取模板正文 → {@code POST /api/app/template/getContent}
     *
     * @param request 含 templateId
     * @return 含 content 的模板 DTO，id 无效时返回 null
     */
    public TemplateDto getContent(@Valid GetTemplateContentRequest request) {
        if (request == null || !StringUtils.hasText(request.getTemplateId())) {
            return null;
        }
        GetTemplateRequest req = new GetTemplateRequest();
        req.setId(request.getTemplateId());
        return getTemplate(req);
    }

    /** {@link #createTemplate} 的 API 别名。 */
    public TemplateDto create(@Valid CreateTemplateRequest request) {
        return createTemplate(request);
    }

    /** {@link #updateTemplate} 的 API 别名。 */
    public TemplateDto update(@Valid UpdateTemplateRequest request) {
        return updateTemplate(request);
    }

    /** {@link #deleteTemplate} 的 API 别名。 */
    public void delete(@Valid DeleteTemplateRequest request) {
        deleteTemplate(request);
    }

    /**
     * 启用状态的文书类型精简列表 → {@code POST /api/app/template/getDocumentTypeList}
     *
     * @return code/name 列表
     */
    public List<DocumentTypeItemDto> getDocumentTypeList() {
        QueryDocumentTypeRequest req = new QueryDocumentTypeRequest();
        req.setEnabled(true);
        return getDocumentTypes(req);
    }

    /**
     * 模板分页（含 content）→ 内部及 {@link #getList} 共用。
     *
     * @param request 分页与 scope、文书类型等过滤
     * @return 分页结果
     */
    public TemplatePageResponse pageTemplates(@Valid QueryTemplateRequest request) {
        // Gate：归一化分页窗口
        int pageNo = EmrQuerySupport.resolvePageNo(request.getPageNo(), request.getPageIndex());
        int size = EmrQuerySupport.resolvePageSize(request.getPageSize());
        // Pre：组装过滤 Specification
        Specification<Template> spec = buildTemplateSpec(request);
        // Cmd：分页查模板
        var templatePage = templateRepository.findAll(spec, PageRequest.of(pageNo - 1, size));
        // Asm：映射分页响应（列表不含 content，避免医生站树回退 getList 时拖垮接口）
        TemplatePageResponse response = new TemplatePageResponse();
        response.setContent(templatePage.getContent().stream()
                .map(t -> toTemplateDto(t, false))
                .collect(Collectors.toList()));
        response.setTotalCount(templatePage.getTotalElements());
        return response;
    }

    /**
     * 按 id 查询模板（含 content）→ {@code POST /api/app/template/getTemplate}
     *
     * @param request 含模板 id 字符串
     * @return 模板详情，不存在时返回 null
     */
    public TemplateDto getTemplate(@Valid GetTemplateRequest request) {
        // Gate：id 为空直接返回 null
        if (request == null || !StringUtils.hasText(request.getId())) {
            return null;
        }
        // Pre：解析雪花 id 字符串
        Long templateId = parseTemplateId(request.getId());
        // Cmd + Asm：查未软删模板并映射 DTO
        return templateRepository.findByIdAndIsDeletedFalse(templateId)
                .map(t -> toTemplateDto(t, true))
                .orElse(null);
    }

    /**
     * 新建模板 → {@code POST /api/app/template/createTemplate}
     *
     * @param request 名称、文书类型、范围与 content
     * @return 持久化后的模板（含 content）
     */
    public TemplateDto createTemplate(@Valid CreateTemplateRequest request) {
        // Gate：编码唯一（若提供）
        if (request.getCode() != null && !request.getCode().isEmpty()
                && templateRepository.findByCodeAndIsDeletedFalse(request.getCode()).isPresent()) {
            throw new IllegalArgumentException("模板编码已存在: " + request.getCode());
        }
        // Pre：组装实体；医生站仅允许个人/科室，所有者与科室取当前登录上下文（避免前端雪花精度问题）
        Template t = new Template();
        t.setName(request.getName());
        t.setCode(request.getCode());
        t.setDocumentTypeCode(request.getDocumentTypeCode());
        if (Boolean.TRUE.equals(request.getFromDoctorStation())) {
            applyDoctorStationScopeOnCreate(t, request);
        } else {
            applyScopeFieldsToTemplate(
                    t,
                    resolveScopeLevelForCreate(request.getScopeLevel()),
                    parseOptionalLongId(request.getOrganizationId(), "organizationId"),
                    resolveOwnerForCreate(parseOptionalLongId(request.getOwnerPractitionerId(), "ownerPractitionerId")),
                    request.getEncounterClassCode());
        }
        t.setEncounterTypeEnum(request.getEncounterTypeEnum());
        t.setContent(request.getContent());
        if (request.getEnabled() != null) {
            t.setEnabled(request.getEnabled());
        } else {
            t.setEnabled(true);
        }
        // Cmd：持久化
        t = templateRepository.save(t);
        // Asm：返回含 content 的 DTO
        return toTemplateDto(t, true);
    }

    /**
     * 更新模板 → {@code POST /api/app/template/updateTemplate}
     *
     * @param request 含 id 与待更新字段
     * @return 更新后的模板
     */
    public TemplateDto updateTemplate(@Valid UpdateTemplateRequest request) {
        // Pre：加载现有模板
        Long templateId = parseTemplateId(request.getId());
        Template t = templateRepository.findByIdAndIsDeletedFalse(templateId)
                .orElseThrow(() -> new IllegalArgumentException("模板不存在: " + request.getId()));
        // Cmd：增量更新各字段；变更 scope 时重新校验范围规则
        if (request.getName() != null) {
            t.setName(request.getName());
        }
        if (request.getCode() != null) {
            Optional<Template> existing = templateRepository.findByCodeAndIsDeletedFalse(request.getCode());
            if (existing.isPresent() && !existing.get().getId().equals(templateId)) {
                throw new IllegalArgumentException("模板编码已存在: " + request.getCode());
            }
            t.setCode(request.getCode());
        }
        if (request.getDocumentTypeCode() != null) {
            t.setDocumentTypeCode(request.getDocumentTypeCode());
        }
        if (request.getScopeLevel() != null
                || request.getEncounterClassCode() != null
                || request.getOrganizationId() != null
                || request.getOwnerPractitionerId() != null) {
            String scope;
            if (request.getScopeLevel() != null) {
                scope = request.getScopeLevel();
            } else {
                scope = t.getScopeLevel();
            }
            Long orgId;
            if (request.getOrganizationId() != null) {
                orgId = parseOptionalLongId(request.getOrganizationId(), "organizationId");
            } else {
                orgId = t.getOrganizationId();
            }
            Long ownerId;
            if (request.getOwnerPractitionerId() != null) {
                ownerId = parseOptionalLongId(request.getOwnerPractitionerId(), "ownerPractitionerId");
            } else {
                ownerId = t.getOwnerPractitionerId();
            }
            String encounterClass;
            if (request.getEncounterClassCode() != null) {
                encounterClass = request.getEncounterClassCode();
            } else {
                encounterClass = t.getEncounterClassCode();
            }
            applyScopeFieldsToTemplate(t, scope, orgId, ownerId, encounterClass);
        }
        if (request.getEncounterTypeEnum() != null) {
            t.setEncounterTypeEnum(request.getEncounterTypeEnum());
        }
        if (request.getContent() != null) {
            t.setContent(request.getContent());
        }
        if (request.getEnabled() != null) {
            t.setEnabled(request.getEnabled());
        }
        // Asm：保存并返回 DTO
        t = templateRepository.save(t);
        return toTemplateDto(t, true);
    }

    /**
     * 软删除模板 → {@code POST /api/app/template/deleteTemplate}
     *
     * @param request 含模板 id
     */
    public void deleteTemplate(@Valid DeleteTemplateRequest request) {
        // Gate：id 为空则 noop
        if (request == null || !StringUtils.hasText(request.getId())) {
            return;
        }
        // Cmd：软删模板
        Long templateId = parseTemplateId(request.getId());
        Template t = templateRepository.findByIdAndIsDeletedFalse(templateId)
                .orElseThrow(() -> new IllegalArgumentException("模板不存在: " + request.getId()));
        t.setIsDeleted(true);
        templateRepository.save(t);
    }

    /**
     * 启用/停用模板 → {@code POST /api/app/template/setTemplateEnabled}
     *
     * @param request 含 templateId 与 enabled
     */
    public void setTemplateEnabled(@Valid SetTemplateEnabledRequest request) {
        // Cmd：切换启用标志
        Long templateId = parseTemplateId(request.getTemplateId());
        Template t = templateRepository.findByIdAndIsDeletedFalse(templateId)
                .orElseThrow(() -> new IllegalArgumentException("模板不存在: " + request.getTemplateId()));
        t.setEnabled(Boolean.TRUE.equals(request.getEnabled()));
        templateRepository.save(t);
    }

    /**
     * 复制模板 → {@code POST /api/app/template/copyTemplate}
     *
     * @param request 源模板 id；可选新名称与编码
     * @return 副本模板
     */
    public TemplateDto copyTemplate(@Valid CopyTemplateRequest request) {
        // Pre：加载源模板
        Long sourceId = parseTemplateId(request.getSourceTemplateId());
        Template source = templateRepository.findByIdAndIsDeletedFalse(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("源模板不存在: " + request.getSourceTemplateId()));
        // Gate：新编码唯一（若提供）
        if (request.getNewCode() != null && !request.getNewCode().isEmpty()
                && templateRepository.findByCodeAndIsDeletedFalse(request.getNewCode()).isPresent()) {
            throw new IllegalArgumentException("新模板编码已存在: " + request.getNewCode());
        }
        Template t = new Template();
        if (request.getNewName() != null) {
            t.setName(request.getNewName());
        } else {
            t.setName(source.getName() + "（副本）");
        }
        if (request.getNewCode() != null) {
            t.setCode(request.getNewCode());
        } else {
            t.setCode(source.getCode() + "_copy");
        }
        t.setDocumentTypeCode(source.getDocumentTypeCode());
        applyScopeFieldsToTemplate(
                t,
                source.getScopeLevel(),
                source.getOrganizationId(),
                source.getOwnerPractitionerId(),
                source.getEncounterClassCode());
        t.setEncounterTypeEnum(source.getEncounterTypeEnum());
        t.setContent(source.getContent());
        t.setEnabled(source.getEnabled());
        // Cmd：保存副本
        t = templateRepository.save(t);
        // Asm：返回新模板 DTO
        return toTemplateDto(t, true);
    }

    private String resolveScopeLevelForCreate(String raw) {
        String normalized = TemplateScopeValidator.normalizeScopeLevel(raw);
        if (normalized != null) {
            return normalized;
        }
        return TemplateScopeLevel.HOSPITAL.getCode();
    }

    /**
     * 医生站「存为模版」：仅 PERSONAL / DEPARTMENT；
     * 个人所有者与科室 id <b>一律</b>取当前登录会话，忽略请求体中的雪花 id
     *（避免前端精度丢失或选了非当前登录科室，导致同科医生 listForStation 看不见）。
     */
    private void applyDoctorStationScopeOnCreate(Template t, CreateTemplateRequest request) {
        String scope = TemplateScopeValidator.normalizeScopeLevel(request.getScopeLevel());
        if (scope == null) {
            scope = TemplateScopeLevel.PERSONAL.getCode();
        }
        if (TemplateScopeLevel.PERSONAL.getCode().equals(scope)) {
            Long ownerId = currentUser != null ? currentUser.getCurrentPractitionerId() : null;
            if (ownerId == null) {
                throw new ValidationException("当前账号未关联医务人员，无法保存个人模板");
            }
            applyScopeFieldsToTemplate(
                    t,
                    TemplateScopeLevel.PERSONAL.getCode(),
                    null,
                    ownerId,
                    request.getEncounterClassCode());
            return;
        }
        if (TemplateScopeLevel.DEPARTMENT.getCode().equals(scope)) {
            // 与 listForStation 可见性过滤同源：会话科室（HIS adm_organization.id）
            Long orgId = currentUser != null ? currentUser.getCurrentOrganizationUnitId() : null;
            if (orgId == null) {
                throw new ValidationException("当前账号未选择登录科室，无法保存科室模板");
            }
            applyScopeFieldsToTemplate(
                    t,
                    TemplateScopeLevel.DEPARTMENT.getCode(),
                    orgId,
                    null,
                    request.getEncounterClassCode());
            return;
        }
        throw new ValidationException("医生站仅支持另存为个人模板或科室模板");
    }

    private Long resolveOwnerForCreate(Long requestedOwner) {
        if (requestedOwner != null) {
            return requestedOwner;
        }
        if (currentUser != null && currentUser.getCurrentPractitionerId() != null) {
            return currentUser.getCurrentPractitionerId();
        }
        return null;
    }

    private void applyScopeFieldsToTemplate(
            Template t,
            String scopeLevelRaw,
            Long organizationId,
            Long ownerPractitionerId,
            String encounterClassCode) {
        String scope = TemplateScopeValidator.normalizeScopeLevel(scopeLevelRaw);
        if (scope == null) {
            scope = TemplateScopeLevel.HOSPITAL.getCode();
        }
        Long orgId = organizationId;
        Long ownerId = ownerPractitionerId;
        if (TemplateScopeLevel.HOSPITAL.getCode().equals(scope)) {
            orgId = null;
            ownerId = null;
        } else if (TemplateScopeLevel.DEPARTMENT.getCode().equals(scope)) {
            ownerId = null;
        } else if (TemplateScopeLevel.PERSONAL.getCode().equals(scope)) {
            orgId = null;
            if (ownerId == null && currentUser != null) {
                ownerId = currentUser.getCurrentPractitionerId();
            }
        }
        String encounterCode = TemplateScopeValidator.normalizeEncounterClassCode(encounterClassCode);
        TemplateScopeValidator.validateForSave(scope, orgId, ownerId, encounterCode);
        t.setScopeLevel(scope);
        t.setOrganizationId(orgId);
        t.setOwnerPractitionerId(ownerId);
        t.setEncounterClassCode(encounterCode);
    }

    private static Long parseTemplateId(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new ValidationException("模板 ID 不能为空");
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            throw new ValidationException("模板 ID 格式无效: " + raw);
        }
    }

    /** 可选雪花 ID：空则 null；非法则校验失败 */
    private static Long parseOptionalLongId(String raw, String fieldName) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            throw new ValidationException(fieldName + " 格式无效: " + raw);
        }
    }

    private static int scopeSortOrder(String scopeLevel) {
        if (TemplateScopeLevel.PERSONAL.getCode().equals(scopeLevel)) {
            return 0;
        }
        if (TemplateScopeLevel.DEPARTMENT.getCode().equals(scopeLevel)) {
            return 1;
        }
        if (TemplateScopeLevel.HOSPITAL.getCode().equals(scopeLevel)) {
            return 2;
        }
        return 3;
    }

    private Specification<Template> buildStationSpec(
            TemplateStationKind stationKind,
            Long practitionerId,
            Long organizationId,
            ListTemplatesForStationRequest request) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("isDeleted"), false));
            predicates.add(cb.equal(root.get("encounterClassCode"), stationKind.getEncounterClassCode()));

            Boolean enabled = request.getEnabled();
            predicates.add(cb.equal(root.get("enabled"), enabled == null || enabled));

            if (StringUtils.hasText(request.getDocumentTypeCode())) {
                predicates.add(cb.equal(root.get("documentTypeCode"), request.getDocumentTypeCode().trim()));
            }

            List<Predicate> visibility = new ArrayList<>();
            visibility.add(cb.equal(root.get("scopeLevel"), TemplateScopeLevel.HOSPITAL.getCode()));
            if (organizationId != null) {
                visibility.add(cb.and(
                        cb.equal(root.get("scopeLevel"), TemplateScopeLevel.DEPARTMENT.getCode()),
                        cb.equal(root.get("organizationId"), organizationId)));
            }
            if (practitionerId != null) {
                visibility.add(cb.and(
                        cb.equal(root.get("scopeLevel"), TemplateScopeLevel.PERSONAL.getCode()),
                        cb.equal(root.get("ownerPractitionerId"), practitionerId)));
            }
            predicates.add(cb.or(visibility.toArray(new Predicate[0])));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Specification<Template> buildTemplateSpec(QueryTemplateRequest request) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("isDeleted"), false));
            if (request.getDocumentTypeCode() != null && !request.getDocumentTypeCode().isEmpty()) {
                predicates.add(cb.equal(root.get("documentTypeCode"), request.getDocumentTypeCode()));
            }
            if (StringUtils.hasText(request.getScopeLevel())) {
                predicates.add(cb.equal(
                        root.get("scopeLevel"),
                        TemplateScopeValidator.normalizeScopeLevel(request.getScopeLevel())));
            }
            if (StringUtils.hasText(request.getEncounterClassCode())) {
                predicates.add(cb.equal(
                        root.get("encounterClassCode"),
                        TemplateScopeValidator.normalizeEncounterClassCode(request.getEncounterClassCode())));
            }
            if (request.getOrganizationId() != null) {
                predicates.add(cb.equal(root.get("organizationId"), request.getOrganizationId()));
            }
            Boolean enabled = request.getEnabled();
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            String key = request.getSearchKey();
            if (key == null || key.isEmpty()) {
                key = request.getKeyword();
            }
            if (key != null && !key.isEmpty()) {
                String pattern = "%" + key.trim() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("name"), pattern),
                        cb.like(root.get("code"), pattern)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private TemplateDto toTemplateDto(Template t, boolean includeContent) {
        TemplateDto dto = new TemplateDto();
        dto.setId(t.getId());
        dto.setName(t.getName());
        dto.setCode(t.getCode());
        dto.setDocumentTypeCode(t.getDocumentTypeCode());
        try {
            documentManager.getDocumentTypeByCode(t.getDocumentTypeCode())
                    .ifPresent(dt -> dto.setDocumentTypeName(dt.getName()));
        } catch (RuntimeException ignored) {
            // 分类列迁移未落地等场景：列表仍可用，仅缺少类型中文名
        }
        dto.setScopeLevel(t.getScopeLevel());
        TemplateScopeLevel scopeLevel = TemplateScopeLevel.fromCode(t.getScopeLevel());
        if (scopeLevel != null) {
            dto.setScopeLevelName(scopeLevel.getDisplay());
        } else {
            dto.setScopeLevelName(t.getScopeLevel());
        }
        dto.setOrganizationId(t.getOrganizationId());
        dto.setOwnerPractitionerId(t.getOwnerPractitionerId());
        dto.setEncounterClassCode(t.getEncounterClassCode());
        dto.setEncounterClassName(resolveEncounterClassDisplay(t.getEncounterClassCode()));
        dto.setEncounterClassEnum(t.getEncounterClassEnum());
        dto.setEncounterTypeEnum(t.getEncounterTypeEnum());
        if (includeContent) {
            dto.setContent(t.getContent());
        }
        dto.setEnabled(t.getEnabled());
        dto.setIsEnabled(t.getEnabled());
        dto.setCreatedAt(t.getCreatedAt());
        dto.setUpdatedAt(t.getUpdatedAt());
        dto.setCreatedBy(t.getCreatedBy());
        dto.setUpdatedBy(t.getUpdatedBy());
        return dto;
    }

    private static String resolveEncounterClassDisplay(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        return ENCOUNTER_CLASS_DISPLAY.getOrDefault(code.trim().toUpperCase(), code);
    }

    private DocumentTypeItemDto toDocumentTypeItemDtoFromDto(DocumentTypeDto dto) {
        DocumentTypeItemDto item = new DocumentTypeItemDto();
        item.setCode(dto.getCode());
        item.setName(dto.getName());
        return item;
    }
}
