/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.audit.application;

import com.openhis.whale.audit.application.dto.AuditLogDto;
import com.openhis.whale.audit.application.dto.AuditLogQueryRequest;
import com.openhis.whale.audit.domain.AuditLog;
import com.openhis.whale.audit.infrastructure.repository.AuditLogRepository;
import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.core.application.util.PageRequestUtils;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 审计日志查询应用服务
 * 提供分页查询，不参与写审计（写由 IAuditingStore 实现）
 */
@Service
public class AuditLogAppService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogAppService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }
    public PagedResult<AuditLogDto> getList(AuditLogQueryRequest request) {
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (request.getEntityType() != null && !request.getEntityType().isBlank()) {
                predicates.add(cb.equal(root.get("entityType"), request.getEntityType()));
            }
            if (request.getEntityId() != null) {
                predicates.add(cb.equal(root.get("entityId"), request.getEntityId()));
            }
            if (request.getOperationType() != null) {
                predicates.add(cb.equal(root.get("operationType"), request.getOperationType()));
            }
            if (request.getUserId() != null) {
                predicates.add(cb.equal(root.get("userId"), request.getUserId()));
            }
            if (request.getTenantId() != null) {
                predicates.add(cb.equal(root.get("tenantId"), request.getTenantId()));
            }
            if (request.getStartTime() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), request.getStartTime()));
            }
            if (request.getEndTime() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), request.getEndTime()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Sort.Direction direction = request.getSortDirection() != null && "ASC".equalsIgnoreCase(request.getSortDirection()) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String sortBy = request.getSortBy() != null && !request.getSortBy().isBlank() ? request.getSortBy() : "createdAt";
        PageRequest pageable = PageRequestUtils.create(request.getPageNo(), request.getPageSize(), direction, sortBy);

        Page<AuditLog> page = auditLogRepository.findAll(spec, pageable);
        List<AuditLogDto> items = page.getContent().stream().map(this::toDto).toList();
        return PagedResult.of(items, page.getTotalElements(), page.getNumber() + 1, page.getSize());
    }

    private AuditLogDto toDto(AuditLog entity) {
        AuditLogDto dto = new AuditLogDto();
        dto.setId(entity.getId());
        dto.setEntityType(entity.getEntityType());
        dto.setEntityId(entity.getEntityId());
        dto.setOperationType(entity.getOperationType());
        dto.setUserId(entity.getUserId());
        dto.setTenantId(entity.getTenantId());
        dto.setIpAddress(entity.getIpAddress());
        dto.setUserAgent(entity.getUserAgent());
        dto.setChanges(entity.getChanges());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }
}
