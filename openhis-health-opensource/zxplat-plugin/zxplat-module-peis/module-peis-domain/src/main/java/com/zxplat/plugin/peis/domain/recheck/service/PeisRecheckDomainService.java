/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.domain.recheck.service;

import com.zxplat.plugin.peis.domain.recheck.command.CompleteRecheckTaskCommand;
import com.zxplat.plugin.peis.domain.recheck.command.CreateRecheckTaskCommand;
import com.zxplat.plugin.peis.domain.recheck.command.StartRecheckTaskCommand;
import com.zxplat.plugin.peis.domain.recheck.entity.PeisRecheckTask;
import com.zxplat.plugin.peis.domain.recheck.repository.PeisRecheckTaskRepository;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.PeisRecheckTaskStatusConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 复查任务写侧领域服务（Peis 04 §4.5 方案 A，peis_recheck_task 承载 Task 语义）。
 */
@Service
@RequiredArgsConstructor
public class PeisRecheckDomainService {

    private static final List<String> OPEN_STATUSES = Arrays.asList(
        PeisRecheckTaskStatusConstants.REQUESTED,
        PeisRecheckTaskStatusConstants.IN_PROGRESS);

    private final PeisRecheckTaskRepository repository;

    /**
     * 创建复查任务（requested）。
     *
     * @param cmd 创建命令
     * @return 持久化任务
     */
    public PeisRecheckTask createRecheckTask(CreateRecheckTaskCommand cmd) {
        if (cmd == null || cmd.getEncounterId() == null || cmd.getOperatorId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_RECHECK_INVALID_ARGUMENT, "就诊与操作人不能为空");
        }
        if (StringUtils.isEmpty(cmd.getReason())) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_RECHECK_INVALID_ARGUMENT, "复查原因不能为空");
        }
        if (hasOpenRecheck(cmd.getEncounterId())) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_RECHECK_ALREADY_OPEN, "该就诊已有未关闭复查任务");
        }
        LocalDateTime now = LocalDateTime.now();
        PeisRecheckTask task = new PeisRecheckTask();
        task.setEncounterId(cmd.getEncounterId());
        task.setPatientId(cmd.getPatientId());
        task.setOrgId(cmd.getOrgId());
        task.setTenantId(cmd.getTenantId());
        task.setReason(cmd.getReason().trim());
        task.setStatusEnum(PeisRecheckTaskStatusConstants.REQUESTED);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        task.setCreatedBy(cmd.getOperatorId());
        task.setUpdatedBy(cmd.getOperatorId());
        return repository.save(task);
    }

    /**
     * 启动复查任务（requested → in-progress）。
     *
     * @param cmd 启动命令
     * @return 更新后任务
     */
    public PeisRecheckTask startTask(StartRecheckTaskCommand cmd) {
        if (cmd == null || cmd.getTaskId() == null || cmd.getOperatorId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_RECHECK_INVALID_ARGUMENT, "任务与操作人不能为空");
        }
        PeisRecheckTask task = requireTask(cmd.getTaskId());
        if (!PeisRecheckTaskStatusConstants.REQUESTED.equals(task.getStatusEnum())) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_RECHECK_INVALID_STATUS, "仅已申请任务可确认复查项目");
        }
        task.setStatusEnum(PeisRecheckTaskStatusConstants.IN_PROGRESS);
        task.setUpdatedAt(LocalDateTime.now());
        task.setUpdatedBy(cmd.getOperatorId());
        return repository.save(task);
    }

    /**
     * 完成复查任务（→ completed，关联 DR）。
     *
     * @param cmd 完成命令
     * @return 更新后任务
     */
    public PeisRecheckTask completeTask(CompleteRecheckTaskCommand cmd) {
        if (cmd == null || cmd.getTaskId() == null || cmd.getDiagnosticReportId() == null
            || cmd.getOperatorId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_RECHECK_INVALID_ARGUMENT, "任务、报告与操作人不能为空");
        }
        PeisRecheckTask task = requireTask(cmd.getTaskId());
        if (PeisRecheckTaskStatusConstants.COMPLETED.equals(task.getStatusEnum())) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_RECHECK_INVALID_STATUS, "复查任务已完成");
        }
        LocalDateTime now = LocalDateTime.now();
        task.setStatusEnum(PeisRecheckTaskStatusConstants.COMPLETED);
        task.setDiagnosticReportId(cmd.getDiagnosticReportId());
        task.setCompletedAt(now);
        task.setCompletedBy(cmd.getOperatorId());
        task.setUpdatedAt(now);
        task.setUpdatedBy(cmd.getOperatorId());
        return repository.save(task);
    }

    /**
     * 是否存在未关闭复查任务。
     *
     * @param encounterId 就诊主键
     * @return 是否存在 open task
     */
    public boolean hasOpenRecheck(Long encounterId) {
        if (encounterId == null) {
            return false;
        }
        return repository.existsByEncounterIdAndStatusEnumInAndIsDeletedFalse(encounterId, OPEN_STATUSES);
    }

    private PeisRecheckTask requireTask(Long taskId) {
        return repository.findById(taskId).filter(t -> !Boolean.TRUE.equals(t.getIsDeleted())).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.PEIS_RECHECK_NOT_FOUND, "复查任务不存在"));
    }
}
