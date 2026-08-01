/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.bridge.push.schedule;

import com.openhis.whale.crb.bridge.push.workflow.CrbPushOrchestrator;
import com.openhis.whale.crb.domain.config.CrbProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 到期推送任务定时 drain（bridge 层调度，application 不可注入 Orchestrator）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrbPushDrainScheduler {

    private final CrbPushOrchestrator pushOrchestrator;
    private final CrbProperties crbProperties;

    /**
     * 按配置租户扫描到期 PENDING 任务并触发 Worker。
     */
    @Scheduled(fixedDelayString = "${openhis.crb.push.drain-interval-ms:30000}")
    public void drainDueTasks() {
        Long tenantId = crbProperties.getPush().getDrainTenantId();
        // 未配置租户则跳过，避免多租户误扫
        if (tenantId == null) {
            return;
        }
        if (log.isDebugEnabled()) {
            log.debug("CRB push drain start tenantId={}", tenantId);
        }
        pushOrchestrator.drainDueTasks(tenantId);
    }
}
