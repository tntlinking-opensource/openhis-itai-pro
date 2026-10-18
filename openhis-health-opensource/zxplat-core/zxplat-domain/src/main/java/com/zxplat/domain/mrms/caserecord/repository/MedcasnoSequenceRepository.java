/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.mrms.caserecord.repository;

import com.zxplat.domain.mrms.caserecord.entity.MedcasnoSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

/**
 * 病案号流水序列表仓储（仅本聚合 {@code mrms_medcasno_sequence}）。
 */
public interface MedcasnoSequenceRepository extends JpaRepository<MedcasnoSequence, Long> {

    /**
     * 按租户与自然年悲观锁加载序列行（并发发号串行化）。
     *
     * <p>规模风险：按唯一键点查一行，无全表扫描。</p>
     *
     * @param tenantId 租户
     * @param yearValue 自然年 yyyy
     * @return 加锁后的序列表行
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MedcasnoSequence> findByTenantIdAndYearValueAndIsDeletedFalse(Long tenantId, Integer yearValue);
}
