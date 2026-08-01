/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.recheck.service;

import com.openhis.health.application.peis.orchestration.PeisRegistrationOrchestrator;
import com.openhis.health.application.peis.recheck.mapper.PeisRecheckMapper;
import com.openhis.health.application.peis.recheck.request.CompleteRecheckRequest;
import com.openhis.health.application.peis.recheck.request.ConfirmRecheckItemsRequest;
import com.openhis.health.application.peis.recheck.request.CreateRecheckRequest;
import com.openhis.health.application.peis.recheck.request.SearchRecheckRequest;
import com.openhis.health.application.peis.recheck.response.CreateRecheckResponse;
import com.openhis.health.application.peis.recheck.response.SearchRecheckResponse;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.clinical.diagnosticreport.queries.model.DiagnosticReportModel;
import com.openhis.health.domain.clinical.diagnosticreport.service.DiagnosticReportQueryService;
import com.openhis.health.domain.peis.recheck.command.CompleteRecheckTaskCommand;
import com.openhis.health.domain.peis.recheck.command.CreateRecheckTaskCommand;
import com.openhis.health.domain.peis.recheck.command.StartRecheckTaskCommand;
import com.openhis.health.domain.peis.recheck.entity.PeisRecheckTask;
import com.openhis.health.domain.peis.recheck.queries.model.RecheckTaskRowModel;
import com.openhis.health.domain.peis.recheck.queries.query.RecheckTaskQuery;
import com.openhis.health.domain.peis.recheck.service.PeisRecheckDomainService;
import com.openhis.health.domain.peis.recheck.service.PeisRecheckQueryService;
import com.openhis.health.domain.share.constants.DiagnosticReportStatusConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.PeisRecheckTaskStatusConstants;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

/**
 * 复查管理应用服务（AS-PEIS-REC-001）：列表、申请、项目确认、完成。
 *
 * <p>Task 语义由 {@code peis_recheck_task} 承载（待 WKF Task 迁入）。</p>
 */
@Service
@RequiredArgsConstructor
public class PeisRecheckManageAppService {

    private final PeisRecheckDomainService peisRecheckDomainService;
    private final PeisRecheckQueryService peisRecheckQueryService;
    private final EncounterQueryService encounterQueryService;
    private final DiagnosticReportQueryService diagnosticReportQueryService;
    private final PeisRegistrationOrchestrator peisRegistrationOrchestrator;
    private final PeisRecheckMapper peisRecheckMapper;
    private final PageWindowGate pageWindowGate;
    private final CurrentPractitionerResolver currentPractitionerResolver;

    /**
     * 复查列表（IF-PEIS-REC-01）。
     *
     * @param request 检索条件
     * @return 分页复查行
     */
    public SearchRecheckResponse searchRecheck(SearchRecheckRequest request) {
        // Gate：分页窗口
        pageWindowGate.requirePositivePageWindow(
            request,
            ErrorCodeConstants.PEIS_RECHECK_INVALID_ARGUMENT,
            "复查列表分页参数非法");
        // Pre：领域查询
        RecheckTaskQuery query = peisRecheckMapper.toRecheckTaskQuery(request);
        Page<RecheckTaskRowModel> page = peisRecheckQueryService.searchRecheckTasks(query);
        // Asm
        return peisRecheckMapper.toSearchRecheckResponse(
            page, peisRecheckMapper.toRecheckRows(page.getContent()));
    }

