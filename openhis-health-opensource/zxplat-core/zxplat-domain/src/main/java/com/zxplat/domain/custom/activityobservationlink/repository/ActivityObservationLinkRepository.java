/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.activityobservationlink.repository;

import com.zxplat.domain.custom.activityobservationlink.entity.ActivityObservationLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * 项目↔小项绑定仓储（仅本聚合；禁止 @Query）。
 */
public interface ActivityObservationLinkRepository extends JpaRepository<ActivityObservationLink, Long> {

    /**
     * 列出某项目下未软删的绑定行。
     *
     * @param activityDefinitionId 项目 AD 主键
     * @return 链接行列表
     */
    List<ActivityObservationLink> findByActivityDefinitionIdAndIsDeletedFalse(Long activityDefinitionId);

    /**
     * 按小项主键批量列出未软删绑定（目录列表统计「被引用项目数」）。
     *
     * @param observationDefinitionIds 小项 OD 主键集合
     * @return 链接行列表；空集合时由调用方短路
     */
    List<ActivityObservationLink> findByObservationDefinitionIdInAndIsDeletedFalse(
        Collection<Long> observationDefinitionIds);

    /**
     * 按项目主键批量列出未软删绑定（目录列表避免按 AD N+1）。
     *
     * @param activityDefinitionIds 项目 AD 主键集合
     * @return 链接行列表；空集合时由调用方短路
     */
    List<ActivityObservationLink> findByActivityDefinitionIdInAndIsDeletedFalse(
        Collection<Long> activityDefinitionIds);
}
