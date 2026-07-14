/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.menu.infrastructure.repository;

import com.openhis.whale.menu.domain.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 菜单Repository
 */
@Repository
public interface MenuRepository extends JpaRepository<Menu, Long>, JpaSpecificationExecutor<Menu> {

    /**
     * 根据代码查找菜单
     */
    Optional<Menu> findByCode(String code);

    /**
     * 判断代码是否存在
     */
    boolean existsByCode(String code);

    /**
     * 查找根菜单（parentId为null）
     */
    List<Menu> findByParentIdIsNullOrderBySortOrderAsc();

    /**
     * 根据父菜单ID查找子菜单（按排序号升序）
     */
    List<Menu> findByParentIdOrderBySortOrderAsc(Long parentId);

    /**
     * 根据父菜单ID查找子菜单
     */
    List<Menu> findByParentId(Long parentId);

}
