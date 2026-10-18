/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.encounterhistory.repository;

import com.zxplat.domain.custom.encounterhistory.entity.EncounterHistory;
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
