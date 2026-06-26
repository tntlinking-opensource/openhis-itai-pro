/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.domain.repository;

import com.openhis.whale.mrms.domain.entity.MedcasnoSequence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 病案号流水序列表仓储。
 */
public interface MedcasnoSequenceRepository extends JpaRepository<MedcasnoSequence, Long> {

    /**
     * 按租户与年度查发号序列表。
     *
     * @param tenantId 租户
     * @param yearValue 年度
     * @return 序列表
     */
    Optional<MedcasnoSequence> findByTenantIdAndYearValueAndIsDeletedFalse(Long tenantId, Integer yearValue);
}
