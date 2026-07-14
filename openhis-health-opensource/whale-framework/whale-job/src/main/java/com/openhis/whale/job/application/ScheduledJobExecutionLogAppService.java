/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.job.application;

import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.core.application.exception.EntityNotFoundException;
import com.openhis.whale.core.infrastructure.permission.RequirePermission;
import com.openhis.whale.job.application.dto.ScheduledJobExecutionLogDto;
import com.openhis.whale.job.application.dto.ScheduledJobExecutionLogIdRequest;
import com.openhis.whale.job.application.dto.ScheduledJobExecutionLogQueryRequest;
import com.openhis.whale.job.application.support.ScheduledJobTenantSupport;
import com.openhis.whale.job.domain.ScheduledJobExecutionLog;
import com.openhis.whale.job.domain.ScheduledJobExecutionType;
import com.openhis.whale.job.infrastructure.repository.ScheduledJobExecutionLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 定时任务执行历史应用服务（读写 whale_qrtz_job_execution_log）。
 */
@Service
@RequiredArgsConstructor
public class ScheduledJobExecutionLogAppService {

    private final ScheduledJobExecutionLogRepository scheduledJobExecutionLogRepository;
    private final ScheduledJobTenantSupport scheduledJobTenantSupport;

    /**
     * 分页查询执行历史。
     *
     * @param request 查询条件（支持租户、任务键、成败、时间范围）
     * @return 分页结果
     */
    @RequirePermission("Navigation.JobManagement")
    public PagedResult<ScheduledJobExecutionLogDto> getList(ScheduledJobExecutionLogQueryRequest request) {
        // Gate：分页窗口归一（finished_at 倒序）
        int pageIndex = request.getPageNo();
        int pageSize = request.getPageSize();
        PageRequest pageRequest = PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Direction.DESC, "finishedAt"));
        // Pre：条件下推数据库
        Specification<ScheduledJobExecutionLog> spec = buildSpecification(request);
        Page<ScheduledJobExecutionLog> page = scheduledJobExecutionLogRepository.findAll(spec, pageRequest);
        Set<Long> tenantIds = new HashSet<>();
        for (ScheduledJobExecutionLog log : page.getContent()) {
            if (log.getTenantId() != null) {
                tenantIds.add(log.getTenantId());
            }
        }
        Map<Long, String> tenantNames = scheduledJobTenantSupport.resolveTenantNames(tenantIds);
        // Asm：映射 DTO
        return PagedResult.fromPage(page.map(log -> toDto(log, tenantNames)));
    }

    /**
     * 按 ID 查询单条执行历史。
     *
     * @param request 含日志 ID
     * @return 执行详情
     */
    @RequirePermission("Navigation.JobManagement")
    public ScheduledJobExecutionLogDto getById(ScheduledJobExecutionLogIdRequest request) {
        ScheduledJobExecutionLog log = scheduledJobExecutionLogRepository.findById(request.getId())
                .orElseThrow(() -> new EntityNotFoundException("任务执行日志", request.getId()));
        Map<Long, String> tenantNames = scheduledJobTenantSupport.resolveTenantNames(
                log.getTenantId() == null ? List.of() : List.of(log.getTenantId()));
        return toDto(log, tenantNames);
    }

    private Specification<ScheduledJobExecutionLog> buildSpecification(ScheduledJobExecutionLogQueryRequest request) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (request.getTenantId() != null) {
                predicates.add(cb.equal(root.get("tenantId"), request.getTenantId()));
            }
            if (trimToNull(request.getJobName()) != null) {
                predicates.add(cb.equal(root.get("jobName"), request.getJobName().trim()));
            }
            if (trimToNull(request.getJobGroup()) != null) {
                predicates.add(cb.equal(root.get("jobGroup"), request.getJobGroup().trim()));
            }
            if (request.getSuccess() != null) {
                predicates.add(cb.equal(root.get("success"), request.getSuccess()));
            }
            if (request.getFinishedAtFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("finishedAt"), request.getFinishedAtFrom()));
            }
            if (request.getFinishedAtTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("finishedAt"), request.getFinishedAtTo()));
            }
            String keyword = trimToNull(request.getKeyword());
            if (keyword != null) {
                String pattern = "%" + keyword.toLowerCase() + "%";
                Predicate jobNameLike = cb.like(cb.lower(root.get("jobName")), pattern);
                Predicate displayNameLike = cb.like(cb.lower(root.get("displayName")), pattern);
                Predicate beanLike = cb.like(cb.lower(root.get("jobBeanName")), pattern);
                predicates.add(cb.or(jobNameLike, displayNameLike, beanLike));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private ScheduledJobExecutionLogDto toDto(ScheduledJobExecutionLog log, Map<Long, String> tenantNames) {
        ScheduledJobExecutionLogDto dto = new ScheduledJobExecutionLogDto();
        dto.setId(log.getId());
        dto.setJobName(log.getJobName());
        dto.setJobGroup(log.getJobGroup());
        dto.setJobBeanName(log.getJobBeanName());
        dto.setDisplayName(log.getDisplayName());
        dto.setTenantId(log.getTenantId());
        if (log.getTenantId() != null && tenantNames != null) {
            dto.setTenantName(tenantNames.get(log.getTenantId()));
        }
        dto.setExecutionType(log.getExecutionType());
        ScheduledJobExecutionType executionType = ScheduledJobExecutionType.fromCode(log.getExecutionType());
        if (executionType != null) {
            dto.setExecutionTypeLabel(executionType.getLabel());
        }
        dto.setSuccess(log.getSuccess());
        if (Boolean.TRUE.equals(log.getSuccess())) {
            dto.setSuccessLabel("成功");
        } else {
            dto.setSuccessLabel("失败");
        }
        dto.setErrorMessage(log.getErrorMessage());
        dto.setDurationMs(log.getDurationMs());
        dto.setStartedAt(log.getStartedAt());
        dto.setFinishedAt(log.getFinishedAt());
        return dto;
    }
}
