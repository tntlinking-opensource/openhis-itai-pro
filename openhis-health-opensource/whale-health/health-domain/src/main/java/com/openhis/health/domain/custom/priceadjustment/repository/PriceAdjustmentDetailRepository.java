/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.priceadjustment.repository;

import com.openhis.health.domain.custom.priceadjustment.entity.PriceAdjustmentDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 调价批号明细仓储（表 {@code cus_price_adjustment_detail}）。
 *
 * <p>子表与主行全量对齐时由领域服务软删旧行后重建；approved 后禁止删改。</p>
 */
public interface PriceAdjustmentDetailRepository extends JpaRepository<PriceAdjustmentDetail, Long> {

    /**
     * 按项目行主键查询未删明细。
     *
     * @param adjustmentId 项目行主键
     * @return 明细列表
     */
    List<PriceAdjustmentDetail> findByAdjustmentIdAndIsDeletedFalse(Long adjustmentId);

    /**
     * 批量按项目行主键查询未删明细（读侧一次 IN 加载，禁止循环内逐条仓储访问）。
     *
     * @param adjustmentIds 项目行主键集合
     * @return 明细列表
     */
    List<PriceAdjustmentDetail> findByAdjustmentIdInAndIsDeletedFalse(List<Long> adjustmentIds);
}
