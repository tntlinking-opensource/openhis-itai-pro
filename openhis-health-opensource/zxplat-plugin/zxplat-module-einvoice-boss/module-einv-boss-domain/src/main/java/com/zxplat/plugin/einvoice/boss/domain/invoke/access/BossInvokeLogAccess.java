/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.domain.invoke.access;

import com.zxplat.plugin.einvoice.boss.domain.invoke.result.BossInvokeResult;
import com.zxplat.plugin.einvoice.boss.domain.issue.entity.EinvBossIssueTask;

/**
 * 博思 HTTP 调用日志写入端口（application 实现，bridge 出站支撑注入）。
 */
public interface BossInvokeLogAccess {

    void write(
            EinvBossIssueTask task,
            String serviceId,
            String busNo,
            BossInvokeResult invokeResult,
            int durationMs,
            String requestJson,
            String responseBody);

    void writeWithoutTask(
            Long tenantId,
            String serviceId,
            String busNo,
            BossInvokeResult invokeResult,
            int durationMs,
            String requestJson,
            String responseBody);
}
