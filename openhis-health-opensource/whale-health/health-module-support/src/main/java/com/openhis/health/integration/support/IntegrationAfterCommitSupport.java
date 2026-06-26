/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.support;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 在事务提交后执行集成回调，等价于 adapter {@code @TransactionalEventListener(AFTER_COMMIT)}。
 */
public final class IntegrationAfterCommitSupport {

    private IntegrationAfterCommitSupport() {
    }

    public static void runAfterCommit(Runnable action) {
        if (action == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
