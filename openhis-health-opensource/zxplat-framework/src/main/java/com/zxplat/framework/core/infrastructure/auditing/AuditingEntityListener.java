/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.infrastructure.auditing;

import com.zxplat.framework.core.application.util.JsonUtils;
import com.zxplat.framework.core.domain.AuditedEntityBase;
import com.zxplat.framework.core.domain.BaseAuditedEntity;
import com.zxplat.framework.core.domain.IAuditSummary;
import com.zxplat.framework.core.domain.IExcludeFromAudit;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import com.zxplat.framework.core.infrastructure.security.ICurrentUser;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;

/**
 * 审计实体监听器
 * 1. Pre*：自动填充创建时间、更新时间、创建人、更新人、租户ID；软删除时设置删除标记
 * 2. Post*：若存在 IAuditingStore 实现（如 zxplat-framework audit），则写入审计日志
 */
@Component
public class AuditingEntityListener implements ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(AuditingEntityListener.class);
    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        AuditingEntityListener.applicationContext = applicationContext;
    }

    private ICurrentUser getCurrentUser() {
        if (applicationContext != null) {
            try {
                return applicationContext.getBean(ICurrentUser.class);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private IAuditingStore getAuditingStore() {
        if (applicationContext != null) {
            try {
                return applicationContext.getBean(IAuditingStore.class);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private String getClientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                if (request != null) {
                    return request.getRemoteAddr();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String getUserAgent() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                if (request != null) {
                    return request.getHeader("User-Agent");
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String serializeState(AuditedEntityBase entity) {
        try {
            return JsonUtils.toJson(entity);
        } catch (Exception e) {
            log.debug("Audit state serialize skipped for {}: {}", entity.getClass().getSimpleName(), e.getMessage());
            return fallbackState(entity);
        }
    }

    /** 序列化失败时至少保留实体类型与 id，便于排查 */
    private String fallbackState(AuditedEntityBase entity) {
        try {
            return JsonUtils.toJson(java.util.Map.of(
                    "entityType", entity.getClass().getSimpleName(),
                    "entityId", entity.getId() != null ? entity.getId() : ""));
        } catch (Exception e) {
            return "{}";
        }
    }

    /** 优先使用实体提供的审计摘要，否则完整序列化 */
    private String buildNewState(AuditedEntityBase entity, OperationType operationType) {
        if (entity instanceof IAuditSummary) {
            String summary = ((IAuditSummary) entity).getAuditSummary(operationType);
            if (summary != null && !summary.isEmpty()) {
                return summary;
            }
        }
        return serializeState(entity);
    }

    private void saveAuditLog(AuditedEntityBase entity, OperationType operationType, String oldState, String newState) {
        if (entity instanceof IExcludeFromAudit) {
            return;
        }
        IAuditingStore store = getAuditingStore();
        if (store == null) {
            return;
        }
        try {
            ICurrentUser user = getCurrentUser();
            Long userId = user != null ? user.getCurrentUserId() : null;
            Long tenantId;
            if (entity instanceof BaseAuditedEntity ta && ta.getTenantId() != null) {
                tenantId = ta.getTenantId();
            } else {
                tenantId = TenantUtils.getCurrentTenantId();
            }
            AuditLogInfo info = AuditLogInfo.builder()
                    .entityType(entity.getClass().getSimpleName())
                    .entityId(entity.getId())
                    .operationType(operationType)
                    .userId(userId)
                    .tenantId(tenantId)
                    .ipAddress(getClientIp())
                    .userAgent(getUserAgent())
                    .oldState(oldState)
                    .newState(newState)
                    .createdAt(LocalDateTime.now())
                    .build();
            store.save(info);
        } catch (Exception e) {
            log.warn("Audit log save failed for {} {}: {}", entity.getClass().getSimpleName(), operationType, e.getMessage());
        }
    }

    @PrePersist
    public void prePersist(Object target) {
        if (!(target instanceof AuditedEntityBase entity)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        if (entity instanceof BaseAuditedEntity ta && ta.getTenantId() == null) {
            Long tenantId = TenantUtils.getCurrentTenantId();
            if (tenantId != null) {
                ta.setTenantId(tenantId);
            }
        }

        ICurrentUser currentUser = getCurrentUser();
        if (currentUser != null) {
            Long currentUserId = currentUser.getCurrentUserId();
            entity.setCreatedBy(currentUserId);
            entity.setUpdatedBy(currentUserId);
        }
    }

    @PostPersist
    public void postPersist(Object target) {
        if (!(target instanceof AuditedEntityBase entity)) {
            return;
        }
        saveAuditLog(entity, OperationType.CREATE, null, buildNewState(entity, OperationType.CREATE));
    }

    @PreUpdate
    public void preUpdate(Object target) {
        if (!(target instanceof AuditedEntityBase entity)) {
            return;
        }
        entity.setUpdatedAt(LocalDateTime.now());

        ICurrentUser currentUser = getCurrentUser();
        if (currentUser != null) {
            Long currentUserId = currentUser.getCurrentUserId();
            entity.setUpdatedBy(currentUserId);
        }
    }

    @PostUpdate
    public void postUpdate(Object target) {
        if (!(target instanceof AuditedEntityBase entity)) {
            return;
        }
        saveAuditLog(entity, OperationType.UPDATE, null, buildNewState(entity, OperationType.UPDATE));
    }

    @PreRemove
    public void preRemove(Object target) {
        if (!(target instanceof AuditedEntityBase entity)) {
            return;
        }
        // 软删除：设置删除标记和删除时间
        entity.setIsDeleted(true);
        entity.setDeletedAt(LocalDateTime.now());

        ICurrentUser currentUser = getCurrentUser();
        if (currentUser != null) {
            Long currentUserId = currentUser.getCurrentUserId();
            entity.setDeletedBy(currentUserId);
        }
    }

    @PostRemove
    public void postRemove(Object target) {
        if (!(target instanceof AuditedEntityBase entity)) {
            return;
        }
        saveAuditLog(entity, OperationType.DELETE, serializeState(entity), null);
    }
}
