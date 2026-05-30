/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.custom.encounterhistory.repository;

import com.openhis.health.domain.custom.encounterhistory.entity.EncounterHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 就诊历史里程碑仓储。
 */
public interface EncounterHistoryRepository extends JpaRepository<EncounterHistory, Long> {

    /**
     * 按主键查询。
     * @param id 条件
     * @return 查询结果
     */
    Optional<EncounterHistory> findById(Long id);

    /**
     * 按条件查询列表。
     * @param encounterId 条件
     * @return 查询结果
     */
    List<EncounterHistory> findAllByEncounterIdOrderByCreatedAtAsc(Long encounterId);

    /**
     * 按就诊主键集合批量查询里程碑（读侧 ON_HOLD 分流装配用）。
     *
     * @param encounterIds 就诊主键集合
     * @return 里程碑行
     */
    List<EncounterHistory> findAllByEncounterIdIn(Collection<Long> encounterIds);

    /**
     * 按条件查询首条。
     * @param encounterId 条件
     * @param name 条件
     * @return 查询结果
     */
    Optional<EncounterHistory> findFirstByEncounterIdAndNameOrderByCreatedAtDesc(Long encounterId, String name);

    /**
     * 按条件查询列表。
     * @param encounterId 条件
     * @param name 条件
     * @return 查询结果
     */
    List<EncounterHistory> findAllByEncounterIdAndNameOrderByCreatedAtDesc(Long encounterId, String name);
}

