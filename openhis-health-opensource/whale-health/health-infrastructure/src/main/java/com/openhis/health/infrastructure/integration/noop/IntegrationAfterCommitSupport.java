/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.noop;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 在事务提交后执行集成回调，等价于 adapter {@code @TransactionalEventListener(AFTER_COMMIT)}。
 */
public final class IntegrationAfterCommitSupport {

    private IntegrationAfterCommitSupport() {
    }

    /**
     * 注册事务提交后回调；无活跃事务时立即执行，避免集成侧在回滚后仍发外部请求。
     *
     * @param action 提交后执行的集成动作；null 时忽略
     */
    public static void runAfterCommit(Runnable action) {
        // 空动作无需注册同步器
        if (action == null) {
            return;
        }
        // 非事务上下文（如单测/异步线程）直接执行，与 AFTER_COMMIT 语义退化为「立即」
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        // 活跃事务：注册 AFTER_COMMIT 同步器，仅提交成功后才执行集成动作
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
