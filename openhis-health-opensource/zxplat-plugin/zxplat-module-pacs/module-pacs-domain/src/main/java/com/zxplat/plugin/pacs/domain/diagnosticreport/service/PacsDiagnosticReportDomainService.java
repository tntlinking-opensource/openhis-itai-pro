/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.domain.diagnosticreport.service;

import com.zxplat.plugin.pacs.domain.diagnosticreport.command.ApprovePacsReportCommand;
import com.zxplat.plugin.pacs.domain.diagnosticreport.command.BindPacsReportPdfCommand;
import com.zxplat.plugin.pacs.domain.diagnosticreport.command.CancelPacsReportAuditCommand;
import com.zxplat.plugin.pacs.domain.diagnosticreport.command.OpenOrCreatePacsReportCommand;
import com.zxplat.plugin.pacs.domain.diagnosticreport.command.RejectPacsReportCommand;
import com.zxplat.plugin.pacs.domain.diagnosticreport.command.SavePacsReportDraftCommand;
import com.zxplat.plugin.pacs.domain.diagnosticreport.command.SubmitPacsReportCommand;
import com.zxplat.plugin.pacs.domain.diagnosticreport.entity.PacsDiagnosticReport;
import com.zxplat.plugin.pacs.domain.diagnosticreport.repository.PacsDiagnosticReportRepository;
import com.zxplat.plugin.pacs.domain.workitem.entity.PacsWorkItem;
import com.zxplat.plugin.pacs.domain.workitem.repository.PacsWorkItemRepository;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.enums.PacsWorkItemStatus;
import com.zxplat.domain.share.events.pacs.diagnosticreport.PacsReportApprovedEvent;
import com.zxplat.domain.share.events.pacs.diagnosticreport.PacsReportAuditCancelledEvent;
import com.zxplat.domain.share.events.pacs.diagnosticreport.PacsReportDraftSavedEvent;
import com.zxplat.domain.share.events.pacs.diagnosticreport.PacsReportPdfBoundEvent;
import com.zxplat.domain.share.events.pacs.diagnosticreport.PacsReportRejectedEvent;
import com.zxplat.domain.share.events.pacs.diagnosticreport.PacsReportSubmittedEvent;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import com.zxplat.framework.core.domain.event.IDomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 检查报告写侧领域服务（DM-PACS-002）。
 */
@Service
@RequiredArgsConstructor
public class PacsDiagnosticReportDomainService {

    private final PacsDiagnosticReportRepository reportRepository;
    private final PacsWorkItemRepository workItemRepository;
    private final IDomainEventPublisher domainEventPublisher;

    /**
     * 按工单打开或创建草稿（BR-PDR-01/02）。
     *
     * @param cmd 命令
     * @return 报告
     */
    public PacsDiagnosticReport openOrCreate(OpenOrCreatePacsReportCommand cmd) {
        if (cmd == null || cmd.getWorkItemId() == null) {
            throw new BusinessException(ErrorCodeConstants.PACS_REPORT_INVALID_ARGUMENT, "workItemId 不能为空");
        }
        Optional<PacsDiagnosticReport> existing =
            reportRepository.findByWorkItemIdAndAmendedFromIdIsNull(cmd.getWorkItemId());
        if (existing.isPresent()) {
            return existing.get();
        }
        PacsWorkItem workItem = workItemRepository.findById(cmd.getWorkItemId())
            .filter(w -> w.getIsDeleted() == null || Boolean.FALSE.equals(w.getIsDeleted()))
            .orElseThrow(() -> new BusinessException(
                ErrorCodeConstants.PACS_WORK_ITEM_NOT_FOUND, "检查工单不存在"));
        if (!PacsWorkItemStatus.COMPLETED.getCode().equals(workItem.getStatusEnum())) {
            throw new BusinessException(
                ErrorCodeConstants.PACS_REPORT_WORKITEM_NOT_COMPLETED, "工单未完成，不可写报告");
        }
        PacsDiagnosticReport report = new PacsDiagnosticReport();
        report.setWorkItemId(workItem.getId());
        report.setPatientId(workItem.getPatientId());
        report.setEncounterId(workItem.getEncounterId());
        report.setOrganizationId(workItem.getOrganizationId());
        report.setTenantId(workItem.getTenantId());
        report.setCreatedBy(cmd.getOperatorId());
        report.setUpdatedBy(cmd.getOperatorId());
        report.initPreliminary();
        if (report.getIsDeleted() == null) {
            report.setIsDeleted(Boolean.FALSE);
        }
        return reportRepository.save(report);
    }

    /**
     * 保存草稿。
     *
     * @param cmd 命令
     * @return 报告
     */
    public PacsDiagnosticReport saveDraft(SavePacsReportDraftCommand cmd) {
        if (cmd == null || cmd.getReportId() == null) {
            throw new BusinessException(ErrorCodeConstants.PACS_REPORT_INVALID_ARGUMENT, "reportId 不能为空");
        }
        PacsDiagnosticReport report = this.load(cmd.getReportId());
        report.saveDraft(cmd.getFindings(), cmd.getConclusion(), cmd.getOperatorId());
        report.setUpdatedBy(cmd.getOperatorId());
        report.setUpdatedAt(LocalDateTime.now());
        PacsDiagnosticReport saved = reportRepository.save(report);
        PacsReportDraftSavedEvent event =
            new PacsReportDraftSavedEvent(saved.getId(), saved.getWorkItemId(), saved.getStatusEnum());
        event.setTenantId(saved.getTenantId());
        event.setUserId(cmd.getOperatorId());
        domainEventPublisher.publish(event);
        return saved;
    }

