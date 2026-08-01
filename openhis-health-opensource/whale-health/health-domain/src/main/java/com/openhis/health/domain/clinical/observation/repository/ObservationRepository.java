/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.observation.repository;

import com.openhis.health.domain.clinical.observation.entity.Observation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 临床观察实例仓储（DM-CLN-007 §4.5）。
 */
public interface ObservationRepository extends JpaRepository<Observation, Long> {

    /**
     * 按服务申请与小项定义读取幂等键行。
     *
     * @param srId  服务申请主键
     * @param defId 观察定义主键
     * @return 未软删行
     */
    Optional<Observation> findByServiceRequestIdAndDefinitionIdAndIsDeletedFalse(Long srId, Long defId);

    /**
     * 按就诊列出观察实例。
     *
     * @param encounterId 就诊主键
     * @return 未软删列表
     */
    List<Observation> findByEncounterIdAndIsDeletedFalse(Long encounterId);

    /**
     * 按服务申请列出观察实例。
     *
     * @param srId 服务申请主键
     * @return 未软删列表
     */
    List<Observation> findByServiceRequestIdAndIsDeletedFalse(Long srId);
}
