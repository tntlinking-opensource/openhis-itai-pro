/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.bridge.adapter.outbound;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zxplat.plugin.pacs.domain.integrationlog.service.PacsIntegrationLogDomainService;
import lombok.RequiredArgsConstructor;

/**
 * 外发编排：调 Mock 端口并写集成日志（序号 17）。
 */
@RequiredArgsConstructor
public class PacsSendApplyCommandHandler {

    private static final String ENTITY_TYPE = "PacsWorkItem";

    private final PacsOutboundPort outboundPort;
    private final PacsIntegrationLogDomainService integrationLogDomainService;
    private final ObjectMapper objectMapper;

    /**
     * 外发检查申请并记日志。
     *
     * @param command 外发命令
     * @return 外发结果
     */
    public PacsOutboundResult handle(PacsSendApplyCommand command) {
        PacsOutboundResult result = outboundPort.sendApply(command);
        String requestJson = this.toJson(command);

        Long workItemId = null;
        Long operatorId = null;
        if (command != null) {
            workItemId = command.getWorkItemId();
            operatorId = command.getOperatorId();
        }
        String outcome;
        if (result.isSuccess()) {
            outcome = PacsIntegrationLogDomainService.OUTCOME_SUCCESS;
        } else {
            outcome = PacsIntegrationLogDomainService.OUTCOME_FAILURE;
        }
        String errorMessage = null;
        if (!result.isSuccess()) {
            errorMessage = result.getMessage();
        }
        integrationLogDomainService.record(
            PacsIntegrationLogDomainService.DIRECTION_OUTBOUND,
            PacsIntegrationLogDomainService.ACTION_SEND_APPLY,
            ENTITY_TYPE,
            workItemId,
            outcome,
            requestJson,
            result.getPayload(),
            errorMessage,
            operatorId);
        return result;
    }

    /**
     * 序列化请求体；失败时退回 toString。
     *
     * @param value 命令对象
     * @return JSON 或原文
     */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
        }
    }
}
