/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.domain.documentedit.entity;

import com.zxplat.framework.core.application.exception.ValidationException;
import com.zxplat.framework.core.domain.BaseAuditedEntity;
import com.zxplat.plugin.emr.domain.enums.DocumentEditApplicationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 文书编辑申请（{@code emr_document_edit_application}）。
 */
@Getter
@Setter
@Entity
@Table(name = "emr_document_edit_application")
public class DocumentEditApplication extends BaseAuditedEntity {

    /** 文书主键。 */
    @Column(name = "document_id", nullable = false)
    private Long documentId;

    /** 就诊标识。 */
    @Size(max = 64)
    @Column(name = "encounter_id", nullable = false, length = 64)
    private String encounterId;

    /** 书写科室镜像。 */
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    /** 门诊/住院 class。 */
    @Size(max = 32)
    @Column(name = "encounter_class_code", length = 32)
    private String encounterClassCode;

    /** DRAFTING / PENDING_REVIEW / APPROVED / REJECTED。 */
    @Size(max = 32)
    @Column(name = "status_enum", nullable = false, length = 32)
    private String statusEnum;

    /** 申请原因。 */
    @Size(max = 500)
    @Column(name = "apply_reason", nullable = false, length = 500)
    private String applyReason;

    /** 驳回原因。 */
    @Size(max = 500)
    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    /** 创建时文书版本。 */
    @Column(name = "from_version", nullable = false)
    private Integer fromVersion;

    /** 目标版本。 */
    @Column(name = "to_version")
    private Integer toVersion;

    /** 旧 JSON。 */
    @Column(name = "old_content_json", nullable = false, columnDefinition = "TEXT")
    private String oldContentJson;

    /** 新 JSON 工作稿。 */
    @Column(name = "new_content_json", nullable = false, columnDefinition = "TEXT")
    private String newContentJson;

    /** 申请人。 */
    @Column(name = "applicant_id", nullable = false)
    private Long applicantId;

    /** 申请时间。 */
    @Column(name = "applied_at", nullable = false)
    private LocalDateTime appliedAt;

    /** 交审时间。 */
    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    /** 审核人。 */
    @Column(name = "reviewer_id")
    private Long reviewerId;

    /** 审核时间。 */
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /**
     * 初始化起草中申请。
     */
    public void initDrafting(
            Long tenantId,
            Long documentId,
            String encounterId,
            Long organizationId,
            String encounterClassCode,
            String applyReason,
            Integer fromVersion,
            String contentJson,
            Long applicantId,
            Long operatorId) {
        if (applyReason == null || applyReason.isBlank()) {
            throw new ValidationException("申请修改原因不能为空");
        }
        if (fromVersion == null || fromVersion < 1) {
            throw new ValidationException("文书版本无效");
        }
        if (contentJson == null || contentJson.isBlank()) {
            throw new ValidationException("文书快照不能为空");
        }
        if (organizationId == null) {
            throw new ValidationException("书写科室不能为空");
        }
        this.setTenantId(tenantId);
        this.documentId = documentId;
        this.encounterId = encounterId;
        this.organizationId = organizationId;
        this.encounterClassCode = encounterClassCode;
        this.statusEnum = DocumentEditApplicationStatus.DRAFTING.getCode();
        this.applyReason = applyReason.trim();
        this.fromVersion = fromVersion;
        this.oldContentJson = contentJson;
        this.newContentJson = contentJson;
        this.applicantId = applicantId;
        this.appliedAt = LocalDateTime.now();
        this.setCreatedAt(LocalDateTime.now());
        this.setCreatedBy(operatorId);
        this.setUpdatedAt(LocalDateTime.now());
        this.setUpdatedBy(operatorId);
    }

    /**
     * 暂存工作稿。
     */
    public void saveDraft(String newContentJson, Long operatorId) {
        assertStatus(DocumentEditApplicationStatus.DRAFTING, "仅起草中可暂存");
        if (newContentJson == null || newContentJson.isBlank()) {
            throw new ValidationException("工作稿不能为空");
        }
        this.newContentJson = newContentJson;
        this.setUpdatedAt(LocalDateTime.now());
        this.setUpdatedBy(operatorId);
    }

    /**
     * 交审。
     */
    public void submitForReview(Long operatorId) {
        assertStatus(DocumentEditApplicationStatus.DRAFTING, "仅起草中可提交审核");
        this.toVersion = this.fromVersion + 1;
        this.statusEnum = DocumentEditApplicationStatus.PENDING_REVIEW.getCode();
        this.submittedAt = LocalDateTime.now();
        this.setUpdatedAt(LocalDateTime.now());
        this.setUpdatedBy(operatorId);
    }

    /**
     * 标记通过。
     */
    public void markApproved(Long reviewerId) {
        assertStatus(DocumentEditApplicationStatus.PENDING_REVIEW, "仅待审核可同意");
        this.statusEnum = DocumentEditApplicationStatus.APPROVED.getCode();
        this.reviewerId = reviewerId;
        this.reviewedAt = LocalDateTime.now();
        this.setUpdatedAt(LocalDateTime.now());
        this.setUpdatedBy(reviewerId);
    }

    /**
     * 标记驳回终态。
     */
    public void markRejected(String rejectReason, Long reviewerId) {
        assertStatus(DocumentEditApplicationStatus.PENDING_REVIEW, "仅待审核可驳回");
        if (rejectReason == null || rejectReason.isBlank()) {
            throw new ValidationException("驳回原因不能为空");
        }
        this.rejectReason = rejectReason.trim();
        this.statusEnum = DocumentEditApplicationStatus.REJECTED.getCode();
        this.reviewerId = reviewerId;
        this.reviewedAt = LocalDateTime.now();
        this.setUpdatedAt(LocalDateTime.now());
        this.setUpdatedBy(reviewerId);
    }

    /**
     * 断言当前状态等于期望枚举，否则抛校验异常。
     *
     * @param expected 期望状态
     * @param message 失败提示
     */
    private void assertStatus(DocumentEditApplicationStatus expected, String message) {
        if (!expected.getCode().equals(this.statusEnum)) {
            throw new ValidationException(message);
        }
    }
}
