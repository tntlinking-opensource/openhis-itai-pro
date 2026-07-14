/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.mrms.caserecord.repository;

import com.openhis.health.domain.mrms.caserecord.entity.MedcasnoSequence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 病案号流水序列表仓储。
 */
public interface MedcasnoSequenceRepository extends JpaRepository<MedcasnoSequence, Long> {

    /**
     * 按租户与自然年查发号序列表（并发发号行锁）。
     *
     * @param tenantId 租户
     * @param yearValue 自然年 yyyy
     * @return 序列表行
     */
    Optional<MedcasnoSequence> findByTenantIdAndYearValueAndIsDeletedFalse(Long tenantId, Integer yearValue);
}
