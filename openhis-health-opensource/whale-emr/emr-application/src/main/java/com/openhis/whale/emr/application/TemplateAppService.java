/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.whale.emr.application;

import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.core.infrastructure.security.ICurrentUser;
import com.openhis.whale.emr.application.dto.DocumentTypeDto;
import com.openhis.whale.emr.application.dto.DocumentTypeItemDto;
import com.openhis.whale.emr.application.dto.TemplateDto;
import com.openhis.whale.emr.application.request.*;
import com.openhis.whale.emr.application.support.TemplateScopeValidator;
import com.openhis.whale.emr.domain.entity.Template;
import com.openhis.whale.emr.domain.enums.TemplateScopeLevel;
import com.openhis.whale.emr.domain.enums.TemplateStationKind;
import com.openhis.whale.emr.domain.repository.TemplateRepository;
import com.openhis.whale.emr.domain.service.DocumentManager;
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
 * 模板 AppService → /api/app/template/{methodName}
 */
@Service
@Validated
public class TemplateAppService {

    private static final Map<String, String> ENCOUNTER_CLASS_DISPLAY = Map.of(
            "AMB", "门诊",
            "IMP", "住院",
            "EMER", "急诊",
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

    public TemplatePageResponse getList(@Valid QueryTemplateRequest request) {
        return pageTemplates(request);
    }

    /**
     * 医生站模板树：按门诊/住院场景与当前用户可见范围返回（不含 content）。
     */
    public List<TemplateDto> listForStation(@Valid ListTemplatesForStationRequest request) {
        TemplateStationKind stationKind = TemplateStationKind.fromCode(request.getStationKind());
        if (stationKind == null) {
            throw new ValidationException("stationKind 无效，须为 OUTPATIENT 或 INPATIENT");
        }
        Long practitionerId = currentUser != null ? currentUser.getCurrentPractitionerId() : null;
        Long organizationId = currentUser != null ? currentUser.getCurrentOrganizationUnitId() : null;
        Specification<Template> spec = buildStationSpec(stationKind, practitionerId, organizationId, request);
        List<Template> templates = templateRepository.findAll(spec, Sort.by(Sort.Direction.ASC, "name"));
        return templates.stream()
                .sorted(Comparator.comparingInt(t -> scopeSortOrder(t.getScopeLevel())))
                .map(t -> toTemplateDto(t, false))
                .collect(Collectors.toList());
    }

    public List<DocumentTypeItemDto> getDocumentTypes(QueryDocumentTypeRequest request) {
        QueryDocumentTypeRequest req = request != null ? request : new QueryDocumentTypeRequest();
        if (req.getEnabled() == null) {
            req.setEnabled(true);
        }
        return documentTypeAppService.getList(req).stream()
                .map(this::toDocumentTypeItemDtoFromDto)
                .collect(Collectors.toList());
    }

    public TemplateDto getContent(@Valid GetTemplateContentRequest request) {
        if (request == null || !StringUtils.hasText(request.getTemplateId())) {
            return null;
        }
        GetTemplateRequest req = new GetTemplateRequest();
        req.setId(request.getTemplateId());
        return getTemplate(req);
    }

    public TemplateDto create(@Valid CreateTemplateRequest request) {
        return createTemplate(request);
    }

    public TemplateDto update(@Valid UpdateTemplateRequest request) {
        return updateTemplate(request);
    }

    public void delete(@Valid DeleteTemplateRequest request) {
        deleteTemplate(request);
    }

    public List<DocumentTypeItemDto> getDocumentTypeList() {
        QueryDocumentTypeRequest req = new QueryDocumentTypeRequest();
        req.setEnabled(true);
        return getDocumentTypes(req);
    }

    public TemplatePageResponse pageTemplates(@Valid QueryTemplateRequest request) {
        int pageNo = resolvePageNo(request.getPageNo(), request.getPageIndex());
        int size = request.getPageSize() != null && request.getPageSize() > 0 ? request.getPageSize() : 20;
        Specification<Template> spec = buildTemplateSpec(request);
        var templatePage = templateRepository.findAll(spec, PageRequest.of(pageNo - 1, size));
        TemplatePageResponse response = new TemplatePageResponse();
        response.setContent(templatePage.getContent().stream()
                .map(t -> toTemplateDto(t, true))
                .collect(Collectors.toList()));
        response.setTotalCount(templatePage.getTotalElements());
        return response;
    }

    public TemplateDto getTemplate(@Valid GetTemplateRequest request) {
        if (request == null || !StringUtils.hasText(request.getId())) {
            return null;
        }
        Long templateId = parseTemplateId(request.getId());
        return templateRepository.findByIdAndIsDeletedFalse(templateId)
                .map(t -> toTemplateDto(t, true))
                .orElse(null);
    }

    public TemplateDto createTemplate(@Valid CreateTemplateRequest request) {
        if (request.getCode() != null && !request.getCode().isEmpty()
                && templateRepository.findByCodeAndIsDeletedFalse(request.getCode()).isPresent()) {
            throw new IllegalArgumentException("模板编码已存在: " + request.getCode());
        }
        Template t = new Template();
        t.setName(request.getName());
        t.setCode(request.getCode());
        t.setDocumentTypeCode(request.getDocumentTypeCode());
        if (Boolean.TRUE.equals(request.getFromDoctorStation())) {
            applyScopeFieldsToTemplate(
                    t,
                    TemplateScopeLevel.PERSONAL.getCode(),
                    null,
                    resolveOwnerForCreate(request.getOwnerPractitionerId()),
                    request.getEncounterClassCode());
        } else {
            applyScopeFieldsToTemplate(
                    t,
                    resolveScopeLevelForCreate(request.getScopeLevel()),
                    request.getOrganizationId(),
                    resolveOwnerForCreate(request.getOwnerPractitionerId()),
                    request.getEncounterClassCode());
        }
        t.setEncounterTypeEnum(request.getEncounterTypeEnum());
        t.setContent(request.getContent());
        t.setEnabled(request.getEnabled() != null ? request.getEnabled() : true);
        t = templateRepository.save(t);
        return toTemplateDto(t, true);
    }

    public TemplateDto updateTemplate(@Valid UpdateTemplateRequest request) {
        Long templateId = parseTemplateId(request.getId());
        Template t = templateRepository.findByIdAndIsDeletedFalse(templateId)
                .orElseThrow(() -> new IllegalArgumentException("模板不存在: " + request.getId()));
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
            String scope = request.getScopeLevel() != null ? request.getScopeLevel() : t.getScopeLevel();
            Long orgId = request.getOrganizationId() != null ? request.getOrganizationId() : t.getOrganizationId();
            Long ownerId = request.getOwnerPractitionerId() != null
                    ? request.getOwnerPractitionerId()
                    : t.getOwnerPractitionerId();
            String encounterClass = request.getEncounterClassCode() != null
                    ? request.getEncounterClassCode()
                    : t.getEncounterClassCode();
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
        t = templateRepository.save(t);
        return toTemplateDto(t, true);
    }

    public void deleteTemplate(@Valid DeleteTemplateRequest request) {
        if (request == null || !StringUtils.hasText(request.getId())) {
            return;
        }
        Long templateId = parseTemplateId(request.getId());
        Template t = templateRepository.findByIdAndIsDeletedFalse(templateId)
                .orElseThrow(() -> new IllegalArgumentException("模板不存在: " + request.getId()));
        t.setIsDeleted(true);
        templateRepository.save(t);
    }

    public void setTemplateEnabled(@Valid SetTemplateEnabledRequest request) {
        Long templateId = parseTemplateId(request.getTemplateId());
        Template t = templateRepository.findByIdAndIsDeletedFalse(templateId)
                .orElseThrow(() -> new IllegalArgumentException("模板不存在: " + request.getTemplateId()));
        t.setEnabled(Boolean.TRUE.equals(request.getEnabled()));
        templateRepository.save(t);
    }

    public TemplateDto copyTemplate(@Valid CopyTemplateRequest request) {
        Long sourceId = parseTemplateId(request.getSourceTemplateId());
        Template source = templateRepository.findByIdAndIsDeletedFalse(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("源模板不存在: " + request.getSourceTemplateId()));
        if (request.getNewCode() != null && !request.getNewCode().isEmpty()
                && templateRepository.findByCodeAndIsDeletedFalse(request.getNewCode()).isPresent()) {
            throw new IllegalArgumentException("新模板编码已存在: " + request.getNewCode());
        }
        Template t = new Template();
        t.setName(request.getNewName() != null ? request.getNewName() : source.getName() + "（副本）");
        t.setCode(request.getNewCode() != null ? request.getNewCode() : source.getCode() + "_copy");
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
        t = templateRepository.save(t);
        return toTemplateDto(t, true);
    }

    private String resolveScopeLevelForCreate(String raw) {
        String normalized = TemplateScopeValidator.normalizeScopeLevel(raw);
        return normalized != null ? normalized : TemplateScopeLevel.HOSPITAL.getCode();
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

    private static int resolvePageNo(Integer pageNo, Integer pageIndex) {
        if (pageNo != null && pageNo > 0) {
            return pageNo;
        }
        if (pageIndex != null && pageIndex > 0) {
            return pageIndex;
        }
        return 1;
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
        documentManager.getDocumentTypeByCode(t.getDocumentTypeCode())
                .ifPresent(dt -> dto.setDocumentTypeName(dt.getName()));
        dto.setScopeLevel(t.getScopeLevel());
        TemplateScopeLevel scopeLevel = TemplateScopeLevel.fromCode(t.getScopeLevel());
        dto.setScopeLevelName(scopeLevel != null ? scopeLevel.getDisplay() : t.getScopeLevel());
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
