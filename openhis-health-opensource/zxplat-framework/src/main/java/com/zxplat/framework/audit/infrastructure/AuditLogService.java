/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.audit.infrastructure;

import com.zxplat.framework.audit.domain.AuditLog;
import com.zxplat.framework.audit.infrastructure.repository.AuditLogRepository;
import com.zxplat.framework.core.application.util.JsonUtils;
import com.zxplat.framework.core.infrastructure.auditing.AuditLogInfo;
import com.zxplat.framework.core.infrastructure.auditing.IAuditingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 审计存储实现（{@link IAuditingStore}）。
 * <p>
 * 将 {@link AuditLogInfo} 异步写入 {@code zxplat_audit_logs}，避免阻塞主业务事务。
 * </p>
 */
@Service
public class AuditLogService implements IAuditingStore {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogRepository;

    /**
     * @param auditLogRepository 审计日志仓储
     */
    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * 异步落库一条审计记录；失败只打错误日志，不向外抛以免影响调用方。
     *
     * @param info 框架采集的审计快照
     */
    @Override
    @Async
    public void save(AuditLogInfo info) {
        try {
            persistAuditLog(info);
        } catch (Exception e) {
            log.error("Failed to save audit log for {} {}: {}", info.getEntityType(), info.getOperationType(), e.getMessage(), e);
        }
    }

    /**
     * 组装实体并持久化；{@code changes} 存 old/new JSON。
     *
     * @param info 审计快照
     */
    private void persistAuditLog(AuditLogInfo info) {
        AuditLog entity = new AuditLog();
        entity.setEntityType(info.getEntityType());
        entity.setEntityId(info.getEntityId());
        entity.setOperationType(info.getOperationType());
        entity.setUserId(info.getUserId());
        entity.setTenantId(info.getTenantId());
        entity.setIpAddress(info.getIpAddress());
        entity.setUserAgent(info.getUserAgent());
        entity.setChanges(buildChangesJson(info.getOldState(), info.getNewState()));
        // 无采集时间则用当前时间，保证 created_at 非空
        if (info.getCreatedAt() != null) {
            entity.setCreatedAt(info.getCreatedAt());
        } else {
            entity.setCreatedAt(java.time.LocalDateTime.now());
        }
        auditLogRepository.save(entity);
        log.debug("Audit log saved: {} {} id={}", info.getEntityType(), info.getOperationType(), info.getEntityId());
    }

    /**
     * 将变更前后快照压成 JSON 字符串写入 {@code changes} 列。
     *
     * @param oldState 变更前（可空）
     * @param newState 变更后（可空）
     * @return JSON，形如 {@code {"old":"...","new":"..."}}
     */
    private static String buildChangesJson(String oldState, String newState) {
        Map<String, String> map = new HashMap<>();
        map.put("old", oldState != null ? oldState : "");
        map.put("new", newState != null ? newState : "");
        return JsonUtils.toJson(map);
    }
}
