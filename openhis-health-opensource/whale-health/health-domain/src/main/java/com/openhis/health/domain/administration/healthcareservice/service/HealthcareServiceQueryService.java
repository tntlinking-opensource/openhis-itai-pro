/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.healthcareservice.service;

import com.openhis.health.domain.administration.healthcareservice.entity.HealthcareService;
import com.openhis.health.domain.administration.healthcareservice.repository.HealthcareServiceRepository;
import com.openhis.health.domain.share.enums.HealthcareServiceStatus;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;

/**
 * 医疗服务（号源/服务项定义）读侧查询服务。
 *
 * <p>职责：为挂号、排班等场景提供启用中的医疗服务列表及按主键加载详情。</p>
 *
 * <p>边界：只读；列表过滤条件为 {@link HealthcareServiceStatus#ACTIVE} 语义码。</p>
 * <p>聚合边界：仅本聚合 {@link HealthcareServiceRepository}。</p>
 */
@Service
@RequiredArgsConstructor
public class HealthcareServiceQueryService {
    private final HealthcareServiceRepository repository;

    /**
     * 列出挂号场景可选的启用医疗服务（维护台已发布且启用）。
     *
     * @return 医疗服务列表，可能为空
     */
    public List<HealthcareService> listServicesForRegistration() {
        // 仅返回已发布（ACTIVE）行，停用服务不出现在挂号下拉中。
        return repository.findAllByStatusEnum(HealthcareServiceStatus.ACTIVE.getCode());
    }

    /**
     * 按主键加载医疗服务详情。
     *
     * @param id 医疗服务主键
     * @return 实体；不存在时 {@code null}，由应用层决定是否提示
     */
    public HealthcareService loadHealthcareServiceById(Long id) {
        return repository.findById(id).orElse(null);
    }

    /**
     * 按主键批量加载医疗服务，供绑定列表 enrich 号源来源项展示名。
     *
     * @param ids 主键集合
     * @return 主键到实体的映射；入参为空时返回空 Map
     */
    public Map<Long, HealthcareService> loadHealthcareServiceMapByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        List<HealthcareService> rows = repository.findAllById(ids);
        Map<Long, HealthcareService> result = new HashMap<>();
        for (HealthcareService row : rows) {
            if (row != null && row.getId() != null) {
                result.put(row.getId(), row);
            }
        }
        return result;
    }
}
