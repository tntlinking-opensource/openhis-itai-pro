/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.observationdefinition.repository;

import com.zxplat.domain.clinical.observationdefinition.entity.ObservationDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 临床观察定义仓储（仅本聚合；禁止 @Query）。
 */
public interface ObservationDefinitionRepository
    extends JpaRepository<ObservationDefinition, Long>, JpaSpecificationExecutor<ObservationDefinition> {

    /**
     * 租户内编码是否已被未软删行占用（BR-CLN-OD-01）。
     *
     * @param tenantId 租户
     * @param code     编码
     * @return 存在则为 true
     */
    boolean existsByTenantIdAndCodeAndIsDeletedFalse(Long tenantId, String code);

    /**
     * 按主键加载未软删行。
     *
     * @param id 主键
     * @return 可选实体
     */
    Optional<ObservationDefinition> findByIdAndIsDeletedFalse(Long id);

    /**
     * 按分类列出未软删行（再由服务按状态过滤）。
     *
     * @param categoryCode 分类码
     * @return 列表
     */
    List<ObservationDefinition> findByCategoryCodeAndIsDeletedFalse(String categoryCode);

    /**
     * 主键批量加载（未过滤软删，调用方过滤）。
     *
     * @param ids 主键集合
     * @return 实体列表
     */
    List<ObservationDefinition> findByIdIn(Collection<Long> ids);
}
