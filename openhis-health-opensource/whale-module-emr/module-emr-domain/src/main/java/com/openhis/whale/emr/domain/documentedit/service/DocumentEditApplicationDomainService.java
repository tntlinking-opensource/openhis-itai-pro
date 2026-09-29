/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.domain.documentedit.service;

import com.openhis.whale.core.application.exception.EntityNotFoundException;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.emr.domain.document.entity.Document;
import com.openhis.whale.emr.domain.document.service.DocumentManager;
import com.openhis.whale.emr.domain.enums.DocumentEditApplicationStatus;
import com.openhis.whale.emr.domain.documentedit.entity.DocumentEditApplication;
import com.openhis.whale.emr.domain.documentedit.repository.DocumentEditApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 文书编辑申请写侧（DM-EMR-002）。
 */
@Service
@RequiredArgsConstructor
public class DocumentEditApplicationDomainService {

    private static final List<String> ACTIVE = List.of(
            DocumentEditApplicationStatus.DRAFTING.getCode(),
            DocumentEditApplicationStatus.PENDING_REVIEW.getCode());

    private final DocumentEditApplicationRepository documentEditApplicationRepository;
    private final DocumentManager documentManager;

    /**
     * 按主键加载。
     *
     * @param id 申请主键
     * @return 申请
     */
    public DocumentEditApplication requireById(Long id) {
        return documentEditApplicationRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new EntityNotFoundException("文书编辑申请", id));
    }

    /**
     * 创建申请（调用方须确认文书 ARCHIVED）。
     *
     * @param tenantId 租户
     * @param documentId 文书
     * @param encounterClassCode 门诊/住院
     * @param applyReason 原因
     * @param applicantId 申请人
     * @param operatorId 操作人
     * @return 新建申请
     */
    @Transactional(rollbackFor = Exception.class)
    public DocumentEditApplication create(
            Long tenantId,
            Long documentId,
            String encounterClassCode,
            String applyReason,
            Long applicantId,
            Long operatorId) {
        Document doc = documentManager.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("文书", documentId));
        String status = "";
        if (doc.getStatus() != null) {
            status = doc.getStatus().trim();
        }
        if (!DocumentManager.STATUS_ARCHIVED.equals(status)) {
            throw new ValidationException("仅已归档文书可申请编辑");
        }
        List<DocumentEditApplication> active = documentEditApplicationRepository
                .findByTenantIdAndDocumentIdAndStatusEnumInAndIsDeletedFalse(tenantId, documentId, ACTIVE);
        if (!active.isEmpty()) {
            throw new ValidationException("已有在途编辑申请");
        }
        Integer versionNo = doc.getVersionNo();
        if (versionNo == null) {
            versionNo = 1;
        }
        if (doc.getOrganizationId() == null) {
            throw new ValidationException("文书缺少书写科室，无法发起编辑申请");
        }
        String snapshotContent = "{}";
        if (doc.getContent() != null && !doc.getContent().isBlank()) {
            snapshotContent = doc.getContent();
        }
        DocumentEditApplication entity = new DocumentEditApplication();
        entity.initDrafting(
                tenantId,
                documentId,
                doc.getEncounterId(),
                doc.getOrganizationId(),
                encounterClassCode,
                applyReason,
                versionNo,
                snapshotContent,
                applicantId,
                operatorId);
        return documentEditApplicationRepository.save(entity);
    }

    /**
     * 暂存工作稿 JSON。
     *
     * @param applicationId 申请主键
     * @param newContentJson 新正文工作稿
     * @param operatorId 操作人
     * @return 更新后申请
     */
    @Transactional(rollbackFor = Exception.class)
    public DocumentEditApplication saveDraft(Long applicationId, String newContentJson, Long operatorId) {
        DocumentEditApplication entity = requireById(applicationId);
        entity.saveDraft(newContentJson, operatorId);
        return documentEditApplicationRepository.save(entity);
    }

    /**
     * 交审：DRAFTING → PENDING_REVIEW。
     *
     * @param applicationId 申请主键
     * @param operatorId 操作人
     * @return 更新后申请
     */
    @Transactional(rollbackFor = Exception.class)
    public DocumentEditApplication submitForReview(Long applicationId, Long operatorId) {
        DocumentEditApplication entity = requireById(applicationId);
        entity.submitForReview(operatorId);
        return documentEditApplicationRepository.save(entity);
    }

    /**
     * 同意并回写文书 content / version_no。
     *
     * @param applicationId 申请主键
     * @param reviewerId 审核人
     * @return 更新后申请
     */
    @Transactional(rollbackFor = Exception.class)
    public DocumentEditApplication approve(Long applicationId, Long reviewerId) {
        DocumentEditApplication entity = requireById(applicationId);
        // Gate：仅待审核可回写主档
        if (!DocumentEditApplicationStatus.PENDING_REVIEW.getCode().equals(entity.getStatusEnum())) {
            throw new ValidationException("仅待审核可同意");
        }
        // Cmd：先回写文书再标申请终态
        documentManager.applyApprovedEdit(
                entity.getDocumentId(),
                entity.getNewContentJson(),
                entity.getFromVersion(),
                entity.getToVersion(),
                reviewerId);
        entity.markApproved(reviewerId);
        return documentEditApplicationRepository.save(entity);
    }

    /**
     * 驳回为终态。
     *
     * @param applicationId 申请主键
     * @param rejectReason 驳回原因
     * @param reviewerId 审核人
     * @return 更新后申请
     */
    @Transactional(rollbackFor = Exception.class)
    public DocumentEditApplication reject(Long applicationId, String rejectReason, Long reviewerId) {
        DocumentEditApplication entity = requireById(applicationId);
        entity.markRejected(rejectReason, reviewerId);
        return documentEditApplicationRepository.save(entity);
    }
}
