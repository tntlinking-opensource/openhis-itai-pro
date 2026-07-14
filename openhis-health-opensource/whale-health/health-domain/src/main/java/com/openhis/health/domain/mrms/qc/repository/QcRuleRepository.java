/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.mrms.qc.repository;

import com.openhis.health.domain.mrms.qc.entity.QcRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * 质控规则仓储。
 */
public interface QcRuleRepository extends JpaRepository<QcRule, Long> {

    /**
     * 批量加载种子租户与业务租户下已启用规则（内存 merge 用）。
     *
     * @param tenantIds 租户 id 集合
     * @return 规则行
     */
    List<QcRule> findByTenantIdInAndEnabledTrueAndIsDeletedFalse(Collection<Long> tenantIds);
}
