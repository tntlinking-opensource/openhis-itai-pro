/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.infrastructure.event;

import com.zxplat.framework.core.domain.event.ApplicationEvent;
import com.zxplat.framework.core.domain.event.DomainEvent;
import com.zxplat.framework.core.domain.event.IDomainEventPublisher;
import com.zxplat.framework.core.infrastructure.security.ICurrentUser;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 领域事件发布器实现
 * 参考 ABP.io 的 DomainEventPublisher
 *
 * <p>支持事务内发布和事务后发布</p>
 *
 * @author OpenHIS Contributors
 */
@Slf4j
@RequiredArgsConstructor
public class DomainEventPublisher implements IDomainEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final ICurrentUser currentUser;

    @Override
    public void publish(DomainEvent event) {
        // 自动填充审计信息
        enrichEvent(event);

        // 如果当前有事务，则在事务提交后发布
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void beforeCommit(boolean readOnly) {
                        if (!readOnly) {
                            try {
                                applicationEventPublisher.publishEvent(event);
                                log.debug("领域事件已发布: {} (ID: {})", event.getClass().getSimpleName(), event.getEventId());
                            } catch (Exception e) {
                                log.error("领域事件发布失败，事务将回滚: {} (ID: {})",
                                    event.getClass().getSimpleName(), event.getEventId(), e);
                                throw e; // 重新抛出异常，触发事务回滚
                            }
                        }
                    }
                }
            );
        } else {
            // 没有事务，直接发布
            applicationEventPublisher.publishEvent(event);
            log.debug("领域事件已发布（无事务）: {} (ID: {})", event.getClass().getSimpleName(), event.getEventId());
        }
    }

    @Override
    public void publish(DomainEvent... events) {
        for (DomainEvent event : events) {
            publish(event);
        }
    }

    @Override
    public void publish(ApplicationEvent event) {
        applicationEventPublisher.publishEvent(event);
        log.debug("应用事件已发布: {} (ID: {})", event.getClass().getSimpleName(), event.getEventId());
    }

    /**
     * 丰富事件信息（自动填充租户ID、用户ID等）
     */
    private void enrichEvent(DomainEvent event) {
        // 填充租户ID
        if (event.getTenantId() == null) {
            Long tenantId = TenantUtils.getCurrentTenantId();
            event.setTenantId(tenantId);
        }

        // 填充用户ID
        if (event.getUserId() == null && currentUser != null) {
            try {
                Long userId = currentUser.getCurrentUserId();
                event.setUserId(userId);
            } catch (Exception e) {
                // 忽略异常，可能没有登录用户
                log.trace("无法获取当前用户ID: {}", e.getMessage());
            }
        }
    }
}
