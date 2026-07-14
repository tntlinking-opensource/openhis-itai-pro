/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.tenant.infrastructure.repository;

import com.openhis.whale.tenant.domain.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 租户Repository
 *
 * @author Whale Framework
 */
@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long>, JpaSpecificationExecutor<Tenant> {

    /**
     * 根据代码查找租户
     */
    Optional<Tenant> findByCode(String code);

    /**
     * 检查代码是否存在
     */
    boolean existsByCode(String code);

    /**
     * 检查名称是否存在
     */
    boolean existsByName(String name);
}
