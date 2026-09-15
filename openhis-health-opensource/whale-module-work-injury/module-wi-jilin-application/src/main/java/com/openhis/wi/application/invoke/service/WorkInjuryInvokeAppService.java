/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.application.invoke.service;

import com.openhis.wi.application.recon.request.ConfirmWorkInjuryReconDailyRequest;
import com.openhis.wi.application.recon.request.ListWorkInjuryReconSettlementsRequest;
import com.openhis.wi.application.recon.request.SaveWorkInjuryReconDailyPlatformRequest;
import com.openhis.wi.application.recon.request.SummarizeWorkInjuryReconByDayRangeRequest;
import com.openhis.wi.application.recon.response.WorkInjuryReconDaySummaryResponse;
import com.openhis.wi.application.recon.response.WorkInjuryReconSettlementResponse;
import com.openhis.wi.application.recon.service.WorkInjuryReconAppService;
import com.openhis.wi.application.register.request.BuildWorkInjury2301PayloadRequest;
import com.openhis.wi.application.register.response.BuildWorkInjury2301PayloadResponse;
import com.openhis.wi.application.register.support.WorkInjury2301PayloadPreviewSupport;
import com.openhis.wi.domain.invoke.access.PrepareWorkInjuryInvokeInvoker;
import com.openhis.wi.domain.invoke.access.SubmitWorkInjuryInvokeInvoker;
import com.openhis.wi.domain.invoke.command.PrepareWorkInjuryInvokeRequest;
import com.openhis.wi.domain.invoke.command.SubmitWorkInjuryInvokeResultRequest;
import com.openhis.wi.domain.invoke.result.PrepareWorkInjuryInvokeResponse;
import com.openhis.wi.domain.invoke.result.SubmitWorkInjuryInvokeResultResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 工伤 Win invoke UI（POST /api/app/workInjuryInvoke/*）。
 *
 * <p>前端 prepare → OpenHIS.Win → submit；直调 domain invoke access 端口。</p>
 */
@Service
@RequiredArgsConstructor
public class WorkInjuryInvokeAppService {

    private final PrepareWorkInjuryInvokeInvoker prepareWorkInjuryInvokeInvoker;
    private final SubmitWorkInjuryInvokeInvoker submitWorkInjuryInvokeInvoker;
    private final WorkInjury2301PayloadPreviewSupport workInjury2301PayloadPreviewSupport;
    private final WorkInjuryReconAppService workInjuryReconAppService;

    public PrepareWorkInjuryInvokeResponse prepareInvoke(PrepareWorkInjuryInvokeRequest command) {
        return prepareWorkInjuryInvokeInvoker.invoke(command);
    }

    public SubmitWorkInjuryInvokeResultResponse submitInvokeResult(SubmitWorkInjuryInvokeResultRequest command) {
        return submitWorkInjuryInvokeInvoker.invoke(command);
    }

    /**
     * 【2301】按表单字段预览 Win input（不发 Win）。
     *
     * @param request encounterId 必填；其余为 2301 表单字段
     * @return 待下发业务体 input
     */
    public BuildWorkInjury2301PayloadResponse build2301Payload(BuildWorkInjury2301PayloadRequest request) {
        return workInjury2301PayloadPreviewSupport.preview(request);
    }

    /** 工伤对账：按日列出本地 WORK_INJURY 结算（含收银勾稽）。 */
    public List<WorkInjuryReconSettlementResponse> listReconSettlementsByDay(
            ListWorkInjuryReconSettlementsRequest request) {
        return workInjuryReconAppService.listSettlementsByDay(request);
    }

    /** 工伤对账：按日汇总 settlement ↔ payment（并持久化日结果覆盖层）。 */
    public List<WorkInjuryReconDaySummaryResponse> summarizeReconByDayRange(
            SummarizeWorkInjuryReconByDayRangeRequest request) {
        return workInjuryReconAppService.summarizeByDayRange(request);
    }

    /** 工伤对账：确认某日账平。 */
    public void confirmReconDailyBalanced(ConfirmWorkInjuryReconDailyRequest request) {
        workInjuryReconAppService.confirmDailyBalanced(request);
    }

    /** 工伤对账：保存某日平台 1320 金额。 */
    public void saveReconDailyPlatform(SaveWorkInjuryReconDailyPlatformRequest request) {
        workInjuryReconAppService.saveDailyPlatform(request);
    }
}
