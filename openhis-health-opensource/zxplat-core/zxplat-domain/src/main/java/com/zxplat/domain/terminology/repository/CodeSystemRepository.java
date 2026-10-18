/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.terminology.repository;

import com.zxplat.domain.terminology.entity.CodeSystem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 编码系统Repository
 *
 */
@Repository
public interface CodeSystemRepository
    extends JpaRepository<CodeSystem, Long>,
            JpaSpecificationExecutor<CodeSystem> {

    /**
     * 根据名称查找编码系统
     */
    Optional<CodeSystem> findByName(String name);

    /**
     * 根据名称和版本查找编码系统
     */
    Optional<CodeSystem> findByNameAndVersion(String name, String version);

    /**
     * 根据名称和状态查找编码系统（获取最新版本）
     */
    Optional<CodeSystem> findByNameAndStatus(String name, String status);

    /**
     * 检查编码系统是否存在
     */
    boolean existsByName(String name);

    /**
     * 根据状态查找所有编码系统
     */
    List<CodeSystem> findByStatus(String status);

    /**
     * 根据租户ID查找编码系统
     */
    List<CodeSystem> findByTenantId(Long tenantId);

}
