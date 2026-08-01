/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
     * 批量判断项目是否存在指定状态的调价单（大单提交 pending 互斥，避免逐条 exists）。
     *
     * @param itemTable  项目物理表
     * @param itemIds    项目定义主键集合
     * @param statusEnum 状态 code（如 pending）
     * @return 命中的未删行；无命中为空列表
     */
    List<PriceAdjustment> findByItemTableAndItemIdInAndStatusEnumAndIsDeletedFalse(
        String itemTable,
        List<Long> itemIds,
        String statusEnum
    );

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
