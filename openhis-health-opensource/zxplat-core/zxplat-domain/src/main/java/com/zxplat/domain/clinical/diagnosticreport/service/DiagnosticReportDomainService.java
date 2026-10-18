/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.diagnosticreport.service;

import com.zxplat.domain.clinical.diagnosticreport.command.CreateForEncounterCommand;
import com.zxplat.domain.clinical.diagnosticreport.command.FinalizeMasterCommand;
import com.zxplat.domain.clinical.diagnosticreport.command.IncreaseDiagnosticReportPrintCountCommand;
import com.zxplat.domain.clinical.diagnosticreport.command.SaveMasterDraftCommand;
import com.zxplat.domain.clinical.diagnosticreport.entity.DiagnosticReport;
import com.zxplat.domain.clinical.diagnosticreport.repository.DiagnosticReportRepository;
import com.zxplat.domain.share.constants.DiagnosticReportStatusConstants;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.events.clinical.diagnosticreport.DiagnosticReportDraftSavedEvent;
import com.zxplat.domain.share.events.clinical.diagnosticreport.DiagnosticReportFinalizedEvent;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import com.zxplat.framework.core.domain.event.IDomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 临床总检报告写侧领域服务（DM-CLN-008）。
 */
@Service
@RequiredArgsConstructor
public class DiagnosticReportDomainService {

    private final DiagnosticReportRepository repository;
    private final IDomainEventPublisher domainEventPublisher;

    /**
     * 按就诊创建或返回已有主报告（BR-CLN-DR-01）。
     *
     * @param cmd 创建命令
     * @return 报告实体
     */
    public DiagnosticReport createOrGetForEncounter(CreateForEncounterCommand cmd) {
        // 前置：核心标识齐全
        if (cmd == null || cmd.getEncounterId() == null || cmd.getPatientId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.DIAGNOSTIC_REPORT_INVALID_ARGUMENT, "就诊与患者不能为空");
        }
        if (cmd.getTenantId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.DIAGNOSTIC_REPORT_INVALID_ARGUMENT, "租户标识不能为空");
        }
        Optional<DiagnosticReport> existing =
            repository.findByEncounterIdAndIsDeletedFalse(cmd.getEncounterId());
        if (existing.isPresent()) {
            return existing.get();
        }

        DiagnosticReport entity = new DiagnosticReport();
        entity.setTenantId(cmd.getTenantId());
        entity.setOrgId(cmd.getOrgId());
        entity.setEncounterId(cmd.getEncounterId());
        entity.setPatientId(cmd.getPatientId());
        entity.markPreliminary();
        if (entity.getIsDeleted() == null) {
            entity.setIsDeleted(Boolean.FALSE);
        }
        return repository.save(entity);
    }

    /**
     * 保存总检综述草稿（MAS-03）。
     *
     * @param cmd 草稿命令
     * @return 持久化后的实体
     */
    public DiagnosticReport saveMasterDraft(SaveMasterDraftCommand cmd) {
        // load → 状态校验 → update → 事件
        if (cmd == null || cmd.getEncounterId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.DIAGNOSTIC_REPORT_INVALID_ARGUMENT, "就诊标识不能为空");
        }
        if (cmd.getOperatorId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.DIAGNOSTIC_REPORT_INVALID_ARGUMENT, "操作人不能为空");
        }
        DiagnosticReport entity = repository.findByEncounterIdAndIsDeletedFalse(cmd.getEncounterId())
            .orElseThrow(() -> new BusinessException(
                ErrorCodeConstants.DIAGNOSTIC_REPORT_NOT_FOUND, "总检报告不存在，请先创建草稿行"));

        String summary = null;
        if (StringUtils.isNotEmpty(cmd.getSummaryText())) {
            summary = cmd.getSummaryText().trim();
        }
        String conclusion = null;
        if (StringUtils.isNotEmpty(cmd.getConclusionText())) {
            conclusion = cmd.getConclusionText().trim();
        }
        entity.saveDraft(summary, conclusion, cmd.getResultRefsJson());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setUpdatedBy(cmd.getOperatorId());

        DiagnosticReport saved = repository.save(entity);
        domainEventPublisher.publish(new DiagnosticReportDraftSavedEvent(saved.getId(), saved.getEncounterId()));
        return saved;
    }

    /**
     * 总检定稿（MAS-04；不写 index，由 AppService 编排）。
     *
     * @param cmd 定稿命令
     * @return 持久化后的实体
     */
    public DiagnosticReport finalizeMaster(FinalizeMasterCommand cmd) {
        // load → finalize → save → 事件
        if (cmd == null || cmd.getEncounterId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.DIAGNOSTIC_REPORT_INVALID_ARGUMENT, "就诊标识不能为空");
        }
        if (cmd.getOperatorId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.DIAGNOSTIC_REPORT_INVALID_ARGUMENT, "操作人不能为空");
        }
        DiagnosticReport entity = repository.findByEncounterIdAndIsDeletedFalse(cmd.getEncounterId())
            .orElseThrow(() -> new BusinessException(
                ErrorCodeConstants.DIAGNOSTIC_REPORT_NOT_FOUND, "总检报告不存在，请先保存综述草稿"));

        if (DiagnosticReportStatusConstants.STATUS_FINAL.equals(entity.getStatusEnum())) {
            throw new BusinessException(
                ErrorCodeConstants.MASTER_ALREADY_FINAL, "总检报告已定稿");
        }
        entity.finalizeReport(cmd.getOperatorId());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setUpdatedBy(cmd.getOperatorId());

        DiagnosticReport saved = repository.save(entity);
        domainEventPublisher.publish(new DiagnosticReportFinalizedEvent(
            saved.getId(), saved.getEncounterId(), saved.getPatientId(), saved.getStatusEnum()));
        return saved;
    }

    /**
     * 累加总检报告打印次数（REP-03）。
     *
     * @param cmd 命令
     * @return 更新后的实体
     */
    public DiagnosticReport increasePrintCount(IncreaseDiagnosticReportPrintCountCommand cmd) {
        if (cmd == null || cmd.getDiagnosticReportId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.DIAGNOSTIC_REPORT_INVALID_ARGUMENT, "总检报告标识不能为空");
        }
        if (cmd.getOperatorId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.DIAGNOSTIC_REPORT_INVALID_ARGUMENT, "操作人不能为空");
        }
        int increaseBy = cmd.getIncreaseBy() == null ? 1 : cmd.getIncreaseBy();
        DiagnosticReport entity = repository.findById(cmd.getDiagnosticReportId())
            .filter(row -> !Boolean.TRUE.equals(row.getIsDeleted()))
            .orElseThrow(() -> new BusinessException(
                ErrorCodeConstants.DIAGNOSTIC_REPORT_NOT_FOUND, "总检报告不存在"));
        if (!DiagnosticReportStatusConstants.STATUS_FINAL.equals(entity.getStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.REPORT_NOT_FINAL, "总检报告未定稿，不可记录打印");
        }
        entity.increasePrintCount(increaseBy);
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setUpdatedBy(cmd.getOperatorId());
        return repository.save(entity);
    }
}
