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

    /** 草稿（可编辑）。 */
    public static final String STATUS_DRAFT = "DRAFT";
    /** 待归档审批。 */
    public static final String STATUS_PENDING_ARCHIVE = "PENDING_ARCHIVE";
    /** 已归档（只读）。 */
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
     * 分页列表：按患者、就诊、文书类型、状态筛选（单就诊）。
     */
    public Page<Document> findPage(String patientId, String encounterId, String documentTypeCode, String status, Pageable pageable) {
        List<String> encounterIds = null;
        if (encounterId != null && !encounterId.isEmpty()) {
            encounterIds = List.of(encounterId);
        }
        return findPage(patientId, encounterIds, documentTypeCode, status, pageable);
    }

    /**
     * 分页列表：按患者、就诊集合（IN）、文书类型、状态筛选。
     *
     * @param encounterIds 就诊 id 集合；空或 null 表示不按就诊过滤
     */
    public Page<Document> findPage(
            String patientId,
            List<String> encounterIds,
            String documentTypeCode,
            String status,
            Pageable pageable) {
        Specification<Document> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("isDeleted"), false));
            if (patientId != null && !patientId.isEmpty()) {
                predicates.add(cb.equal(root.get("patientId"), patientId));
            }
            if (encounterIds != null && !encounterIds.isEmpty()) {
                if (encounterIds.size() == 1) {
                    predicates.add(cb.equal(root.get("encounterId"), encounterIds.get(0)));
                } else {
                    predicates.add(root.get("encounterId").in(encounterIds));
                }
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
        return create(patientId, encounterId, documentTypeCode, templateId, null);
    }

    /**
     * 创建文书（须带书写时科室，DM-EMR-001）。
     *
     * @param patientId 患者
     * @param encounterId 就诊
     * @param documentTypeCode 类型
     * @param templateId 模板
     * @param organizationId 书写时科室；可空时存量兼容，新业务应传入
     * @return 新建文书
     */
    @Transactional(rollbackFor = Exception.class)
    public Document create(
            String patientId,
            String encounterId,
            String documentTypeCode,
            Long templateId,
            Long organizationId) {
        Document doc = new Document();
        doc.setPatientId(patientId);
        doc.setEncounterId(encounterId);
        doc.setDocumentTypeCode(documentTypeCode);
        doc.setStatus(STATUS_DRAFT);
        doc.setVersionNo(1);
        doc.setOrganizationId(organizationId);
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
        if (STATUS_PENDING_ARCHIVE.equals(trimStatus(doc.getStatus()))
                || STATUS_ARCHIVED.equals(trimStatus(doc.getStatus()))) {
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
     * 提交归档：DRAFT → PENDING_ARCHIVE。
     *
     * @param documentId 文书
     * @param operatorId 提交人
     * @return 更新后文书
     */
    @Transactional(rollbackFor = Exception.class)
    public Document submitArchive(Long documentId, Long operatorId) {
        Document doc = documentRepository.findByIdAndIsDeletedFalse(documentId)
                .orElseThrow(() -> new EntityNotFoundException("文书", documentId));
        if (!STATUS_DRAFT.equals(trimStatus(doc.getStatus()))) {
            throw new ValidationException("仅草稿可提交归档");
        }
        doc.setStatus(STATUS_PENDING_ARCHIVE);
        doc.setArchiveSubmittedBy(operatorId);
        doc.setArchiveSubmittedAt(LocalDateTime.now());
        doc.setArchiveRejectReason(null);
        return documentRepository.save(doc);
    }

    /**
     * 同批多文书提交归档：每份独立校验；任一份失败整批回滚（BR-EMR-DOC-008）。
     *
     * @param documentIds 文书主键列表
     * @param operatorId 提交人
     * @return 提交后的文书列表（顺序与入参一致）
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Document> submitArchiveBatch(List<Long> documentIds, Long operatorId) {
        if (documentIds == null || documentIds.isEmpty()) {
            throw new ValidationException("提交归档文书列表不能为空");
        }
        List<Document> result = new ArrayList<>();
        for (Long documentId : documentIds) {
            // 逐份走单条门禁；失败抛异常触发整批回滚
            result.add(submitArchive(documentId, operatorId));
        }
        return result;
    }

    /**
     * 归档同意：PENDING_ARCHIVE → ARCHIVED（不升 version_no）。
     *
     * @param documentId 文书
     * @param operatorId 审批人
     * @return 更新后文书
     */
    @Transactional(rollbackFor = Exception.class)
    public Document approveArchive(Long documentId, Long operatorId) {
        Document doc = documentRepository.findByIdAndIsDeletedFalse(documentId)
                .orElseThrow(() -> new EntityNotFoundException("文书", documentId));
        if (!STATUS_PENDING_ARCHIVE.equals(trimStatus(doc.getStatus()))) {
            throw new ValidationException("仅待归档审批可同意");
        }
        doc.setStatus(STATUS_ARCHIVED);
        doc.setArchiveApprovedBy(operatorId);
        doc.setArchiveApprovedAt(LocalDateTime.now());
        return documentRepository.save(doc);
    }

    /**
     * 归档驳回：PENDING_ARCHIVE → DRAFT。
     *
     * @param documentId 文书
     * @param rejectReason 驳回原因
     * @param operatorId 审批人
     * @return 更新后文书
     */
    @Transactional(rollbackFor = Exception.class)
    public Document rejectArchive(Long documentId, String rejectReason, Long operatorId) {
        Document doc = documentRepository.findByIdAndIsDeletedFalse(documentId)
                .orElseThrow(() -> new EntityNotFoundException("文书", documentId));
        if (!STATUS_PENDING_ARCHIVE.equals(trimStatus(doc.getStatus()))) {
            throw new ValidationException("仅待归档审批可驳回");
        }
        if (!StringUtils.hasText(rejectReason)) {
            throw new ValidationException("驳回原因不能为空");
        }
        doc.setStatus(STATUS_DRAFT);
        doc.setArchiveRejectReason(rejectReason.trim());
        return documentRepository.save(doc);
    }

    /**
     * 编辑审核通过后回写 content 并设定业务版本。
     *
     * @param documentId 文书
     * @param newContent 新正文
     * @param expectedFromVersion 申请 from_version
     * @param toVersion 目标版本
     * @param operatorId 操作人
     * @return 更新后文书
     */
    @Transactional(rollbackFor = Exception.class)
    public Document applyApprovedEdit(
            Long documentId,
            String newContent,
            Integer expectedFromVersion,
            Integer toVersion,
            Long operatorId) {
        Document doc = documentRepository.findByIdAndIsDeletedFalse(documentId)
                .orElseThrow(() -> new EntityNotFoundException("文书", documentId));
        if (!STATUS_ARCHIVED.equals(trimStatus(doc.getStatus()))) {
            throw new ValidationException("仅已归档文书可回写编辑结果");
        }
        Integer currentVersion = doc.getVersionNo();
        if (currentVersion == null) {
            currentVersion = 1;
        }
        if (expectedFromVersion == null || !expectedFromVersion.equals(currentVersion)) {
            throw new ValidationException("文书版本已变化，请重新申请编辑");
        }
        if (toVersion == null || toVersion != expectedFromVersion + 1) {
            throw new ValidationException("目标版本无效");
        }
        doc.setContent(newContent);
        doc.setVersionNo(toVersion);
        doc.setStatus(STATUS_ARCHIVED);
        return documentRepository.save(doc);
    }

    /**
     * 状态 trim；不做大小写兼容。
     *
     * @param status 原始状态
     * @return trim 后；空则空串
     */
    private String trimStatus(String status) {
        if (status == null) {
            return "";
        }
        return status.trim();
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
        // 未传类型时沿用源文书类型
        String typeCode = source.getDocumentTypeCode();
        if (documentTypeCode != null && !documentTypeCode.isEmpty()) {
            typeCode = documentTypeCode;
        }
        doc.setDocumentTypeCode(typeCode);
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
