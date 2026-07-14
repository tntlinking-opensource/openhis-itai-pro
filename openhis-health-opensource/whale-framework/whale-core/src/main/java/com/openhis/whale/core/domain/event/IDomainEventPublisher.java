/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.domain.event;

/**
 * 领域事件发布器接口
 * 参考 ABP.io 的 IDomainEventPublisher
 *
 * <p>用于发布领域事件，支持事务内发布和事务后发布</p>
 *
 * @author Whale Framework
 */
public interface IDomainEventPublisher {

    /**
     * 发布领域事件
     *
     * <p>事件会在当前事务提交后发布（异步）</p>
     *
     * @param event 领域事件
     */
    void publish(DomainEvent event);

    /**
     * 发布多个领域事件
     *
     * @param events 领域事件数组
     */
    void publish(DomainEvent... events);

    /**
     * 发布应用事件
     *
     * @param event 应用事件
     */
    void publish(ApplicationEvent event);
}
