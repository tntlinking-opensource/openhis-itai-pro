/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.domain.repository;

import com.zxplat.plugin.wi.domain.entity.YbWiEncounterAnchor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** {@link YbWiEncounterAnchor} 仓储。 */
public interface YbWiEncounterAnchorRepository extends JpaRepository<YbWiEncounterAnchor, Long> {

    /**
     * 按就诊查锚点（未软删由框架过滤）。
     *
     * @param encounterId 就诊 id
     * @return 锚点
     */
    Optional<YbWiEncounterAnchor> findByEncounterId(Long encounterId);

    /**
     * 按就诊集合批量查锚点（对账明细避免 N+1）。
     *
     * @param encounterIds 就诊 id 集合
     * @return 锚点列表
     */
    List<YbWiEncounterAnchor> findByEncounterIdIn(Collection<Long> encounterIds);
}