    /**
     * 提交待审。
     *
     * @param cmd 命令
     * @return 报告
     */
    public PacsDiagnosticReport submit(SubmitPacsReportCommand cmd) {
        if (cmd == null || cmd.getReportId() == null) {
            throw new BusinessException(ErrorCodeConstants.PACS_REPORT_INVALID_ARGUMENT, "reportId 不能为空");
        }
        PacsDiagnosticReport report = this.load(cmd.getReportId());
        report.submit(cmd.getOperatorId(), LocalDateTime.now());
        report.setUpdatedBy(cmd.getOperatorId());
        report.setUpdatedAt(LocalDateTime.now());
        PacsDiagnosticReport saved = reportRepository.save(report);
        PacsReportSubmittedEvent event = new PacsReportSubmittedEvent(
            saved.getId(), saved.getWorkItemId(), saved.getSubmittedAt(), saved.getStatusEnum());
        event.setTenantId(saved.getTenantId());
        event.setUserId(cmd.getOperatorId());
        domainEventPublisher.publish(event);
        return saved;
    }

    /**
     * 绑定 PDF 存储键。
     *
     * @param cmd 命令
     * @return 报告
     */
    public PacsDiagnosticReport bindPdf(BindPacsReportPdfCommand cmd) {
        if (cmd == null || cmd.getReportId() == null) {
            throw new BusinessException(ErrorCodeConstants.PACS_REPORT_INVALID_ARGUMENT, "reportId 不能为空");
        }
        PacsDiagnosticReport report = this.load(cmd.getReportId());
        report.bindPdf(cmd.getPdfStorageKey());
        report.setUpdatedBy(cmd.getOperatorId());
        report.setUpdatedAt(LocalDateTime.now());
        PacsDiagnosticReport saved = reportRepository.save(report);
        PacsReportPdfBoundEvent event =
            new PacsReportPdfBoundEvent(saved.getId(), saved.getWorkItemId(), saved.getPdfStorageKey());
        event.setTenantId(saved.getTenantId());
        event.setUserId(cmd.getOperatorId());
        domainEventPublisher.publish(event);
        return saved;
    }

    /**
     * 审核通过（供 REP-002）。
     *
     * @param cmd 命令
     * @return 报告
     */
    public PacsDiagnosticReport approve(ApprovePacsReportCommand cmd) {
        if (cmd == null || cmd.getReportId() == null) {
            throw new BusinessException(ErrorCodeConstants.PACS_REPORT_INVALID_ARGUMENT, "reportId 不能为空");
        }
        Long auditorId = cmd.getAuditorId();
        if (auditorId == null) {
            auditorId = cmd.getOperatorId();
        }
        PacsDiagnosticReport report = this.load(cmd.getReportId());
        report.approve(auditorId, LocalDateTime.now());
        report.setUpdatedBy(cmd.getOperatorId());
        report.setUpdatedAt(LocalDateTime.now());
        PacsDiagnosticReport saved = reportRepository.save(report);
        PacsReportApprovedEvent event = new PacsReportApprovedEvent(
            saved.getId(), saved.getWorkItemId(), saved.getIssuedAt(), saved.getStatusEnum());
        event.setTenantId(saved.getTenantId());
        event.setUserId(cmd.getOperatorId());
        domainEventPublisher.publish(event);
        return saved;
    }

    /**
     * 审核退回（供 REP-002）。
     *
     * @param cmd 命令
     * @return 报告
     */
    public PacsDiagnosticReport reject(RejectPacsReportCommand cmd) {
        if (cmd == null || cmd.getReportId() == null) {
            throw new BusinessException(ErrorCodeConstants.PACS_REPORT_INVALID_ARGUMENT, "reportId 不能为空");
        }
        PacsDiagnosticReport report = this.load(cmd.getReportId());
        report.reject();
        report.setUpdatedBy(cmd.getOperatorId());
        report.setUpdatedAt(LocalDateTime.now());
        PacsDiagnosticReport saved = reportRepository.save(report);
        PacsReportRejectedEvent event = new PacsReportRejectedEvent(
            saved.getId(), saved.getWorkItemId(), cmd.getReason(), saved.getStatusEnum());
        event.setTenantId(saved.getTenantId());
        event.setUserId(cmd.getOperatorId());
        domainEventPublisher.publish(event);
        return saved;
    }

    /**
     * 取消审核：final → 待审（供 REP-09）。
     *
     * @param cmd 命令（须含原因）
     * @return 回退后报告
     */
    public PacsDiagnosticReport cancelAudit(CancelPacsReportAuditCommand cmd) {
        if (cmd == null || cmd.getReportId() == null) {
            throw new BusinessException(ErrorCodeConstants.PACS_REPORT_INVALID_ARGUMENT, "reportId 不能为空");
        }
        if (StringUtils.isEmpty(cmd.getReason())) {
            throw new BusinessException(
                ErrorCodeConstants.PACS_REPORT_CANCEL_REASON_REQUIRED, "取消审核原因不能为空");
        }
        PacsDiagnosticReport report = this.load(cmd.getReportId());
        report.cancelAudit();
        report.setUpdatedBy(cmd.getOperatorId());
        report.setUpdatedAt(LocalDateTime.now());
        PacsDiagnosticReport saved = reportRepository.save(report);
        PacsReportAuditCancelledEvent event = new PacsReportAuditCancelledEvent(
            saved.getId(), saved.getWorkItemId(), cmd.getReason(), saved.getStatusEnum());
        event.setTenantId(saved.getTenantId());
        event.setUserId(cmd.getOperatorId());
        domainEventPublisher.publish(event);
        return saved;
    }

    private PacsDiagnosticReport load(Long reportId) {
        return reportRepository.findById(reportId)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.PACS_REPORT_NOT_FOUND, "检查报告不存在"));
    }
}