    /**
     * 复查申请（IF-PEIS-REC-01）。
     *
     * @param request 申请请求
     * @return 任务标识
     */
    public CreateRecheckResponse createRecheck(CreateRecheckRequest request) {
        // Gate：参数
        if (request == null || request.getEncounterId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_RECHECK_INVALID_ARGUMENT, "就诊标识不能为空");
        }
        Long operatorId = resolveOperatorId(request.getOperatorId());
        // Pre：PE 就诊 + final DR（BR-REC-03）
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
            request.getEncounterId(),
            EncounterClass.PE.getCode(),
            ErrorCodeConstants.PEIS_RECHECK_INVALID_ARGUMENT,
            "仅体检就诊可申请复查");
        if (!EncounterStatus.COMPLETED.getCode().equals(encounter.getStatusEnum())
            && !EncounterStatus.IN_PROGRESS.getCode().equals(encounter.getStatusEnum())) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_RECHECK_ENCOUNTER_NOT_FINISHED, "原检未完成，不可申请复查");
        }
        DiagnosticReportModel report =
            diagnosticReportQueryService.getByEncounterId(request.getEncounterId()).orElse(null);
        if (report == null || !DiagnosticReportStatusConstants.STATUS_FINAL.equals(report.getStatusEnum())) {
            throw new BusinessException(
                ErrorCodeConstants.REPORT_NOT_FINAL, "须已总检定稿后才可申请复查");
        }
        if (peisRecheckQueryService.hasOpenRecheckTask(request.getEncounterId())) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_RECHECK_ALREADY_OPEN, "已有未关闭复查任务");
        }
        // Cmd：创建 Task
        CreateRecheckTaskCommand cmd = new CreateRecheckTaskCommand();
        cmd.setEncounterId(request.getEncounterId());
        cmd.setPatientId(encounter.getPatientId());
        cmd.setOrgId(encounter.getOrganizationId());
        cmd.setTenantId(TenantUtils.getCurrentTenantId());
        cmd.setReason(request.getReason());
        cmd.setOperatorId(operatorId);
        PeisRecheckTask task = peisRecheckDomainService.createRecheckTask(cmd);
        // Asm
        CreateRecheckResponse response = new CreateRecheckResponse();
        response.setTaskId(task.getId());
        response.setEncounterId(task.getEncounterId());
        return response;
    }

    /**
     * 复查项目确认（IF-PEIS-REC-02）。
     *
     * @param request 确认请求
     */
    public void confirmRecheckItems(ConfirmRecheckItemsRequest request) {
        // Gate：任务主键
        if (request == null || request.getTaskId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_RECHECK_INVALID_ARGUMENT, "复查任务不能为空");
        }
        Long operatorId = resolveOperatorId(request.getOperatorId());
        // Pre：任务存在且未 completed
        RecheckTaskRowModel task = peisRecheckQueryService.getById(request.getTaskId()).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.PEIS_RECHECK_NOT_FOUND, "复查任务不存在"));
        if (PeisRecheckTaskStatusConstants.COMPLETED.equals(task.getStatusCode())) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_RECHECK_INVALID_STATUS, "复查任务已关闭");
        }
        // Cmd：追加复查 SR + 任务 in-progress
        peisRegistrationOrchestrator.addRecheckServiceRequests(
            task.getEncounterId(), request.getActivityIds(), operatorId);
        StartRecheckTaskCommand startCmd = new StartRecheckTaskCommand();
        startCmd.setTaskId(request.getTaskId());
        startCmd.setOperatorId(operatorId);
        peisRecheckDomainService.startTask(startCmd);
        // Asm：无出参（计费提示由前端引导 health 收费）
    }

    /**
     * 复查完成（IF-PEIS-REC-03）。
     *
     * @param request 完成请求
     */
    public void completeRecheck(CompleteRecheckRequest request) {
        // Gate：任务与报告标识
        if (request == null || request.getTaskId() == null || request.getDiagnosticReportId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_RECHECK_INVALID_ARGUMENT, "任务与报告标识不能为空");
        }
        Long operatorId = resolveOperatorId(request.getOperatorId());
        // Pre：关联 DR 须 final
        DiagnosticReportModel report = diagnosticReportQueryService.mustGetById(request.getDiagnosticReportId());
        if (!DiagnosticReportStatusConstants.STATUS_FINAL.equals(report.getStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.REPORT_NOT_FINAL, "复查报告须已定稿");
        }
        // Cmd：关闭 Task 并关联 DR
        CompleteRecheckTaskCommand cmd = new CompleteRecheckTaskCommand();
        cmd.setTaskId(request.getTaskId());
        cmd.setDiagnosticReportId(request.getDiagnosticReportId());
        cmd.setOperatorId(operatorId);
        peisRecheckDomainService.completeTask(cmd);
        // Asm：无出参
    }

    /**
     * 解析操作人：请求显式传入优先，否则取当前登录医师。
     *
     * @param requestOperatorId 请求中的操作人
     * @return 非空操作人主键
     */
    private Long resolveOperatorId(Long requestOperatorId) {
        if (requestOperatorId != null) {
            return requestOperatorId;
        }
        return currentPractitionerResolver.requirePractitionerId(
            ErrorCodeConstants.PEIS_RECHECK_INVALID_ARGUMENT, "请先登录后再操作");
    }
}
