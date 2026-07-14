/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.encounter.repository;

import com.openhis.health.domain.administration.encounter.entity.EncounterReason;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 就诊原因仓储。
 */
public interface EncounterReasonRepository extends JpaRepository<EncounterReason, Long> {

    /**
     * 查询就诊下原因记录。
     *
     * @param encounterId 就诊标识
     * @return 原因列表
     */
    List<EncounterReason> findAllByEncounterIdOrderByCreatedAtAsc(Long encounterId);
}
