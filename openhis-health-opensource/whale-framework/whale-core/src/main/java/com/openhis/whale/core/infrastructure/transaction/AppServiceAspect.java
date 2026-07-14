/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.infrastructure.transaction;

import com.openhis.whale.core.application.util.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

/**
 * AppService事务切面（UoW 自动管理）
 * 自动为所有 AppService 方法添加读写事务。
 * 切点覆盖 com.openhis 下所有子包（whale 框架 + health 等业务模块）。
 */
@Aspect
@Component
@Order(1)
public class AppServiceAspect {

    private static final Logger logger = LoggerFactory.getLogger(AppServiceAspect.class);

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Around("execution(* com.openhis..*AppService.*(..))")
    public Object aroundAppService(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();
        logger.debug("🔵 AppServiceAspect: 开始处理方法 {}", methodName);

        // 使用编程式事务管理，确保事务正确开启
        org.springframework.transaction.support.DefaultTransactionDefinition def =
            new org.springframework.transaction.support.DefaultTransactionDefinition();
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        def.setReadOnly(false);

        TransactionStatus status = transactionManager.getTransaction(def);
        boolean newTransaction = status.isNewTransaction();
        logger.debug("🔵 AppServiceAspect: 事务已开启，isNewTransaction={}", newTransaction);

        try {
            Object result = joinPoint.proceed();
            if (newTransaction && !status.isCompleted()) {
                transactionManager.commit(status);
                logger.debug("🔵 AppServiceAspect: 事务已提交");
            }
            return result;
        } catch (Throwable e) {
            if (newTransaction && !status.isCompleted()) {
                transactionManager.rollback(status);
            }
            Throwable rootCause = getRootCause(e);
            StackTraceElement location = findPreferredBusinessLocation(e, rootCause);
            String traceId = StringUtils.defaultIfBlank(StringUtils.firstNonBlank(
                MDC.get("traceId"), MDC.get("X-B3-TraceId"), MDC.get("requestId")), "-");
            if (location != null) {
                logger.error(
                    "🔵 AppServiceAspect: 事务已回滚，method={}, traceId={}, exceptionType={}, rootType={}, rootMsg={}, 定位={}.{}:{}",
                    methodName,
                    traceId,
                    e.getClass().getName(),
                    rootCause.getClass().getName(),
                    rootCause.getMessage(),
                    location.getClassName(),
                    location.getMethodName(),
                    location.getLineNumber(),
                    e
                );
            } else {
                logger.error(
                    "🔵 AppServiceAspect: 事务已回滚，method={}, traceId={}, exceptionType={}, rootType={}, rootMsg={}",
                    methodName,
                    traceId,
                    e.getClass().getName(),
                    rootCause.getClass().getName(),
                    rootCause.getMessage(),
                    e
                );
            }
            throw e;
        }
    }

    private Throwable getRootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private StackTraceElement findPreferredBusinessLocation(Throwable throwable, Throwable rootCause) {
        StackTraceElement location = findBusinessLocation(rootCause);
        if (location != null) {
            return location;
        }
        location = findBusinessLocation(throwable);
        if (location != null) {
            return location;
        }
        return rootCause.getStackTrace().length > 0 ? rootCause.getStackTrace()[0] : null;
    }

    private StackTraceElement findBusinessLocation(Throwable throwable) {
        for (StackTraceElement element : throwable.getStackTrace()) {
            String className = element.getClassName();
            if (className.startsWith("com.openhis.health.")) {
                return element;
            }
        }
        for (StackTraceElement element : throwable.getStackTrace()) {
            String className = element.getClassName();
            if (className.startsWith("com.openhis.") && !className.startsWith("com.openhis.whale.core.")) {
                return element;
            }
        }
        return null;
    }
}
