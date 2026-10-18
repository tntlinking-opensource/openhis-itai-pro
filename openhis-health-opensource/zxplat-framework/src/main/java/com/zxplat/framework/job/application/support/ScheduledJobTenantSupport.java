/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.job.application.support;

import com.zxplat.framework.job.application.dto.ScheduledJobTenantOptionDto;
import com.zxplat.framework.tenant.domain.Tenant;
import com.zxplat.framework.tenant.infrastructure.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 定时任务模块租户下拉与名称解析。
 */
@Component
@RequiredArgsConstructor
public class ScheduledJobTenantSupport {

    private final TenantRepository tenantRepository;

    /**
     * 加载未删除且启用的租户选项（任务绑定与日志筛选共用）。
     *
     * @return 租户下拉列表
     */
    public List<ScheduledJobTenantOptionDto> listActiveTenantOptions() {
        Specification<Tenant> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("isDeleted"), false));
            predicates.add(cb.equal(root.get("isActive"), true));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<Tenant> tenants = tenantRepository.findAll(spec);
        tenants.sort(Comparator.comparing(Tenant::getName, Comparator.nullsLast(String::compareTo)));
        List<ScheduledJobTenantOptionDto> options = new ArrayList<>();
        for (Tenant tenant : tenants) {
            ScheduledJobTenantOptionDto option = new ScheduledJobTenantOptionDto();
            option.setId(tenant.getId());
            option.setName(tenant.getName());
            option.setCode(tenant.getCode());
            options.add(option);
        }
        return options;
    }

    /**
     * 批量解析租户 ID → 名称，供列表展示。
     *
     * @param tenantIds 租户 ID 集合
     * @return ID 到名称映射
     */
    public Map<Long, String> resolveTenantNames(Iterable<Long> tenantIds) {
        Map<Long, String> result = new HashMap<>();
        if (tenantIds == null) {
            return result;
        }
        for (Long tenantId : tenantIds) {
            if (tenantId == null || result.containsKey(tenantId)) {
                continue;
            }
            tenantRepository.findById(tenantId).ifPresent(tenant -> result.put(tenantId, tenant.getName()));
        }
        return result;
    }
}
