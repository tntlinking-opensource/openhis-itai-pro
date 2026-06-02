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

import com.openhis.whale.core.application.exception.EntityNotFoundException;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.core.application.util.SpecificationUtils;
import com.openhis.whale.emr.application.dto.DocumentTypeDto;
import com.openhis.whale.emr.application.request.CreateDocumentTypeRequest;
import com.openhis.whale.emr.application.request.DeleteDocumentTypeRequest;
import com.openhis.whale.emr.application.request.GetDocumentTypeRequest;
import com.openhis.whale.emr.application.request.QueryDocumentTypeRequest;
import com.openhis.whale.emr.application.request.UpdateDocumentTypeRequest;
import com.openhis.whale.emr.domain.entity.Document;
import com.openhis.whale.emr.domain.entity.DocumentType;
import com.openhis.whale.emr.domain.entity.Template;
import com.openhis.whale.emr.domain.repository.DocumentRepository;
import com.openhis.whale.emr.domain.repository.DocumentTypeRepository;
import com.openhis.whale.emr.domain.repository.TemplateRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 文书类型 AppService → /api/app/documentType/{methodName}
 */
@Service
@Validated
public class DocumentTypeAppService {

    private final DocumentTypeRepository documentTypeRepository;
    private final TemplateRepository templateRepository;
    private final DocumentRepository documentRepository;

    public DocumentTypeAppService(
            DocumentTypeRepository documentTypeRepository,
            TemplateRepository templateRepository,
            DocumentRepository documentRepository) {
        this.documentTypeRepository = documentTypeRepository;
        this.templateRepository = templateRepository;
        this.documentRepository = documentRepository;
    }

