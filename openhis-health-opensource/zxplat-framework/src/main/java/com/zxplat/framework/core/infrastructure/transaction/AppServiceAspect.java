/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.infrastructure.transaction;

import com.zxplat.framework.core.application.util.StringUtils;
import com.zxplat.framework.core.infrastructure.job.SchedulableJob;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.lang.reflect.Method;

/**
 * AppService事务切面（UoW 自动管理）
 * 自动为所有 AppService 方法添加读写事务。
 * 切点覆盖 {@code com.zxplat} / {@code openhis}（产品演示院点 L2）下所有 {@code *AppService}。
 *
 * <p>{@link SchedulableJob#openAppServiceTransaction()} 为 false 的定时任务方法跳过本切面外层事务，
 * 由仓储/领域短事务提交，避免长批次中途异常污染整批。</p>
 */
@Aspect
@Component
@Order(1)
public class AppServiceAspect {

    private static final Logger logger = LoggerFactory.getLogger(AppServiceAspect.class);

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Around("execution(* com.zxplat..*AppService.*(..)) || execution(* openhis..*AppService.*(..))")
    public Object aroundAppService(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();
        // Gate：定时任务显式关闭外层 UoW 时直接放行（住院滚费等长批次）
        if (this.shouldSkipOuterTransaction(joinPoint)) {
            logger.debug("🔵 AppServiceAspect: 跳过外层事务 method={}", methodName);
            return joinPoint.proceed();
        }
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

    /**
     * 是否跳过 AppService 外层事务（仅 {@link SchedulableJob#openAppServiceTransaction()} = false）。
     *
     * @param joinPoint 切点
     * @return true 表示本方法不由本切面开启 UoW
     */
    private boolean shouldSkipOuterTransaction(ProceedingJoinPoint joinPoint) {
        if (!(joinPoint.getSignature() instanceof MethodSignature methodSignature)) {
            return false;
        }
        Method method = methodSignature.getMethod();
        Object target = joinPoint.getTarget();
        if (target != null) {
            method = AopUtils.getMostSpecificMethod(method, target.getClass());
        }
        SchedulableJob marker = AnnotationUtils.findAnnotation(method, SchedulableJob.class);
        if (marker == null) {
            return false;
        }
        return !marker.openAppServiceTransaction();
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
            if (className.startsWith("com.zxplat.application.")
                    || className.startsWith("com.zxplat.domain.")
                    || className.startsWith("com.zxplat.plugin.")
                    || className.startsWith("com.zxplat.clinic.")
                    || className.startsWith("openhis.")) {
                return element;
            }
        }
        for (StackTraceElement element : throwable.getStackTrace()) {
            String className = element.getClassName();
            if ((className.startsWith("com.zxplat.") || className.startsWith("openhis."))
                    && !className.startsWith("com.zxplat.framework.core.")) {
                return element;
            }
        }
        return null;
    }
}
