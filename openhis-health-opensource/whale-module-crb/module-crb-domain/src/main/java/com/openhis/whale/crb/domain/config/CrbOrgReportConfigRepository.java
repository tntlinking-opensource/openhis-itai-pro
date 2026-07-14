/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.domain.config;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 院端前置配置仓储。
 */
public interface CrbOrgReportConfigRepository extends JpaRepository<CrbOrgReportConfig, Long> {

    /**
     * 按租户加载启用中的上报配置。
     *
     * @param tenantId 租户 id
     * @return 配置（若存在且未删除）
     */
    Optional<CrbOrgReportConfig> findFirstByTenantIdAndEnabledTrueAndIsDeletedFalse(Long tenantId);
}
