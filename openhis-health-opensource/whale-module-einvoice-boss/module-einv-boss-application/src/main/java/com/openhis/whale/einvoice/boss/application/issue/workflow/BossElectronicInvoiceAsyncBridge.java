/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.application.issue.workflow;

import com.openhis.whale.einvoice.boss.application.internal.issue.BossWriteOffService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 博思编排异步桥：将冲红调度放到独立 Spring Bean，保证 {@code @Async} 生效。
 *
 * <p>Spring AOP 同类自调用不会走代理；{@link EinvoiceIssueOrchestratorImpl} 若直接
 * {@code @Async} 冲红会退化为同步，故拆出本类。</p>
 */
@Service
@RequiredArgsConstructor
public class BossElectronicInvoiceAsyncBridge {

    private final BossWriteOffService writeOffService;

    /**
     * 异步冲红（作废事件 afterCommit 触发）。
     *
     * @param invoiceId 票据主键
     * @param reason 冲红原因
     */
    @Async
    public void scheduleWriteOffAsync(Long invoiceId, String reason) {
        writeOffService.writeOff(invoiceId, reason);
    }
}
