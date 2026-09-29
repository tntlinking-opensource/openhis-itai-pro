/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.mrms.homepageedit.entity;

import com.openhis.health.domain.share.constants.MrmsErrorCodes;
import com.openhis.health.domain.share.enums.HomepageEditApplicationStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 病案首页编辑申请（{@code mrms_homepage_edit_application}）。
 *
 * <p>仅挂本域 {@code case_record_id}；状态 DRAFTING → PENDING_REVIEW → APPROVED/REJECTED。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "mrms_homepage_edit_application")
public class HomepageEditApplication extends BaseAuditedEntity {

    /** 病案主档主键。 */
    @Column(name = "case_record_id", nullable = false)
    private Long caseRecordId;

    /** 住院就诊主键。 */
    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    /** 状态 code，见 {@link HomepageEditApplicationStatus}。 */
    @Column(name = "status_enum", nullable = false, length = 32)
    private String statusEnum;

    /** 申请修改原因。 */
    @Column(name = "apply_reason", nullable = false, length = 500)
    private String applyReason;

    /** 驳回原因。 */
    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    /** 创建时首页业务版本。 */
    @Column(name = "from_version", nullable = false)
    private Integer fromVersion;

    /** 交审后目标版本（from+1）。 */
    @Column(name = "to_version")
    private Integer toVersion;

    /** 创建时首页可编辑分区快照 JSON。 */
    @Column(name = "old_content_json", nullable = false, columnDefinition = "TEXT")
    private String oldContentJson;

    /** 工作稿 JSON。 */
    @Column(name = "new_content_json", nullable = false, columnDefinition = "TEXT")
    private String newContentJson;

    /** 申请人 practitionerId。 */
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
     *
     * @param tenantId 租户
     * @param caseRecordId 病案
     * @param encounterId 就诊
     * @param applyReason 申请原因
     * @param fromVersion 当前首页版本
     * @param contentJson 主档快照（old=new 初值）
     * @param applicantId 申请人
     * @param operatorId 操作人
     */
    public void initDrafting(
            Long tenantId,
            Long caseRecordId,
            Long encounterId,
            String applyReason,
            Integer fromVersion,
            String contentJson,
            Long applicantId,
            Long operatorId) {
        String reason = trimToNull(applyReason);
        if (reason == null) {
            throw new BusinessException(MrmsErrorCodes.HOMEPAGE_EDIT_INVALID_STATUS, "申请修改原因不能为空");
        }
        if (fromVersion == null || fromVersion < 1) {
            throw new BusinessException(MrmsErrorCodes.HOMEPAGE_EDIT_INVALID_STATUS, "首页版本无效");
        }
        String json = trimToNull(contentJson);
        if (json == null) {
            throw new BusinessException(MrmsErrorCodes.HOMEPAGE_EDIT_INVALID_STATUS, "首页快照不能为空");
        }
        this.setTenantId(tenantId);
        this.caseRecordId = caseRecordId;
        this.encounterId = encounterId;
        this.statusEnum = HomepageEditApplicationStatus.DRAFTING.getCode();
        this.applyReason = reason;
        this.fromVersion = fromVersion;
        this.oldContentJson = json;
        this.newContentJson = json;
        this.applicantId = applicantId;
        this.appliedAt = LocalDateTime.now();
        this.setCreatedAt(LocalDateTime.now());
        this.setCreatedBy(operatorId);
        this.setUpdatedAt(LocalDateTime.now());
        this.setUpdatedBy(operatorId);
    }

    /**
     * 暂存工作稿（仅 DRAFTING）。
     *
     * @param newContentJson 工作稿
     * @param operatorId 操作人
     */
    public void saveDraft(String newContentJson, Long operatorId) {
        assertStatus(HomepageEditApplicationStatus.DRAFTING, "仅起草中可暂存");
        String json = trimToNull(newContentJson);
        if (json == null) {
            throw new BusinessException(MrmsErrorCodes.HOMEPAGE_EDIT_INVALID_STATUS, "工作稿不能为空");
        }
        this.newContentJson = json;
        this.setUpdatedAt(LocalDateTime.now());
        this.setUpdatedBy(operatorId);
    }

    /**
     * 提交审核：DRAFTING → PENDING_REVIEW，写入 to_version。
     *
     * @param operatorId 操作人
     */
    public void submitForReview(Long operatorId) {
        assertStatus(HomepageEditApplicationStatus.DRAFTING, "仅起草中可提交审核");
        this.toVersion = this.fromVersion + 1;
        this.statusEnum = HomepageEditApplicationStatus.PENDING_REVIEW.getCode();
        this.submittedAt = LocalDateTime.now();
        this.setUpdatedAt(LocalDateTime.now());
        this.setUpdatedBy(operatorId);
    }

    /**
     * 审核通过（仅改本单状态；回写首页由 DomainService 编排）。
     *
     * @param reviewerId 审核人
     */
    public void markApproved(Long reviewerId) {
        assertStatus(HomepageEditApplicationStatus.PENDING_REVIEW, "仅待审核可同意");
        this.statusEnum = HomepageEditApplicationStatus.APPROVED.getCode();
        this.reviewerId = reviewerId;
        this.reviewedAt = LocalDateTime.now();
        this.setUpdatedAt(LocalDateTime.now());
        this.setUpdatedBy(reviewerId);
    }

    /**
     * 驳回为终态。
     *
     * @param rejectReason 驳回原因
     * @param reviewerId 审核人
     */
    public void markRejected(String rejectReason, Long reviewerId) {
        assertStatus(HomepageEditApplicationStatus.PENDING_REVIEW, "仅待审核可驳回");
        String reason = trimToNull(rejectReason);
        if (reason == null) {
            throw new BusinessException(MrmsErrorCodes.HOMEPAGE_EDIT_INVALID_STATUS, "驳回原因不能为空");
        }
        this.rejectReason = reason;
        this.statusEnum = HomepageEditApplicationStatus.REJECTED.getCode();
        this.reviewerId = reviewerId;
        this.reviewedAt = LocalDateTime.now();
        this.setUpdatedAt(LocalDateTime.now());
        this.setUpdatedBy(reviewerId);
    }

    /**
     * 断言当前状态。
     *
     * @param expected 期望状态
     * @param message 失败提示
     */
    private void assertStatus(HomepageEditApplicationStatus expected, String message) {
        if (!expected.getCode().equals(this.statusEnum)) {
            throw new BusinessException(MrmsErrorCodes.HOMEPAGE_EDIT_INVALID_STATUS, message);
        }
    }
}
