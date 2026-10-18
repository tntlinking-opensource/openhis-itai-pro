/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.supplydelivery.repository;

import com.zxplat.domain.workflow.supplydelivery.entity.SupplyDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 供应发放聚合仓储（表 {@code wkf_supply_delivery}）。
 *
 * <p>约束：禁止 {@code @Query}；过滤（含软删若有）由方法名派生下推数据库。</p>
 *
 * <p>{@code statusEnum} 等参数须为持久化 {@code getCode()} 字符串（B.10.8.1），与实体列一致。</p>
 */
public interface SupplyDeliveryRepository extends JpaRepository<SupplyDelivery, Long> {

    /**
     * 按发放主键集合批量加载。
     *
     * @param idList {@code id} 列表
     * @return 发放行列表
     */
    List<SupplyDelivery> findByIdIn(List<Long> idList);

    /**
     * 按上游供应请求行主键集合加载发放记录。
     *
     * @param requestIdList {@code request_id} 列表
     * @return 发放行列表
     */
    List<SupplyDelivery> findByRequestIdIn(List<Long> requestIdList);

    /**
     * 判断某请求行是否已存在指定状态的发放记录。
     *
     * @param requestId   供应请求行主键
     * @param statusEnum  {@code status_enum} 持久化 code（如 {@link com.zxplat.domain.share.enums.DispenseStatus}）
     * @return 存在则为 {@code true}
     */
    boolean existsByRequestIdAndStatusEnum(Long requestId, String statusEnum);

    /**
     * 在请求主键集合范围内筛选指定状态的发放行。
     *
     * @param requestIdList {@code request_id} 列表
     * @param statusEnum    {@code status_enum} 持久化 code
     * @return 发放行列表
     */
    List<SupplyDelivery> findByRequestIdInAndStatusEnum(List<Long> requestIdList, String statusEnum);
}
