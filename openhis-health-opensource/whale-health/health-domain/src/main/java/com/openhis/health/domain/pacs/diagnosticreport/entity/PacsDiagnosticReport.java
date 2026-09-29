/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.pacs.diagnosticreport.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.PacsReportStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 检查报告聚合根（{@code pacs_diagnostic_report}）。
 */
@Getter
@Setter
@Entity
@Table(name = "pacs_diagnostic_report")
public class PacsDiagnosticReport extends BaseAuditedEntity {

    @Column(name = "work_item_id", nullable = false)
    private Long workItemId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @Column(name = "organization_id")
    private Long organizationId;

    @Column(name = "service_request_id")
    private Long serviceRequestId;

    @Column(name = "status_enum", nullable = false)
    private String statusEnum;

    @Column(name = "findings")
    private String findings;

    @Column(name = "conclusion")
    private String conclusion;

    @Column(name = "pdf_storage_key")
    private String pdfStorageKey;

    @Column(name = "author_practitioner_id")
    private Long authorPractitionerId;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "submitted_by")
    private Long submittedBy;

    @Column(name = "issued_at")
    private LocalDateTime issuedAt;

    @Column(name = "auditor_practitioner_id")
    private Long auditorPractitionerId;

    @Column(name = "amended_from_id")
    private Long amendedFromId;

    /**
     * 初始化为可编辑草稿。
     */
    public void initPreliminary() {
        this.statusEnum = PacsReportStatus.PRELIMINARY.getCode();
        this.submittedAt = null;
        this.submittedBy = null;
    }

    /**
     * 是否待审。
     *
     * @return true=已提交待审
     */
    public boolean isPendingAudit() {
        return PacsReportStatus.PRELIMINARY.getCode().equals(this.statusEnum) && this.submittedAt != null;
    }

    /**
     * 是否可编辑正文。
     */
    public void assertEditable() {
        if (PacsReportStatus.FINAL.getCode().equals(this.statusEnum)
            || PacsReportStatus.AMENDED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.PACS_REPORT_ALREADY_FINAL, "报告已发布，不可改草稿");
        }
        if (this.isPendingAudit()) {
            throw new BusinessException(ErrorCodeConstants.PACS_REPORT_PENDING_AUDIT, "报告待审中，不可改正文");
        }
        if (PacsReportStatus.ENTERED_IN_ERROR.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.PACS_REPORT_INVALID_STATUS, "报告已作废");
        }
    }

    /**
     * 保存草稿正文。
     *
     * @param findingsText 所见
     * @param conclusionText 结论
     * @param authorId 书写医生
     */
    public void saveDraft(String findingsText, String conclusionText, Long authorId) {
        this.assertEditable();
        this.findings = findingsText;
        this.conclusion = conclusionText;
        if (authorId != null) {
            this.authorPractitionerId = authorId;
        }
    }

    /**
     * 提交待审。
     *
     * @param operatorId 提交人
     * @param at 提交时间
     */
    public void submit(Long operatorId, LocalDateTime at) {
        this.assertEditable();
        boolean emptyFindings = StringUtils.isEmpty(this.findings);
        boolean emptyConclusion = StringUtils.isEmpty(this.conclusion);
        if (emptyFindings && emptyConclusion) {
            throw new BusinessException(
                ErrorCodeConstants.PACS_REPORT_INVALID_ARGUMENT, "所见与结论不能同时为空");
        }
        this.submittedAt = at;
        if (this.submittedAt == null) {
            this.submittedAt = LocalDateTime.now();
        }
        this.submittedBy = operatorId;
        this.statusEnum = PacsReportStatus.PRELIMINARY.getCode();
    }

    /**
     * 绑定 PDF 存储键。
     *
     * @param storageKey 对象存储键
     */
    public void bindPdf(String storageKey) {
        if (StringUtils.isEmpty(storageKey)) {
            throw new BusinessException(
                ErrorCodeConstants.PACS_REPORT_INVALID_ARGUMENT, "pdfStorageKey 不能为空");
        }
        if (PacsReportStatus.ENTERED_IN_ERROR.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.PACS_REPORT_INVALID_STATUS, "报告已作废");
        }
        this.pdfStorageKey = storageKey.trim();
    }

    /**
     * 审核通过。
     *
     * @param auditorId 审核人
     * @param at 发布时间
     */
    public void approve(Long auditorId, LocalDateTime at) {
        if (!this.isPendingAudit()) {
            throw new BusinessException(ErrorCodeConstants.PACS_REPORT_INVALID_STATUS, "非待审状态不可审核通过");
        }
        this.statusEnum = PacsReportStatus.FINAL.getCode();
        this.auditorPractitionerId = auditorId;
        this.issuedAt = at;
        if (this.issuedAt == null) {
            this.issuedAt = LocalDateTime.now();
        }
    }

    /**
     * 审核退回为可编辑草稿。
     */
    public void reject() {
        if (!this.isPendingAudit()) {
            throw new BusinessException(ErrorCodeConstants.PACS_REPORT_INVALID_STATUS, "非待审状态不可退回");
        }
        this.submittedAt = null;
        this.submittedBy = null;
        this.statusEnum = PacsReportStatus.PRELIMINARY.getCode();
    }

    /**
     * 取消审核：已发布回退为待审（保留 submittedAt，清审核人/发布时间）。
     *
     * <p>与 {@link #reject()} 不同：仍处于待审队列，不可改正文，须再次通过或退回。</p>
     */
    public void cancelAudit() {
        if (!PacsReportStatus.FINAL.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.PACS_REPORT_INVALID_STATUS, "非已发布状态不可取消审核");
        }
        this.statusEnum = PacsReportStatus.PRELIMINARY.getCode();
        // 保证回待审：若历史数据缺 submittedAt，补当前时间
        if (this.submittedAt == null) {
            this.submittedAt = LocalDateTime.now();
        }
        this.issuedAt = null;
        this.auditorPractitionerId = null;
    }
}
