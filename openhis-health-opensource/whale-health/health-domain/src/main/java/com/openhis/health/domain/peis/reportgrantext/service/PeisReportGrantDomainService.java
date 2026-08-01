/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.peis.reportgrantext.service;

import com.openhis.health.domain.peis.reportgrantext.command.MarkGrantPrintedCommand;
import com.openhis.health.domain.peis.reportgrantext.command.RegisterGrantCommand;
import com.openhis.health.domain.peis.reportgrantext.command.SignGrantCommand;
import com.openhis.health.domain.peis.reportgrantext.entity.PeisReportGrantExt;
import com.openhis.health.domain.peis.reportgrantext.repository.PeisReportGrantExtRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.PeisGrantStatusConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 报告发放扩展写侧领域服务（DM-PEIS-005）。
 */
@Service
@RequiredArgsConstructor
public class PeisReportGrantDomainService {

    private final PeisReportGrantExtRepository repository;

    /**
     * 登记发放（INSERT peis_report_grant_ext，幂等由 PK 保证）。
     *
     * @param cmd 登记命令
     * @return 持久化实体
     */
    public PeisReportGrantExt registerGrant(RegisterGrantCommand cmd) {
        // 前置校验
        if (cmd == null || cmd.getDiagnosticReportId() == null || cmd.getOperatorId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_GRANT_INVALID_ARGUMENT, "报告与操作人不能为空");
        }
        // 防重复登记
        if (repository.findByDiagnosticReportIdAndIsDeletedFalse(cmd.getDiagnosticReportId()).isPresent()) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_GRANT_ALREADY_REGISTERED, "该报告已登记发放");
        }
        LocalDateTime now = LocalDateTime.now();
        PeisReportGrantExt entity = new PeisReportGrantExt();
        entity.setDiagnosticReportId(cmd.getDiagnosticReportId());
        entity.markNew();
        entity.setTenantId(cmd.getTenantId());
        entity.setOrgId(cmd.getOrgId());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setCreatedBy(cmd.getOperatorId());
        entity.setIsDeleted(Boolean.FALSE);
        entity.registerGrant(cmd.getOperatorId(), now);
        return repository.save(entity);
    }

    /**
     * 签收报告。
     *
     * @param cmd 签收命令
     * @return 更新后实体
     */
    public PeisReportGrantExt signGrant(SignGrantCommand cmd) {
        if (cmd == null || cmd.getDiagnosticReportId() == null || cmd.getOperatorId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_GRANT_INVALID_ARGUMENT, "报告与操作人不能为空");
        }
        PeisReportGrantExt entity = requireGrant(cmd.getDiagnosticReportId());
        LocalDateTime now = LocalDateTime.now();
        entity.signGrant(cmd.getRecipientName(), cmd.getOperatorId(), now);
        return repository.save(entity);
    }

    /**
     * 标记已打印（团检批量打印 REP-06）。
     *
     * @param cmd 打印命令
     * @return 更新后实体；不存在则新建 printed 行
     */
    public PeisReportGrantExt markPrinted(MarkGrantPrintedCommand cmd) {
        if (cmd == null || cmd.getDiagnosticReportId() == null || cmd.getOperatorId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_GRANT_INVALID_ARGUMENT, "报告与操作人不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        PeisReportGrantExt entity =
            repository.findByDiagnosticReportIdAndIsDeletedFalse(cmd.getDiagnosticReportId()).orElse(null);
        if (entity == null) {
            entity = new PeisReportGrantExt();
            entity.setDiagnosticReportId(cmd.getDiagnosticReportId());
            entity.markNew();
            entity.setCreatedAt(now);
            entity.setCreatedBy(cmd.getOperatorId());
            entity.setIsDeleted(Boolean.FALSE);
            entity.setGrantStatusEnum(PeisGrantStatusConstants.PENDING);
        }
        entity.markPrinted(cmd.getOperatorId(), now);
        return repository.save(entity);
    }

    /**
     * 标记已邮寄（REP-07 桩）。
     *
     * @param cmd 签收命令（复用 diagnosticReportId + operatorId）
     * @return 更新后实体
     */
    public PeisReportGrantExt markMailed(SignGrantCommand cmd) {
        if (cmd == null || cmd.getDiagnosticReportId() == null || cmd.getOperatorId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_GRANT_INVALID_ARGUMENT, "报告与操作人不能为空");
        }
        PeisReportGrantExt entity = requireGrant(cmd.getDiagnosticReportId());
        LocalDateTime now = LocalDateTime.now();
        entity.markMailed(cmd.getOperatorId(), now);
        return repository.save(entity);
    }

    private PeisReportGrantExt requireGrant(Long diagnosticReportId) {
        return repository.findByDiagnosticReportIdAndIsDeletedFalse(diagnosticReportId).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.PEIS_GRANT_NOT_FOUND, "发放记录不存在"));
    }
}
