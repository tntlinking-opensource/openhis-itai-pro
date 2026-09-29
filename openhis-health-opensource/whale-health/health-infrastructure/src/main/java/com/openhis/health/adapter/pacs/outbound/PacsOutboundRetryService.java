/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.adapter.pacs.outbound;

import com.openhis.health.domain.pacs.integrationlog.entity.PacsIntegrationLog;
import com.openhis.health.domain.pacs.integrationlog.service.PacsIntegrationLogDomainService;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 外发失败补发（INT-05 · 序号 18）。
 */
@RequiredArgsConstructor
public class PacsOutboundRetryService {

    private final PacsSendApplyCommandHandler sendApplyCommandHandler;
    private final PacsIntegrationLogDomainService integrationLogDomainService;

    /**
     * 重放最近失败的 sendApply 日志（按 entityId）。
     *
     * @param operatorId 操作人
     * @return 重试结果列表
     */
    public List<PacsOutboundResult> retryFailedSendApply(Long operatorId) {
        List<PacsIntegrationLog> failed = integrationLogDomainService.listFailedOutbound(
            PacsIntegrationLogDomainService.ACTION_SEND_APPLY);
        List<PacsOutboundResult> results = new ArrayList<>();
        for (PacsIntegrationLog log : failed) {
            if (log.getEntityId() == null) {
                continue;
            }
            PacsSendApplyCommand cmd = new PacsSendApplyCommand();
            cmd.setWorkItemId(log.getEntityId());
            cmd.setOperatorId(operatorId);
            PacsOutboundResult result = sendApplyCommandHandler.handle(cmd);
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
                PacsIntegrationLogDomainService.ACTION_RETRY,
                "PacsWorkItem",
                log.getEntityId(),
                outcome,
                "retry-of-log-" + log.getId(),
                result.getPayload(),
                errorMessage,
                operatorId);
            results.add(result);
        }
        return results;
    }
}
