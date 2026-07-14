/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.infrastructure.transaction;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 软删除过滤器切面：在所有 {@link org.springframework.data.jpa.repository.JpaRepository} 方法执行前
 * 启用 {@code deletedFilter}（定义见 {@link com.openhis.whale.core.domain.AuditedEntityBase}）。
 *
 * <p>启用失败不得静默忽略：否则同号「软删后重插」会把已删行一并查出，业务侧表现为明细翻倍。</p>
 */
@Aspect
@Component
public class SoftDeleteFilterAspect {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 在 Repository 方法执行前启用（或按注解跳过）软删过滤器。
     *
     * @param joinPoint Repository 调用切点
     */
    @Before("execution(* org.springframework.data.jpa.repository.JpaRepository+.*(..))")
    public void enableSoftDeleteFilter(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Session session = entityManager.unwrap(Session.class);
        // SkipSoftDeleteFilter：本调用需要读到已软删行（恢复/审计等），关闭过滤器
        if (method.isAnnotationPresent(SkipSoftDeleteFilter.class)) {
            session.disableFilter("deletedFilter");
            return;
        }
        if (session.getEnabledFilter("deletedFilter") == null) {
            session.enableFilter("deletedFilter").setParameter("isDeleted", false);
        }
    }
}