    public List<DocumentTypeDto> getList(QueryDocumentTypeRequest request) {
        QueryDocumentTypeRequest req = request != null ? request : new QueryDocumentTypeRequest();
        Specification<DocumentType> spec = SpecificationUtils.and(
                SpecificationUtils.<DocumentType>tenantFilter(),
                SpecificationUtils.<DocumentType>notDeleted(),
                SpecificationUtils.equal("enabled", req.getEnabled()),
                SpecificationUtils.keywordLike(req.getKeyword(), "code", "name")
        );
        List<DocumentType> list = documentTypeRepository.findAll(
                spec,
                Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("id"))
        );
        return list.stream().map(this::toDto).collect(Collectors.toList());
    }

    public DocumentTypeDto get(@Valid GetDocumentTypeRequest request) {
        if (request == null || request.getId() == null) {
            throw new ValidationException("文书类型 ID 不能为空");
        }
        DocumentType entity = documentTypeRepository.findById(request.getId())
                .orElseThrow(() -> new EntityNotFoundException("文书类型", request.getId()));
        return toDto(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public DocumentTypeDto create(@Valid CreateDocumentTypeRequest request) {
        if (request == null) {
            throw new ValidationException("请求不能为空");
        }
        String code = normalizeCode(request.getCode());
        String name = normalizeName(request.getName());
        validateCodeRequired(code);
        validateNameRequired(name);
        if (documentTypeRepository.existsByCode(code)) {
            throw new ValidationException("文书类型编码已存在: " + code);
        }

        DocumentType entity = new DocumentType();
        entity.setCode(code);
        entity.setName(name);
        entity.setEnabled(request.getIsEnabled() != null ? request.getIsEnabled() : Boolean.TRUE);
        entity.setSortOrder(request.getSortOrder());
        validateAndApplyDefaultTemplate(entity, request.getDefaultTemplateId());

        return toDto(documentTypeRepository.save(entity));
    }

    @Transactional(rollbackFor = Exception.class)
    public DocumentTypeDto update(@Valid UpdateDocumentTypeRequest request) {
        if (request == null || request.getId() == null) {
            throw new ValidationException("文书类型 ID 不能为空");
        }
        DocumentType entity = documentTypeRepository.findById(request.getId())
                .orElseThrow(() -> new EntityNotFoundException("文书类型", request.getId()));

        if (request.getName() != null) {
            String name = normalizeName(request.getName());
            validateNameRequired(name);
            entity.setName(name);
        }
        if (request.getIsEnabled() != null) {
            entity.setEnabled(request.getIsEnabled());
        }
        if (request.getSortOrder() != null) {
            entity.setSortOrder(request.getSortOrder());
        }
        if (Boolean.TRUE.equals(request.getClearDefaultTemplate())) {
            entity.setDefaultTemplateId(null);
        } else if (request.getDefaultTemplateId() != null) {
            validateAndApplyDefaultTemplate(entity, request.getDefaultTemplateId());
        }

        return toDto(documentTypeRepository.save(entity));
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(@Valid DeleteDocumentTypeRequest request) {
        if (request == null || request.getId() == null) {
            throw new ValidationException("文书类型 ID 不能为空");
        }
        DocumentType entity = documentTypeRepository.findById(request.getId())
                .orElseThrow(() -> new EntityNotFoundException("文书类型", request.getId()));
        assertNotReferenced(entity.getCode());
        documentTypeRepository.delete(entity);
    }

    private void validateAndApplyDefaultTemplate(DocumentType entity, Long templateId) {
        if (templateId == null) {
            entity.setDefaultTemplateId(null);
            return;
        }
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new ValidationException("默认模板不存在: " + templateId));
        if (!Boolean.TRUE.equals(template.getEnabled())) {
            throw new ValidationException("默认模板未启用");
        }
        if (!Objects.equals(template.getDocumentTypeCode(), entity.getCode())) {
            throw new ValidationException("默认模板的文书类型与当前类型不一致");
        }
        entity.setDefaultTemplateId(templateId);
    }

    private void assertNotReferenced(String code) {
        long templateCount = templateRepository.count(buildTemplateRefSpec(code));
        if (templateCount > 0) {
            throw new ValidationException("该文书类型已被 " + templateCount + " 个模板引用，无法删除");
        }
        long documentCount = documentRepository.count(buildDocumentRefSpec(code));
        if (documentCount > 0) {
            throw new ValidationException("该文书类型已被 " + documentCount + " 份文书引用，无法删除");
        }
    }

    private Specification<Template> buildTemplateRefSpec(String code) {
        return SpecificationUtils.and(
                SpecificationUtils.<Template>tenantFilter(),
                SpecificationUtils.notDeleted(),
                SpecificationUtils.equal("documentTypeCode", code)
        );
    }

    private Specification<Document> buildDocumentRefSpec(String code) {
        return SpecificationUtils.and(
                SpecificationUtils.<Document>tenantFilter(),
                SpecificationUtils.notDeleted(),
                SpecificationUtils.equal("documentTypeCode", code)
        );
    }

    private static void validateCodeRequired(String code) {
        if (code == null || code.isEmpty()) {
            throw new ValidationException("文书类型编码不能为空");
        }
        if (code.length() > 32) {
            throw new ValidationException("文书类型编码长度不能超过 32");
        }
    }

    private static void validateNameRequired(String name) {
        if (name == null || name.isEmpty()) {
            throw new ValidationException("文书类型名称不能为空");
        }
        if (name.length() > 64) {
            throw new ValidationException("文书类型名称长度不能超过 64");
        }
    }

    private static String normalizeCode(String code) {
        return code == null ? "" : code.trim();
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.trim();
    }

    private DocumentTypeDto toDto(DocumentType entity) {
        DocumentTypeDto dto = new DocumentTypeDto();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setName(entity.getName());
        dto.setDefaultTemplateId(entity.getDefaultTemplateId());
        dto.setIsEnabled(entity.getEnabled());
        dto.setSortOrder(entity.getSortOrder());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        if (entity.getDefaultTemplateId() != null) {
            templateRepository.findById(entity.getDefaultTemplateId())
                    .ifPresent(template -> dto.setDefaultTemplateName(template.getName()));
        }
        return dto;
    }
}
