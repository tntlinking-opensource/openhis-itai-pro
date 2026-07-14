/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.audit.infrastructure;

import com.openhis.whale.audit.domain.AuditLog;
import com.openhis.whale.audit.infrastructure.repository.AuditLogRepository;
import com.openhis.whale.core.application.util.JsonUtils;
import com.openhis.whale.core.infrastructure.auditing.AuditLogInfo;
import com.openhis.whale.core.infrastructure.auditing.IAuditingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 审计存储实现
 * 将 AuditLogInfo 写入 whale_audit_logs 表，使用独立事务与异步避免影响主流程
 */
@Service
public class AuditLogService implements IAuditingStore {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    @Async
    public void save(AuditLogInfo info) {
        try {
            persistAuditLog(info);
        } catch (Exception e) {
            log.error("Failed to save audit log for {} {}: {}", info.getEntityType(), info.getOperationType(), e.getMessage(), e);
        }
    }

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
        entity.setCreatedAt(info.getCreatedAt() != null ? info.getCreatedAt() : java.time.LocalDateTime.now());
        auditLogRepository.save(entity);
        log.debug("Audit log saved: {} {} id={}", info.getEntityType(), info.getOperationType(), info.getEntityId());
    }

    private static String buildChangesJson(String oldState, String newState) {
        Map<String, String> map = new HashMap<>();
        map.put("old", oldState != null ? oldState : "");
        map.put("new", newState != null ? newState : "");
        return JsonUtils.toJson(map);
    }
}
