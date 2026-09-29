/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.pacs.workitem.repository;

import com.openhis.health.domain.pacs.workitem.entity.PacsModalityCapability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 模态能力配置仓储。
 */
public interface PacsModalityCapabilityRepository
    extends JpaRepository<PacsModalityCapability, Long>, JpaSpecificationExecutor<PacsModalityCapability> {

    /**
     * 批量查已启用能力（接单校验）。
     *
     * @param activityIds 诊疗项目集合
     * @param tenantId 租户
     * @return 能力列表
     */
    List<PacsModalityCapability> findByActivityIdInAndTenantIdAndEnabledFlagTrue(
        Collection<Long> activityIds, Long tenantId);

    /**
     * 按租户与诊疗项目查能力（底座已滤软删）。
     *
     * @param tenantId 租户
     * @param activityId 诊疗项目
     * @return 能力
     */
    Optional<PacsModalityCapability> findByTenantIdAndActivityId(Long tenantId, Long activityId);
}
