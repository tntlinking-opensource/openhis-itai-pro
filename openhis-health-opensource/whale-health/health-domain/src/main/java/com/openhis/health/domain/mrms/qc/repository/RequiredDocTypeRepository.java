/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.mrms.qc.repository;

import com.openhis.health.domain.mrms.qc.entity.RequiredDocType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * 必写文书类型配置仓储。
 */
public interface RequiredDocTypeRepository extends JpaRepository<RequiredDocType, Long> {

    /**
     * 批量加载种子与租户下必写文书配置。
     *
     * @param tenantIds 租户 id 集合
     * @return 必写文书行
     */
    List<RequiredDocType> findByTenantIdInAndRequiredTrueAndIsDeletedFalse(Collection<Long> tenantIds);
}
