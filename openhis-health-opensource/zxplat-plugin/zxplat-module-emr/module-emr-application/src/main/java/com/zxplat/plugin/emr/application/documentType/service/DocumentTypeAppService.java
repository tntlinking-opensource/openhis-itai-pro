/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.application.documentType.service;

import com.zxplat.framework.core.application.exception.EntityNotFoundException;
import com.zxplat.framework.core.application.exception.ValidationException;
import com.zxplat.framework.core.application.util.SpecificationUtils;
import com.zxplat.plugin.emr.application.documentType.response.DocumentTypeDto;
import com.zxplat.plugin.emr.application.documentType.request.CreateDocumentTypeRequest;
import com.zxplat.plugin.emr.application.documentType.request.DeleteDocumentTypeRequest;
import com.zxplat.plugin.emr.application.documentType.request.GetDocumentTypeRequest;
import com.zxplat.plugin.emr.application.documentType.request.QueryDocumentTypeRequest;
import com.zxplat.plugin.emr.application.documentType.request.UpdateDocumentTypeRequest;
import com.zxplat.plugin.emr.domain.document.entity.Document;
import com.zxplat.plugin.emr.domain.document.entity.DocumentType;
import com.zxplat.plugin.emr.domain.template.entity.Template;
import com.zxplat.plugin.emr.domain.document.repository.DocumentRepository;
import com.zxplat.plugin.emr.domain.document.repository.DocumentTypeRepository;
import com.zxplat.plugin.emr.domain.template.repository.TemplateRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 文书类型维护 → {@code POST /api/app/documentType/*}。
 * 管理编码、名称、默认模板及启用状态。
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

    /**
     * 文书类型列表 → {@code POST /api/app/documentType/getList}
     *
     * @param request 可选 enabled、keyword 过滤
     * @return 按 sortOrder 排序的类型列表
     */
    public List<DocumentTypeDto> getList(QueryDocumentTypeRequest request) {
        // Gate：request 为空时使用默认空条件，避免 NPE
        QueryDocumentTypeRequest req;
        if (request != null) {
            req = request;
        } else {
            req = new QueryDocumentTypeRequest();
        }
        // Pre：租户 + 软删 + enabled/keyword 条件下推 DB
        Specification<DocumentType> spec = SpecificationUtils.and(
                SpecificationUtils.<DocumentType>tenantFilter(),
                SpecificationUtils.<DocumentType>notDeleted(),
                SpecificationUtils.equal("enabled", req.getEnabled()),
                SpecificationUtils.keywordLike(req.getKeyword(), "code", "name")
        );
        // Cmd：按 sortOrder 排序查全表（类型数量有限）
        List<DocumentType> list = documentTypeRepository.findAll(
                spec,
                Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("id"))
        );
        // Asm：实体映射为 DTO（含默认模板名称）
        return list.stream().map(this::toDto).collect(Collectors.toList());
    }

    /**
     * 按 id 查询文书类型 → {@code POST /api/app/documentType/get}
     *
     * @param request 含类型 id
     * @return 类型详情（含默认模板名称）
     */
    public DocumentTypeDto get(@Valid GetDocumentTypeRequest request) {
        // Gate：主键必填
        if (request == null || request.getId() == null) {
            throw new ValidationException("文书类型 ID 不能为空");
        }
        // Cmd：按 id 加载
        DocumentType entity = documentTypeRepository.findById(request.getId())
                .orElseThrow(() -> new EntityNotFoundException("文书类型", request.getId()));
        // Asm：映射 DTO
        return toDto(entity);
    }

    /**
     * 新建文书类型 → {@code POST /api/app/documentType/create}
     *
     * @param request code、name 必填；可指定默认模板
     * @return 新建的类型
     * @throws ValidationException 编码重复或默认模板不匹配
     */
    public DocumentTypeDto create(@Valid CreateDocumentTypeRequest request) {
        // Gate：请求体非空；code/name 格式与唯一性校验
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

        // Pre：组装新实体并校验默认模板与类型一致
        DocumentType entity = new DocumentType();
        entity.setCode(code);
        entity.setName(name);
        if (request.getIsEnabled() != null) {
            entity.setEnabled(request.getIsEnabled());
        } else {
            entity.setEnabled(Boolean.TRUE);
        }
        entity.setSortOrder(request.getSortOrder());
        if (request.getCategoryCode() != null) {
            entity.setCategoryCode(normalizeCategoryCode(request.getCategoryCode()));
        }
        validateAndApplyDefaultTemplate(entity, request.getDefaultTemplateId());

        // Cmd：持久化
        // Asm：返回 DTO
        return toDto(documentTypeRepository.save(entity));
    }

    /**
     * 更新文书类型 → {@code POST /api/app/documentType/update}
     *
     * @param request 含 id；code 不可改
     * @return 更新后的类型
     */
    public DocumentTypeDto update(@Valid UpdateDocumentTypeRequest request) {
        // Gate：主键必填
        if (request == null || request.getId() == null) {
            throw new ValidationException("文书类型 ID 不能为空");
        }
        // Pre：加载现有类型
        DocumentType entity = documentTypeRepository.findById(request.getId())
                .orElseThrow(() -> new EntityNotFoundException("文书类型", request.getId()));

        // Cmd：按请求字段增量更新（code 不可改）
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
        if (request.getCategoryCode() != null) {
            entity.setCategoryCode(normalizeCategoryCode(request.getCategoryCode()));
        }
        if (Boolean.TRUE.equals(request.getClearDefaultTemplate())) {
            entity.setDefaultTemplateId(null);
        } else if (request.getDefaultTemplateId() != null) {
            validateAndApplyDefaultTemplate(entity, request.getDefaultTemplateId());
        }

        // Asm：保存并映射 DTO
        return toDto(documentTypeRepository.save(entity));
    }

    /**
     * 删除文书类型 → {@code POST /api/app/documentType/delete}
     *
     * @param request 含类型 id
     * @throws ValidationException 仍被模板或文书引用
     */
    public void delete(@Valid DeleteDocumentTypeRequest request) {
        // Gate：主键必填
        if (request == null || request.getId() == null) {
            throw new ValidationException("文书类型 ID 不能为空");
        }
        DocumentType entity = documentTypeRepository.findById(request.getId())
                .orElseThrow(() -> new EntityNotFoundException("文书类型", request.getId()));
        // Gate：仍被模板或文书引用时不允许删除，避免孤儿数据
        assertNotReferenced(entity.getCode());
        // Cmd：物理删除（软删由实体监听处理）
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
        if (code == null) {
            return "";
        }
        return code.trim();
    }

    private static String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        return name.trim();
    }

    private DocumentTypeDto toDto(DocumentType entity) {
        DocumentTypeDto dto = new DocumentTypeDto();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setName(entity.getName());
        dto.setDefaultTemplateId(entity.getDefaultTemplateId());
        dto.setIsEnabled(entity.getEnabled());
        dto.setSortOrder(entity.getSortOrder());
        dto.setCategoryCode(entity.getCategoryCode());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        if (entity.getDefaultTemplateId() != null) {
            templateRepository.findById(entity.getDefaultTemplateId())
                    .ifPresent(template -> dto.setDefaultTemplateName(template.getName()));
        }
        return dto;
    }

    private static String normalizeCategoryCode(String categoryCode) {
        if (categoryCode == null) {
            return null;
        }
        String normalized = categoryCode.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
