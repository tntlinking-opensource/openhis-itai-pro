/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.identity.infrastructure.repository;

import com.openhis.whale.identity.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 角色Repository
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, Long>, JpaSpecificationExecutor<Role> {

    /**
     * 查询未删除的内置角色，按主键升序。
     */
    List<Role> findByIsStaticTrueAndIsDeletedFalseOrderByIdAsc();

    /**
     * 判断指定主键是否为未删除的内置角色。
     */
    boolean existsByIdAndIsStaticTrueAndIsDeletedFalse(Long id);

    /**
     * 根据名称查找角色
     */
    Optional<Role> findByName(String name);

    /**
     * 根据代码查找角色
     */
    Optional<Role> findByCode(String code);

    /**
     * 判断名称是否存在
     */
    boolean existsByName(String name);
}
