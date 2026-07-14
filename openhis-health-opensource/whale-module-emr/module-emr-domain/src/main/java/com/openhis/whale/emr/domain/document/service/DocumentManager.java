/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.domain.document.service;

import com.openhis.whale.emr.domain.document.entity.Document;
import com.openhis.whale.emr.domain.document.entity.DocumentType;
import com.openhis.whale.emr.domain.document.entity.DocumentVersion;
import com.openhis.whale.emr.domain.template.entity.Template;
import com.openhis.whale.emr.domain.document.repository.DocumentRepository;
import com.openhis.whale.emr.domain.document.repository.DocumentTypeRepository;
import com.openhis.whale.emr.domain.document.repository.DocumentVersionRepository;
import com.openhis.whale.emr.domain.template.repository.TemplateRepository;
import com.openhis.whale.core.application.exception.EntityNotFoundException;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.core.application.util.SpecificationUtils;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 文书领域服务。负责文书的创建、保存、版本、状态变更及列表查询。
 */
@Service
@RequiredArgsConstructor
public class DocumentManager {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_SUBMITTED = "SUBMITTED";
    public static final String STATUS_LOCKED = "LOCKED";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    /** 首次病程模板编码，同就诊仅允许一份 */
    public static final String TEMPLATE_CODE_FIRST_PROGRESS = "PN_FIRST";

    private static final DateTimeFormatter TITLE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final TemplateRepository templateRepository;
    private final DocumentTypeRepository documentTypeRepository;

    public Optional<Document> findById(Long id) {
        return documentRepository.findByIdAndIsDeletedFalse(id);
    }

    /**
     * 按患者、就诊、文书类型查询最新一条（用于“打开文书”）
     */
    public Optional<Document> findLatestByPatientAndEncounterAndType(String patientId, String encounterId, String documentTypeCode) {
        return documentRepository.findFirstByPatientIdAndEncounterIdAndDocumentTypeCodeAndIsDeletedFalseOrderByCreatedAtDesc(
                patientId, encounterId, documentTypeCode);
    }

