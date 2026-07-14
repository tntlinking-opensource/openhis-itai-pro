/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.episodeofcare.repository;

import com.openhis.health.domain.administration.episodeofcare.entity.EpisodeOfCareEncounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 疗程-就诊关联仓储：持久化疗程与就诊的多对多事实，复杂条件走 Specification。
 * 该仓储仅承载通用 JPA 持久化能力，不在接口层固化场景化筛选条件。
 */
public interface EpisodeOfCareEncounterRepository extends JpaRepository<EpisodeOfCareEncounter, Long>, JpaSpecificationExecutor<EpisodeOfCareEncounter> {
    // 未声明派生方法：按疗程/就诊筛选请使用 Specification，避免无过滤的全表扫描。
    // 若后续新增派生方法，必须同时满足“调用点存在 + 条件下推数据库”的门禁。
}
