/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.permission.infrastructure.repository;

import com.openhis.whale.permission.domain.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 权限Repository
 */
@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long>, JpaSpecificationExecutor<Permission> {

    /**
     * 根据名称查找权限
     */
    Optional<Permission> findByName(String name);

    /**
     * 根据父级名称查找所有子权限
     */
    List<Permission> findByParentName(String parentName);

    /**
     * 查找所有根权限（parentName为null）
     */
    List<Permission> findByParentNameIsNull();

    /**
     * 判断权限名称是否存在
     */
    boolean existsByName(String name);
}
