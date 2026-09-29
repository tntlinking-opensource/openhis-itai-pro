/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.adapter.pacs.inbound;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.health.domain.pacs.integrationlog.service.PacsIntegrationLogDomainService;
import lombok.RequiredArgsConstructor;

/**
 * 报告 final 回传薄编排（国基 Mock：仅记日志；REP Domain 就绪后再接 final+投影）。
 */
@RequiredArgsConstructor
public class PacsAdapterReportFinalizeCommandHandler {

    private final PacsIntegrationLogDomainService integrationLogDomainService;
    private final ObjectMapper objectMapper;

    /**
     * 接收 Mock 回传。
     *
     * @param command 回传命令
     */
    public void handle(PacsReportFinalizeCommand command) {
        String requestJson = this.toJson(command);
        boolean ok = command != null && command.getWorkItemId() != null;

        Long workItemId = null;
        Long operatorId = null;
        if (command != null) {
            workItemId = command.getWorkItemId();
            operatorId = command.getOperatorId();
        }
        String outcome;
        String responsePayload = null;
        String errorMessage = null;
        if (ok) {
            outcome = PacsIntegrationLogDomainService.OUTCOME_SUCCESS;
            String pdfPath = "";
            if (command.getPdfPath() != null) {
                pdfPath = command.getPdfPath();
            }
            responsePayload = "mock-finalize-accepted pdfPath=" + pdfPath;
        } else {
            outcome = PacsIntegrationLogDomainService.OUTCOME_FAILURE;
            errorMessage = "workItemId 不能为空";
        }
        integrationLogDomainService.record(
            PacsIntegrationLogDomainService.DIRECTION_INBOUND,
            PacsIntegrationLogDomainService.ACTION_REPORT_FINALIZE,
            "PacsWorkItem",
            workItemId,
            outcome,
            requestJson,
            responsePayload,
            errorMessage,
            operatorId);
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
