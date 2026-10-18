/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.infrastructure.transaction;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.lang.reflect.Method;

/**
 * 只读事务切面
 * 自动为查询方法添加只读事务。
 * 非查询方法直接跳过，由 AppServiceAspect 处理（读写事务）。
 * 切点覆盖 {@code com.zxplat} / {@code openhis}（产品演示院点 L2）下所有子包。
 */
@Aspect
@Component
@Order(0)  // 在AppServiceAspect之前执行
public class ReadOnlyTransactionAspect {

    @Autowired
    private PlatformTransactionManager transactionManager;

    /**
     * 查询方法名称模式
     */
    private static final String[] QUERY_METHOD_PREFIXES = {
        "get", "find", "list", "query", "search", "count", "exists", "check"
    };

    @Around("execution(* com.zxplat..*AppService.*(..)) || execution(* openhis..*AppService.*(..))")
    public Object aroundQueryMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String methodName = method.getName().toLowerCase();

        // 检查是否为查询方法
        boolean isQueryMethod = false;
        for (String prefix : QUERY_METHOD_PREFIXES) {
            if (methodName.startsWith(prefix)) {
                isQueryMethod = true;
                break;
            }
        }

        if (isQueryMethod) {
            // 查询方法：手动开启只读事务
            DefaultTransactionDefinition def = new DefaultTransactionDefinition();
            def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
            def.setReadOnly(true);
            TransactionStatus status = transactionManager.getTransaction(def);
            boolean newTransaction = status.isNewTransaction();
            try {
                Object result = joinPoint.proceed();
                if (newTransaction && !status.isCompleted()) {
                    transactionManager.commit(status);
                }
                return result;
            } catch (Exception e) {
                if (newTransaction && !status.isCompleted()) {
                    transactionManager.rollback(status);
                }
                throw e;
            }
        } else {
            // 非查询方法：直接跳过，让AppServiceAspect处理（使用读写事务）
            return joinPoint.proceed();
        }
    }
}
