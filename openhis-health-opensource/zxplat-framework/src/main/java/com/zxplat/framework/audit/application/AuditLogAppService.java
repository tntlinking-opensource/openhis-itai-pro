/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.audit.application;

import com.zxplat.framework.audit.application.dto.AuditLogDto;
import com.zxplat.framework.audit.application.dto.AuditLogQueryRequest;
import com.zxplat.framework.audit.domain.AuditLog;
import com.zxplat.framework.audit.infrastructure.repository.AuditLogRepository;
import com.zxplat.framework.core.application.dto.PagedResult;
import com.zxplat.framework.core.application.util.PageRequestUtils;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 审计日志查询应用服务。
 * <p>
 * 职责：按实体类型/主键、操作类型、用户、租户与时间窗分页检索审计记录，供运维与合规查阅。
 * 本服务只读；审计写入由框架 {@code IAuditingStore} 在业务变更路径上落库，不经本类。
 * </p>
 */
@Service
public class AuditLogAppService {

    private final AuditLogRepository auditLogRepository;

    /**
     * @param auditLogRepository 审计日志仓储（JPA）
     */
    public AuditLogAppService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * 分页查询审计日志。
     * <p>
     * 条件均为可选：未传字段不下推谓词；排序缺省为 {@code createdAt DESC}。
     * </p>
     *
     * @param request 查询条件与分页、排序参数
     * @return 分页 DTO（页码对外从 1 起）
     */
    public PagedResult<AuditLogDto> getList(AuditLogQueryRequest request) {
        // Pre：按请求动态组装 JPA Specification（空条件不过滤）
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // 实体类型（如聚合名 / 表业务类型）精确匹配
            if (request.getEntityType() != null && !request.getEntityType().isBlank()) {
                predicates.add(cb.equal(root.get("entityType"), request.getEntityType()));
            }
            // 被审计实体主键
            if (request.getEntityId() != null) {
                predicates.add(cb.equal(root.get("entityId"), request.getEntityId()));
            }
            // 操作类型（增删改等）
            if (request.getOperationType() != null) {
                predicates.add(cb.equal(root.get("operationType"), request.getOperationType()));
            }
            // 操作者用户
            if (request.getUserId() != null) {
                predicates.add(cb.equal(root.get("userId"), request.getUserId()));
            }
            // 租户隔离条件
            if (request.getTenantId() != null) {
                predicates.add(cb.equal(root.get("tenantId"), request.getTenantId()));
            }
            // 创建时间下界（含）
            if (request.getStartTime() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), request.getStartTime()));
            }
            // 创建时间上界（含）
            if (request.getEndTime() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), request.getEndTime()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        // Gate：归一排序方向与字段；非法/空则回落 createdAt DESC
        Sort.Direction direction = Sort.Direction.DESC;
        if (request.getSortDirection() != null && "ASC".equalsIgnoreCase(request.getSortDirection())) {
            direction = Sort.Direction.ASC;
        }
        String sortBy = "createdAt";
        if (request.getSortBy() != null && !request.getSortBy().isBlank()) {
            sortBy = request.getSortBy();
        }
        PageRequest pageable = PageRequestUtils.create(request.getPageNo(), request.getPageSize(), direction, sortBy);

        // Cmd：仓储分页查询（只读）
        Page<AuditLog> page = auditLogRepository.findAll(spec, pageable);
        // Asm：实体 → 查询 DTO，页码转为 1-based
        List<AuditLogDto> items = page.getContent().stream().map(this::toDto).toList();
        return PagedResult.of(items, page.getTotalElements(), page.getNumber() + 1, page.getSize());
    }

    /**
     * 将审计实体映射为对外查询 DTO（字段一一对应，不含写侧专用扩展）。
     *
     * @param entity 持久化审计日志
     * @return 查询展示用 DTO
     */
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
