/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.custom.priceadjustment.repository;

import com.openhis.health.domain.custom.priceadjustment.entity.PriceAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * 价格调整仓储。
 */
public interface PriceAdjustmentRepository extends JpaRepository<PriceAdjustment, Long> {

    /**
     * 按主键查询。
     * @param id 条件
     * @return 查询结果
     */
    Optional<PriceAdjustment> findById(Long id);

    /**
     * 按条件查询列表。
     * @param definitionId 条件
     * @return 查询结果
     */
    List<PriceAdjustment> findAllByDefinitionIdOrderByAdjustmentTimeDesc(Long definitionId);

    /**
     * 按条件查询列表。
     * @param definitionId 条件
     * @param priceType 条件
     * @return 查询结果
     */
    List<PriceAdjustment> findAllByDefinitionIdAndPriceTypeOrderByAdjustmentTimeDesc(Long definitionId, String priceType);

    List<PriceAdjustment> findAllByDefinitionIdAndPriceTypeAndLotNumberOrderByAdjustmentTimeDesc(
        Long definitionId,
        String priceType,
        String lotNumber
    );

    /**
     * 按条件查询列表。
     * @param startTime 条件
     * @param endTime 条件
     * @return 查询结果
     */
    List<PriceAdjustment> findAllByAdjustmentTimeBetweenOrderByAdjustmentTimeAsc(Date startTime, Date endTime);

    /**
     * 按条件查询列表。
     * @param orgId 条件
     * @param startTime 条件
     * @param endTime 条件
     * @return 查询结果
     */
    List<PriceAdjustment> findAllByOrgIdAndAdjustmentTimeBetweenOrderByAdjustmentTimeAsc(Long orgId, Date startTime, Date endTime);

    List<PriceAdjustment> findAllByOrgIdAndAdjustmentTimeBetweenAndStatusEnumOrderByAdjustmentTimeAsc(
        Long orgId,
        Date startTime,
        Date endTime,
        String statusEnum
    );

    List<PriceAdjustment> findAllByAdjustmentTimeBetweenAndStatusEnumOrderByAdjustmentTimeAsc(
        Date startTime,
        Date endTime,
        String statusEnum
    );
}

