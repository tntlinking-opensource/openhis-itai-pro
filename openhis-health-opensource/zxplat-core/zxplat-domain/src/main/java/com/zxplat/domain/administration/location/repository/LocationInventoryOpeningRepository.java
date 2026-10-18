/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.location.repository;

import com.zxplat.domain.administration.location.entity.LocationInventoryOpening;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 库房期初关闭态仓储（{@code adm_location_inventory_opening}）。
 */
public interface LocationInventoryOpeningRepository extends JpaRepository<LocationInventoryOpening, Long> {

    /**
     * 按位置主键加载未软删关闭行。
     *
     * @param locationId 位置主键
     * @return 可选行
     */
    Optional<LocationInventoryOpening> findByLocationIdAndIsDeletedFalse(Long locationId);

    /**
     * 批量按位置主键加载未软删关闭行。
     *
     * @param locationIds 位置主键集合
     * @return 行列表
     */
    List<LocationInventoryOpening> findByLocationIdInAndIsDeletedFalse(Collection<Long> locationIds);
}
