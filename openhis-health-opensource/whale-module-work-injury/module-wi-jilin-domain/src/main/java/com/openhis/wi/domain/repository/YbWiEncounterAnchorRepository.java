/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.domain.repository;

import com.openhis.wi.domain.entity.YbWiEncounterAnchor;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
