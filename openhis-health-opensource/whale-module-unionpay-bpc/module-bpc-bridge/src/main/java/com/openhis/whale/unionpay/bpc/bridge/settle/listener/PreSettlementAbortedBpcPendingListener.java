/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.unionpay.bpc.bridge.settle.listener;

import com.openhis.health.domain.share.events.financial.paymentreconciliation.PreSettlementAbortedEvent;
import com.openhis.whale.unionpay.bpc.domain.scan.command.AbandonPendingScanCommand;
import com.openhis.whale.unionpay.bpc.domain.service.BpcSettleDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 支付草稿作废后关闭同支付下银联 PENDING，避免 CANCELLED+PENDING 悬挂。
 *
 * <p>DomainEventPublisher 在 beforeCommit 同步发布；此处用 {@link EventListener} 同线程收口，保留租户上下文。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.unionpay-bpc.enabled", havingValue = "true")
public class PreSettlementAbortedBpcPendingListener {

    private final BpcSettleDomainService settleDomainService;

    @EventListener
    public void onPreSettlementAborted(PreSettlementAbortedEvent event) {
        if (event == null || event.getAggregateId() == null) {
            return;
        }
        try {
            int count = settleDomainService.abandonPendingScan(
                new AbandonPendingScanCommand()
                    .setPaymentId(event.getAggregateId())
                    .setRespCode("AB")
                    .setReason("payment draft cancelled"))
                .getAbandonedCount();
            if (count > 0) {
                log.info("BPC PENDING closed after draft abort paymentId={} count={}",
                    event.getAggregateId(), count);
            }
        } catch (Exception ex) {
            // 关单失败不阻断草稿作废；由下次 prepare / 运维收口。
            log.warn("BPC PENDING abandon after draft abort failed paymentId={}: {}",
                event.getAggregateId(), ex.getMessage());
        }
    }
}
