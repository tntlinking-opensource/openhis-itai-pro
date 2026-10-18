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
 * 报告路径回传 HIS 薄编排（IF-PACS-INT-06 · 国基 Mock）。
 */
@RequiredArgsConstructor
public class PacsNotifyReportPathCommandHandler {

    private final PacsOutboundPort outboundPort;
    private final PacsIntegrationLogDomainService integrationLogDomainService;
    private final ObjectMapper objectMapper;

    /**
     * 回传报告路径。
     *
     * @param command 回传命令
     * @return 是否成功
     */
    public boolean handle(PacsNotifyReportPathCommand command) {
        String requestJson = this.toJson(command);
        PacsOutboundResult result = outboundPort.notifyReportPath(command);
        boolean ok = result != null && result.isSuccess();

        Long reportId = null;
        Long operatorId = null;
        if (command != null) {
            reportId = command.getReportId();
            operatorId = command.getOperatorId();
        }
        String outcome;
        if (ok) {
            outcome = PacsIntegrationLogDomainService.OUTCOME_SUCCESS;
        } else {
            outcome = PacsIntegrationLogDomainService.OUTCOME_FAILURE;
        }
        String responsePayload = null;
        String errorMessage = null;
        if (ok) {
            responsePayload = result.getPayload();
        } else {
            if (result != null) {
                errorMessage = result.getMessage();
            } else {
                errorMessage = "外发失败";
            }
        }
        integrationLogDomainService.record(
            PacsIntegrationLogDomainService.DIRECTION_OUTBOUND,
            PacsIntegrationLogDomainService.ACTION_REPORT_PATH,
            "PacsDiagnosticReport",
            reportId,
            outcome,
            requestJson,
            responsePayload,
            errorMessage,
            operatorId);
        return ok;
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
