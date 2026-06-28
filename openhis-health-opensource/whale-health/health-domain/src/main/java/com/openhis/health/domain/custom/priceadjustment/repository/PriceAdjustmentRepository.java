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
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * 调价项目行仓储（表 {@code cus_price_adjustment}）。
 *
 * <p>同一 {@code bus_no} 可多行；默认查询须带 {@code isDeleted=false} 派生方法。</p>
 */
public interface PriceAdjustmentRepository extends JpaRepository<PriceAdjustment, Long>, JpaSpecificationExecutor<PriceAdjustment> {

    /**
     * 按调价单号查询未删行。
     *
     * @param busNo 调价单号
     * @return 项目行列表
     */
    List<PriceAdjustment> findByBusNoAndIsDeletedFalse(String busNo);

    /**
     * 判断项目是否存在指定状态的调价单（pending 互斥 / 临床阻断）。
     *
     * @param itemTable  项目物理表
     * @param itemId     项目定义主键
     * @param statusEnum 状态 code（如 pending）
     * @return 存在未删匹配行时为 true
     */
    boolean existsByItemTableAndItemIdAndStatusEnumAndIsDeletedFalse(String itemTable, Long itemId, String statusEnum);

    /**
     * 按机构与提交时间区间查询。
     *
     * @param orgId      机构
     * @param startTime  开始
     * @param endTime    结束
     * @param statusEnum 状态过滤
     * @return 列表
     */
    List<PriceAdjustment> findByOrgIdAndSubmitTimeBetweenAndStatusEnumAndIsDeletedFalseOrderBySubmitTimeDesc(
        Long orgId,
        Date startTime,
        Date endTime,
        String statusEnum
    );

    /**
     * 按状态查询行（审批工作台全量加载后在内存按单号分页）。
     *
     * @param statusEnum 状态 code
     * @return 匹配行列表，按提交时间升序
     */
    List<PriceAdjustment> findByStatusEnumAndIsDeletedFalseOrderBySubmitTimeAsc(String statusEnum);

    /**
     * 按主键查询未删行。
     *
     * @param id 主键
     * @return 行
     */
    Optional<PriceAdjustment> findByIdAndIsDeletedFalse(Long id);

    /**
     * 按项目查询已生效历史。
     *
     * @param itemTable  项目表
     * @param itemId     项目主键
     * @param statusEnum 已生效状态
     * @return 列表
     */
    List<PriceAdjustment> findByItemTableAndItemIdAndStatusEnumAndIsDeletedFalseOrderByApproveTimeDesc(
        String itemTable,
        Long itemId,
        String statusEnum
    );

    /**
     * 按机构与提交时间区间查询（不限状态）。
     *
     * @param orgId      机构
     * @param startTime  开始
     * @param endTime    结束
     * @return 列表
     */
    List<PriceAdjustment> findByOrgIdAndSubmitTimeBetweenAndIsDeletedFalseOrderBySubmitTimeDesc(
        Long orgId,
        Date startTime,
        Date endTime
    );
}
