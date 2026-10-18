/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.domain.peisconfig.repository;

import com.zxplat.plugin.peis.domain.peisconfig.entity.PeisConfig;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * peis_config 仓储。
 */
public interface PeisConfigRepository extends JpaRepository<PeisConfig, Long> {

    Optional<PeisConfig> findByTenantIdAndConfigKeyAndIsDeletedFalse(Long tenantId, String configKey);

    boolean existsByTenantIdAndConfigKeyAndIsDeletedFalse(Long tenantId, String configKey);

    Page<PeisConfig> findByTenantIdAndIsDeletedFalseOrderByConfigKeyAsc(Long tenantId, Pageable pageable);
}
