/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.infrastructure.event;

import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 领域事件监听器
 * 扫描并注册所有标记了 @DomainEventHandler 的方法
 *
 * @author Whale Framework
 */
@Slf4j
@Component
public class DomainEventListener {

    /**
     * 监听所有领域事件
     * 通过 Spring 的 @EventListener 机制自动处理
     *
     * <p>注意：实际的事件处理逻辑应该在各个业务模块中通过 @DomainEventHandler 标记的方法实现</p>
     */
    @EventListener
    @Order
    public void handleDomainEvent(DomainEvent event) {
        log.debug("收到领域事件: {} (ID: {}, Aggregate: {})",
            event.getClass().getSimpleName(),
            event.getEventId(),
            event.getAggregateType());
    }
}
