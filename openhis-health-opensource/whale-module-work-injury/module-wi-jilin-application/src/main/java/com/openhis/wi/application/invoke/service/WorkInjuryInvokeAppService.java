/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.application.invoke.service;

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
}
