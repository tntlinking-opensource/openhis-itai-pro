/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.mrms.borrowrequest.entity;

import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import com.openhis.health.domain.share.enums.BorrowRequestStatus;
import com.openhis.health.domain.share.constants.MrmsErrorCodes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 病案借阅/复印申请聚合根（{@code mrms_borrow_request}）。
 *
 * <p>状态机：pending → approved/rejected → borrowed → returned。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "mrms_borrow_request")
public class BorrowRequest extends BaseAuditedEntity {

    /** 关联病案主档 {@code mrms_case_record.id}。 */
    @Column(name = "case_record_id", nullable = false)
    private Long caseRecordId;

    /** 申请类型 code（借阅/复印）。 */
    @Column(name = "request_type_code", nullable = false, length = 16)
    private String requestTypeCode;

    /** 申请人 {@code adm_practitioner.id}。 */
    @Column(name = "applicant_practitioner_id")
    private Long applicantPractitionerId;

    /** 申请人姓名快照（冗余展示，避免 join practitioner）。 */
    @Column(name = "applicant_name", length = 50)
    private String applicantName;

    /** 申请人科室名称（冗余展示）。 */
    @Column(name = "applicant_dept_name", length = 100)
    private String applicantDeptName;

    /** 借阅/复印用途说明（审批与合规留痕）。 */
    @Column(name = "purpose", length = 500)
    private String purpose;

    /** 借阅范围描述（章节/页码等）。 */
    @Column(name = "scope_desc", length = 500)
    private String scopeDesc;

    /** 复印份数（复印申请时填写）。 */
    @Column(name = "copy_count")
    private Integer copyCount;

    /** 患者授权信息摘要（合规留痕）。 */
    @Column(name = "patient_authorization_info", length = 500)
    private String patientAuthorizationInfo;

    /** 工作流状态 code，存 {@link BorrowRequestStatus#getCode()}。 */
    @Column(name = "status_code", nullable = false, length = 32)
    private String statusCode;

    /** 审批意见（批准或拒绝时填写）。 */
    @Column(name = "approve_comment", length = 500)
    private String approveComment;

    /** 审批人 {@code adm_practitioner.id}。 */
    @Column(name = "approved_by")
    private Long approvedBy;

    /** 审批完成时间（批准或拒绝）。 */
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    /** 实际借出登记时间。 */
    @Column(name = "borrowed_at")
    private LocalDateTime borrowedAt;

    /** 应归还时间（借出时约定）。 */
    @Column(name = "due_return_at")
    private LocalDateTime dueReturnAt;

    /** 实际归还登记时间。 */
    @Column(name = "returned_at")
    private LocalDateTime returnedAt;

    /**
     * 新建借阅/复印申请，初始状态 pending。
     *
     * @param tenantId 租户
     * @param caseRecordId 病案主档
     * @param requestTypeCode 申请类型
     * @param applicantPractitionerId 申请人
     * @param applicantName 申请人姓名
     * @param applicantDeptName 申请人科室
     * @param purpose 用途
     * @param scopeDesc 范围
     * @param copyCount 复印份数
     * @param patientAuthorizationInfo 患者授权
     * @param operatorId 操作人
     */
    public void initApply(
            Long tenantId,
            Long caseRecordId,
            String requestTypeCode,
            Long applicantPractitionerId,
            String applicantName,
            String applicantDeptName,
            String purpose,
            String scopeDesc,
            Integer copyCount,
            String patientAuthorizationInfo,
            Long operatorId) {
        this.setTenantId(tenantId);
        this.caseRecordId = caseRecordId;
        this.requestTypeCode = trimToNull(requestTypeCode);
        this.applicantPractitionerId = applicantPractitionerId;
        this.applicantName = trimToNull(applicantName);
        this.applicantDeptName = trimToNull(applicantDeptName);
        this.purpose = trimToNull(purpose);
        this.scopeDesc = trimToNull(scopeDesc);
        this.copyCount = copyCount;
        this.patientAuthorizationInfo = trimToNull(patientAuthorizationInfo);
        this.statusCode = BorrowRequestStatus.PENDING.getCode();
        this.setCreatedAt(LocalDateTime.now());
        this.setCreatedBy(operatorId);
        this.setIsDeleted(false);
    }

    /**
     * 审批通过：pending → approved，记录审批人与意见。
     *
     * @param comment 审批意见
     * @param operatorId 审批人主键
     */
    public void approve(String comment, Long operatorId) {
        // 仅 pending 可批准
        assertStatus(BorrowRequestStatus.PENDING);
        this.statusCode = BorrowRequestStatus.APPROVED.getCode();
        this.approveComment = trimToNull(comment);
        this.approvedBy = operatorId;
        this.approvedAt = LocalDateTime.now();
        touch(operatorId);
    }

    /**
     * 审批拒绝：pending → rejected，记录拒绝原因。
     *
     * @param comment 拒绝原因
     * @param operatorId 审批人主键
     */
    public void reject(String comment, Long operatorId) {
        // 仅 pending 可拒绝
        assertStatus(BorrowRequestStatus.PENDING);
        this.statusCode = BorrowRequestStatus.REJECTED.getCode();
        this.approveComment = trimToNull(comment);
        this.approvedBy = operatorId;
        this.approvedAt = LocalDateTime.now();
        touch(operatorId);
    }

    /**
     * 借出登记：approved → borrowed，写入借出时间与应还日。
     *
     * @param dueReturnAt 约定应还时间
     * @param operatorId 经办人主键
     */
    public void markBorrowed(LocalDateTime dueReturnAt, Long operatorId) {
        // 仅 approved 可借出
        assertStatus(BorrowRequestStatus.APPROVED);
        this.statusCode = BorrowRequestStatus.BORROWED.getCode();
        this.borrowedAt = LocalDateTime.now();
        this.dueReturnAt = dueReturnAt;
        touch(operatorId);
    }

    /**
     * 归还登记：borrowed → returned，结束借阅流程。
     *
     * @param operatorId 经办人主键
     */
    public void markReturned(Long operatorId) {
        // 仅 borrowed 可归还
        assertStatus(BorrowRequestStatus.BORROWED);
        this.statusCode = BorrowRequestStatus.RETURNED.getCode();
        this.returnedAt = LocalDateTime.now();
        touch(operatorId);
    }

    private void assertStatus(BorrowRequestStatus expected) {
        if (!expected.getCode().equals(statusCode)) {
            throw new BusinessException(MrmsErrorCodes.BORROW_INVALID_STATUS, "借阅申请状态不允许当前操作");
        }
    }

    private void touch(Long operatorId) {
        this.setUpdatedAt(LocalDateTime.now());
        this.setUpdatedBy(operatorId);
    }
}
