/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.chargediscountconfig.repository;

import com.zxplat.domain.custom.chargediscountconfig.entity.ChargeDiscountConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 收费折扣配置仓储（仅本聚合表 {@code cus_charge_discount_config}；禁 {@code @Query}）。
 */
public interface ChargeDiscountConfigRepository
    extends JpaRepository<ChargeDiscountConfig, Long>, JpaSpecificationExecutor<ChargeDiscountConfig> {

    /**
     * 按主键加载未删配置。
     *
     * @param id 主键
     * @return 配置
     */
    Optional<ChargeDiscountConfig> findByIdAndIsDeletedFalse(Long id);

    /**
     * 大类唯一性探测（未删、product_id 空）。
     *
     * @param productTable 产品表
     * @param categoryCode 大类码
     * @return 已存在则 true
     */
    boolean existsByProductTableAndCategoryCodeAndProductIdIsNullAndIsDeletedFalse(
        String productTable, String categoryCode);

    /**
     * 大类唯一性探测（排除自身，修改场景）。
     *
     * @param productTable 产品表
     * @param categoryCode 大类码
     * @param id           排除的主键
     * @return 冲突则 true
     */
    boolean existsByProductTableAndCategoryCodeAndProductIdIsNullAndIdNotAndIsDeletedFalse(
        String productTable, String categoryCode, Long id);

    /**
     * 单项唯一性探测（未删、category_code 空）。
     *
     * @param productTable 产品表
     * @param productId    产品主键
     * @return 已存在则 true
     */
    boolean existsByProductTableAndProductIdAndCategoryCodeIsNullAndIsDeletedFalse(
        String productTable, Long productId);

    /**
     * 单项唯一性探测（排除自身）。
     *
     * @param productTable 产品表
     * @param productId    产品主键
     * @param id           排除的主键
     * @return 冲突则 true
     */
    boolean existsByProductTableAndProductIdAndCategoryCodeIsNullAndIdNotAndIsDeletedFalse(
        String productTable, Long productId, Long id);

    /**
     * 统计未删且指定状态行数（通常传 ACTIVE，供折扣相关显隐判定）。
     *
     * @param statusEnum 状态 code
     * @return 行数
     */
    long countByStatusEnumAndIsDeletedFalse(String statusEnum);

    /**
     * 按表名批量加载 ACTIVE 未删配置，供命中解析（单项+大类一并拉回内存择优）。
     *
     * @param productTables 产品表集合
     * @param statusEnum    状态（通常 ACTIVE）
     * @return 配置列表
     */
    List<ChargeDiscountConfig> findByProductTableInAndStatusEnumAndIsDeletedFalse(
        Collection<String> productTables, String statusEnum);

    /**
     * 按表+产品 id 查 ACTIVE 单项行。
     *
     * @param productTable 产品表
     * @param productId    产品主键
     * @param statusEnum   状态
     * @return 单项配置
     */
    Optional<ChargeDiscountConfig> findFirstByProductTableAndProductIdAndCategoryCodeIsNullAndStatusEnumAndIsDeletedFalse(
        String productTable, Long productId, String statusEnum);

    /**
     * 按表+大类码查 ACTIVE 大类行。
     *
     * @param productTable 产品表
     * @param categoryCode 大类码
     * @param statusEnum   状态
     * @return 大类配置
     */
    Optional<ChargeDiscountConfig> findFirstByProductTableAndCategoryCodeAndProductIdIsNullAndStatusEnumAndIsDeletedFalse(
        String productTable, String categoryCode, String statusEnum);
}