    /**
     * 分页列表：按患者、就诊、文书类型、状态筛选
     */
    public Page<Document> findPage(String patientId, String encounterId, String documentTypeCode, String status, Pageable pageable) {
        Specification<Document> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("isDeleted"), false));
            if (patientId != null && !patientId.isEmpty()) {
                predicates.add(cb.equal(root.get("patientId"), patientId));
            }
            if (encounterId != null && !encounterId.isEmpty()) {
                predicates.add(cb.equal(root.get("encounterId"), encounterId));
            }
            if (documentTypeCode != null && !documentTypeCode.isEmpty()) {
                predicates.add(cb.equal(root.get("documentTypeCode"), documentTypeCode));
            }
            if (status != null && !status.isEmpty()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return documentRepository.findAll(spec, pageable);
    }

    /**
     * 创建文书。若传 templateId 则用该模板 content 初始化，否则用文书类型默认模板或空。
     */
    @Transactional(rollbackFor = Exception.class)
    public Document create(String patientId, String encounterId, String documentTypeCode, Long templateId) {
        Document doc = new Document();
        doc.setPatientId(patientId);
        doc.setEncounterId(encounterId);
        doc.setDocumentTypeCode(documentTypeCode);
        doc.setStatus(STATUS_DRAFT);
        String initialContent = resolveInitialContent(documentTypeCode, templateId);
        doc.setContent(initialContent);
        if (templateId != null) {
            Template template = templateRepository.findByIdAndIsDeletedFalse(templateId).orElse(null);
            if (template != null) {
                doc.setSourceTemplateId(templateId);
                String name;
                if (StringUtils.hasText(template.getName())) {
                    name = template.getName().trim();
                } else {
                    name = "文书";
                }
                doc.setTitle(name + "-" + LocalDateTime.now().format(TITLE_TIME_FORMAT));
                if (TEMPLATE_CODE_FIRST_PROGRESS.equals(template.getCode())
                        && StringUtils.hasText(encounterId)
                        && documentRepository.existsByEncounterIdAndSourceTemplateIdAndIsDeletedFalse(
                                encounterId, templateId)) {
                    throw new ValidationException("该就诊已存在首次病程记录");
                }
            }
        }
        return documentRepository.save(doc);
    }

    /**
     * 保存文书内容；可选同时生成版本快照。
     */
    @Transactional(rollbackFor = Exception.class)
    public Document saveContent(Long documentId, String content, boolean saveVersion) {
        Document doc = documentRepository.findByIdAndIsDeletedFalse(documentId)
                .orElseThrow(() -> new EntityNotFoundException("文书", documentId));
        if (STATUS_LOCKED.equals(doc.getStatus()) || STATUS_ARCHIVED.equals(doc.getStatus())) {
            throw new ValidationException("文书已锁定或归档，不可修改");
        }
        doc.setContent(content);
        doc = documentRepository.save(doc);
        if (saveVersion) {
            createVersionSnapshot(doc);
        }
        return doc;
    }

    /**
     * 为当前文书生成一条版本快照，版本号递增。
     */
    @Transactional(rollbackFor = Exception.class)
    public DocumentVersion createVersionSnapshot(Document document) {
        int next = documentVersionRepository.findByDocumentIdOrderByVersionNumberDesc(document.getId())
                .stream()
                .map(DocumentVersion::getVersionNumber)
                .findFirst()
                .orElse(0) + 1;
        DocumentVersion version = new DocumentVersion();
        version.setDocumentId(document.getId());
        version.setVersionNumber(next);
        if (document.getContent() != null) {
            version.setContent(document.getContent());
        } else {
            version.setContent("{}");
        }
        return documentVersionRepository.save(version);
    }

    /**
     * 回滚到指定版本：将版本内容写回文书并生成新版本记录。
     */
    @Transactional(rollbackFor = Exception.class)
    public Document rollbackToVersion(Long documentId, Long versionId, Integer versionNumber) {
        Document doc = documentRepository.findByIdAndIsDeletedFalse(documentId)
                .orElseThrow(() -> new EntityNotFoundException("文书", documentId));
        DocumentVersion target;
        if (versionId != null) {
            target = documentVersionRepository.findByDocumentIdAndId(documentId, versionId)
                    .orElseThrow(() -> new EntityNotFoundException("版本", versionId));
        } else if (versionNumber != null) {
            long versionKey = versionNumber.longValue();
            target = documentVersionRepository.findByDocumentIdAndVersionNumber(documentId, versionNumber)
                    .orElseThrow(() -> new EntityNotFoundException("版本", versionKey));
        } else {
            throw new ValidationException("请指定 versionId 或 versionNumber");
        }
        doc.setContent(target.getContent());
        doc = documentRepository.save(doc);
        createVersionSnapshot(doc);
        return doc;
    }

    /**
     * 更新文书状态（如锁定、解锁、归档）
     */
    @Transactional(rollbackFor = Exception.class)
    public Document updateStatus(Long documentId, String status) {
        Document doc = documentRepository.findByIdAndIsDeletedFalse(documentId)
                .orElseThrow(() -> new EntityNotFoundException("文书", documentId));
        doc.setStatus(status);
        return documentRepository.save(doc);
    }

    public List<DocumentVersion> getVersionList(Long documentId) {
        return documentVersionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId);
    }

    public Optional<DocumentVersion> getVersion(Long documentId, Long versionId, Integer versionNumber) {
        if (versionId != null) {
            return documentVersionRepository.findByDocumentIdAndId(documentId, versionId);
        }
        if (versionNumber != null) {
            return documentVersionRepository.findByDocumentIdAndVersionNumber(documentId, versionNumber);
        }
        return Optional.empty();
    }

    public Optional<Template> getTemplateById(Long templateId) {
        return templateRepository.findByIdAndIsDeletedFalse(templateId);
    }

    public Optional<DocumentType> getDocumentTypeByCode(String code) {
        return documentTypeRepository.findByCodeAndIsDeletedFalse(code);
    }

    /**
     * 启用的文书类型列表（用于初始化页面等）
     */
    public List<DocumentType> listDocumentTypes() {
        Specification<DocumentType> spec = SpecificationUtils.and(
                SpecificationUtils.<DocumentType>tenantFilter(),
                SpecificationUtils.<DocumentType>notDeleted(),
                SpecificationUtils.equal("enabled", true)
        );
        return documentTypeRepository.findAll(spec, Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("id")));
    }

    /**
     * 软删除文书
     */
    @Transactional(rollbackFor = Exception.class)
    public void softDelete(Long documentId) {
        Document doc = documentRepository.findByIdAndIsDeletedFalse(documentId)
                .orElseThrow(() -> new EntityNotFoundException("文书", documentId));
        doc.setIsDeleted(true);
        documentRepository.save(doc);
    }

    /**
     * 另存为：复制源文书内容到新文书
     */
    @Transactional(rollbackFor = Exception.class)
    public Document saveAs(Long sourceDocumentId, String patientId, String encounterId, String documentTypeCode) {
        Document source = documentRepository.findByIdAndIsDeletedFalse(sourceDocumentId)
                .orElseThrow(() -> new EntityNotFoundException("文书", sourceDocumentId));
        Document doc = new Document();
        doc.setPatientId(patientId);
        doc.setEncounterId(encounterId);
        doc.setDocumentTypeCode(documentTypeCode != null && !documentTypeCode.isEmpty()
                ? documentTypeCode
                : source.getDocumentTypeCode());
        doc.setTitle(source.getTitle());
        doc.setContent(source.getContent());
        doc.setStatus(STATUS_DRAFT);
        return documentRepository.save(doc);
    }

    private String resolveInitialContent(String documentTypeCode, Long templateId) {
        if (templateId != null) {
            return templateRepository.findByIdAndIsDeletedFalse(templateId)
                    .map(Template::getContent)
                    .orElse("{\"type\":\"doc\",\"content\":[]}");
        }
        Optional<DocumentType> typeOpt = documentTypeRepository.findByCodeAndIsDeletedFalse(documentTypeCode);
        if (typeOpt.isPresent() && typeOpt.get().getDefaultTemplateId() != null) {
            return templateRepository.findByIdAndIsDeletedFalse(typeOpt.get().getDefaultTemplateId())
                    .map(Template::getContent)
                    .orElse("{\"type\":\"doc\",\"content\":[]}");
        }
        return "{\"type\":\"doc\",\"content\":[]}";
    }
}
