/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.activityobservationlink.repository;

import com.openhis.health.domain.custom.activityobservationlink.entity.ActivityObservationLink;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
