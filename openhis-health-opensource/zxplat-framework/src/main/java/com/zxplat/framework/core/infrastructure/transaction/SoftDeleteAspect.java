/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.infrastructure.transaction;

import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Objects;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;

/**
 * 全局软删除切面：
 * <p>
 * 将 {@link CrudRepository}（含 JpaRepository）的 delete* 调用统一转换为逻辑删除（isDeleted=true）。
 * </p>
 * <p>
 * 说明：
 * <ul>
 *   <li>默认禁止无参 {@code deleteAll()}，避免误清表。</li>
 *   <li>若确需执行物理删除/跳过软删逻辑，必须显式在方法上标注 {@link SkipSoftDeleteFilter} 放行。</li>
 * </ul>
 * </p>
 */
@Aspect
@Component
public class SoftDeleteAspect {

    @PersistenceContext
    private EntityManager entityManager;

    @Around("execution(* org.springframework.data.repository.CrudRepository+.delete(..)) || " +
        "execution(* org.springframework.data.repository.CrudRepository+.deleteById(..)) || " +
        "execution(* org.springframework.data.repository.CrudRepository+.deleteAllById(..)) || " +
        "execution(* org.springframework.data.repository.CrudRepository+.deleteAll(..)) || " +
        "execution(* org.springframework.data.jpa.repository.JpaRepository+.deleteAllInBatch(..)) || " +
        "execution(* org.springframework.data.jpa.repository.JpaRepository+.deleteAllByIdInBatch(..)) || " +
        "execution(* org.springframework.data.jpa.repository.JpaRepository+.deleteInBatch(..))")
    public Object softDelete(ProceedingJoinPoint pjp) throws Throwable {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        if (method.isAnnotationPresent(SkipSoftDeleteFilter.class)) {
            return pjp.proceed();
        }

        String methodName = method.getName();
        Object[] args = pjp.getArgs();

        // 默认禁止无参全量删除，避免误删整表；确需物理删需显式加 @SkipSoftDeleteFilter
        if (isNoArgDeleteAll(methodName, args)) {
            throw new UnsupportedOperationException(
                methodName + "() is blocked by SoftDeleteAspect; use @SkipSoftDeleteFilter if needed.");
        }

        // delete(entity)
        if (Objects.equals(methodName, "delete") && args != null && args.length == 1) {
            softDeleteEntity(args[0]);
            return null;
        }

        // deleteById(id)
        if (Objects.equals(methodName, "deleteById") && args != null && args.length == 1) {
            Object id = args[0];
            Object repoTarget = pjp.getTarget();
            if (repoTarget instanceof CrudRepository<?, ?> repo) {
                @SuppressWarnings("unchecked")
                CrudRepository<Object, Object> rawRepo = (CrudRepository<Object, Object>) repo;
                rawRepo.findById(id).ifPresent(this::softDeleteEntity);
                return null;
            }
            return pjp.proceed();
        }

        // deleteAllById(ids) / deleteAll(Iterable) / deleteInBatch(entities)
        if ((Objects.equals(methodName, "deleteAllById")
            || Objects.equals(methodName, "deleteAll")
            || Objects.equals(methodName, "deleteInBatch"))
            && args != null && args.length == 1 && args[0] instanceof Iterable<?> iterable) {
            for (Object idOrEntity : iterable) {
                if (idOrEntity == null) {
                    continue;
                }
                // 若 iterable 是实体集合，则直接软删；若是 id 集合，则尝试 findById
                if (isEntityLike(idOrEntity)) {
                    softDeleteEntity(idOrEntity);
                } else {
                    Object repoTarget = pjp.getTarget();
                    if (repoTarget instanceof CrudRepository<?, ?> repo) {
                        @SuppressWarnings("unchecked")
                        CrudRepository<Object, Object> rawRepo = (CrudRepository<Object, Object>) repo;
                        rawRepo.findById(idOrEntity).ifPresent(this::softDeleteEntity);
                    } else {
                        return pjp.proceed();
                    }
                }
            }
            return null;
        }

        // deleteAllByIdInBatch(ids) / deleteAllInBatch(entities)
        if ((Objects.equals(methodName, "deleteAllByIdInBatch") || Objects.equals(methodName, "deleteAllInBatch"))
            && args != null && args.length == 1 && args[0] instanceof Iterable<?> iterable) {
            for (Object idOrEntity : iterable) {
                if (idOrEntity == null) {
                    continue;
                }
                if (isEntityLike(idOrEntity)) {
                    softDeleteEntity(idOrEntity);
                } else {
                    Object repoTarget = pjp.getTarget();
                    if (repoTarget instanceof CrudRepository<?, ?> repo) {
                        @SuppressWarnings("unchecked")
                        CrudRepository<Object, Object> rawRepo = (CrudRepository<Object, Object>) repo;
                        rawRepo.findById(idOrEntity).ifPresent(this::softDeleteEntity);
                    } else {
                        return pjp.proceed();
                    }
                }
            }
            return null;
        }

        return pjp.proceed();
    }

    private boolean isNoArgDeleteAll(String methodName, Object[] args) {
        return (Objects.equals(methodName, "deleteAll") || Objects.equals(methodName, "deleteAllInBatch"))
            && (args == null || args.length == 0);
    }

    private boolean isEntityLike(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj instanceof BaseAuditedEntity) {
            return true;
        }
        return Arrays.stream(obj.getClass().getMethods())
            .anyMatch(m -> Objects.equals(m.getName(), "setIsDeleted") && m.getParameterCount() == 1);
    }

    private void softDeleteEntity(Object entity) {
        if (entity == null) {
            return;
        }
        markDeleted(entity);
        entityManager.merge(entity);
    }

    private void markDeleted(Object entity) {
        if (entity instanceof BaseAuditedEntity audited) {
            audited.setIsDeleted(true);
            audited.setDeletedAt(LocalDateTime.now());
            return;
        }
        try {
            Method setIsDeleted = entity.getClass().getMethod("setIsDeleted", Boolean.class);
            setIsDeleted.invoke(entity, Boolean.TRUE);
        } catch (Exception ignored) {
            // ignored
        }
        try {
            Method setDeletedAt = entity.getClass().getMethod("setDeletedAt", LocalDateTime.class);
            setDeletedAt.invoke(entity, LocalDateTime.now());
        } catch (Exception ignored) {
            // deletedAt 可选
        }
    }
}
