/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.application.register.mapper;

import com.openhis.wi.application.register.request.BuildWorkInjury2301PayloadRequest;
import com.openhis.wi.application.register.response.BuildWorkInjury2301PayloadResponse;
import com.openhis.wi.domain.register.access.Build2301PayloadInvoker;
import com.openhis.wi.domain.register.command.Build2301PayloadCommand;

import java.util.Map;

/** 2301 预览 Request ↔ domain Command。 */
public final class WorkInjury2301PayloadMapper {

    private WorkInjury2301PayloadMapper() {
    }

    /**
     * 预览请求 → domain {@link Build2301PayloadCommand}。
     *
     * @param request application 请求
     * @return domain 命令
     */
    public static Build2301PayloadCommand toCommand(BuildWorkInjury2301PayloadRequest request) {
        if (request == null) {
            return new Build2301PayloadCommand();
        }
        return new Build2301PayloadCommand()
                .setEncounterId(request.getEncounterId())
                .setContractId(request.getContractId())
                .setApproveType(request.getApproveType())
                .setStartDate(request.getStartDate())
                .setEndDate(request.getEndDate())
                .setTerminReason(request.getTerminReason())
                .setInjureAffirmId(request.getInjureAffirmId())
                .setOrgCode(request.getOrgCode())
                .setOrgName(request.getOrgName());
    }

    /**
     * Win input Map → 预览响应。
     *
     * @param input {@link Build2301PayloadInvoker#invoke} 返回的业务体
     * @return 含 input 字段的响应
     */
    public static BuildWorkInjury2301PayloadResponse toResponse(Map<String, Object> input) {
        return new BuildWorkInjury2301PayloadResponse().setInput(input);
    }
}
