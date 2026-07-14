/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.integration.einvoice;


import com.openhis.health.infrastructure.integration.einvoice.BossElectronicInvoiceExtension;
import com.openhis.whale.einvoice.boss.domain.issue.access.EinvoiceIssueOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * health 侧博思电子票据扩展点实现（bridge 层入口）。
 *
 * <p>将支付后开具、作废后冲红等 domain 事件转为对 {@link EinvoiceIssueOrchestrator} 的调用。
 * 不直接依赖 application 源码包，仅注入 domain 端口（由 application 提供 {@code @Primary} 实现）。</p>
 *
 * <p>装配条件：模块开关 {@code openhis.modules.einvoice-boss.enabled=true} 且
 * 容器中存在 {@code EinvoiceIssueOrchestratorImpl} Bean。</p>
 */
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnBean(name = "EinvoiceIssueOrchestratorImpl")
@ConditionalOnProperty(name = "openhis.modules.einvoice-boss.enabled", havingValue = "true")
public class BossElectronicInvoiceExtensionImpl implements BossElectronicInvoiceExtension {

    private final EinvoiceIssueOrchestrator issueOrchestrator;

    /**
     * 模块已启用（与 {@link ConditionalOnProperty} 一致，恒为 true）。
     *
     * @return {@code true}
     */
    @Override
    public boolean isEnabled() {
        return true;
    }

    /**
     * 票据 DRAFT 后异步调度博思开具（支付事务提交后触发）。
     *
     * <p>经 {@code @Async} 线程池执行，不阻塞支付主事务。</p>
     *
     * @param invoiceId 新创建的 health 票据主键；null 时忽略
     */
    @Override
    @Async
    public void scheduleIssueAfterDraft(Long invoiceId) {
        if (invoiceId == null) {
            return;
        }
        log.debug("BossElectronicInvoiceExtension schedule issue, invoiceId={}", invoiceId);
        issueOrchestrator.scheduleIssueAsync(invoiceId);
    }

    /**
     * 票据作废/退费后调度博思冲红（同步入口，内部冲红可走异步桥）。
     *
     * <p>先 {@link EinvoiceIssueOrchestrator#canWriteOff(Long)} 过滤无蓝票场景；
     * reason 为空时默认「退费冲红」。</p>
     *
     * @param invoiceId 作废的票据主键
     * @param reason 冲红原因（可空）
     */
    @Override
    public void scheduleWriteOffAfterCancel(Long invoiceId, String reason) {
        if (invoiceId == null) {
            return;
        }
        if (!issueOrchestrator.canWriteOff(invoiceId)) {
            log.debug("BossElectronicInvoiceExtension skip writeOff, invoiceId={}", invoiceId);
            return;
        }
        String writeOffReason = StringUtils.hasText(reason) ? reason : "退费冲红";
        log.info("BossElectronicInvoiceExtension schedule writeOff, invoiceId={}", invoiceId);
        issueOrchestrator.scheduleWriteOffAsync(invoiceId, writeOffReason);
    }
}
