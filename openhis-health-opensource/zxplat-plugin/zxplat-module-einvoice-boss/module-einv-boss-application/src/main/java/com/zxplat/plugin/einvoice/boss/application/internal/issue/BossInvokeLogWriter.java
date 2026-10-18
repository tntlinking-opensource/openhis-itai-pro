/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.application.internal.issue;

import com.zxplat.plugin.einvoice.boss.domain.invoke.access.BossInvokeLogAccess;
import com.zxplat.plugin.einvoice.boss.domain.invoke.result.BossInvokeResult;
import com.zxplat.plugin.einvoice.boss.domain.issue.entity.EinvBossIssueTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 博思 invoke 日志写入门面（脱敏后委托 {@link BossInvokeLogPersistService}）。
 */
@Component
@RequiredArgsConstructor
public class BossInvokeLogWriter implements BossInvokeLogAccess {

    private final BossInvokeLogPersistService persistAppService;

    @Override
    public void write(
            EinvBossIssueTask task,
            String serviceId,
            String busNo,
            BossInvokeResult invokeResult,
            int durationMs,
            String requestJson,
            String responseBody) {
        persistAppService.persist(
                task.getTenantId(), task.getId(), serviceId, busNo, invokeResult, durationMs, requestJson, responseBody);
    }

    @Override
    public void writeWithoutTask(
            Long tenantId,
            String serviceId,
            String busNo,
            BossInvokeResult invokeResult,
            int durationMs,
            String requestJson,
            String responseBody) {
        persistAppService.persist(tenantId, null, serviceId, busNo, invokeResult, durationMs, requestJson, responseBody);
    }
}
