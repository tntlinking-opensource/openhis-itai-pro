/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.domain.repository;

import com.zxplat.plugin.wi.domain.entity.YbWiReconDaily;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** {@link YbWiReconDaily} 仓储。 */
public interface YbWiReconDailyRepository extends JpaRepository<YbWiReconDaily, Long> {

    Optional<YbWiReconDaily> findFirstByContractIdAndReconDayAndTenantIdAndIsDeletedFalse(
            Long contractId, LocalDate reconDay, Long tenantId);

    List<YbWiReconDaily> findAllByContractIdAndTenantIdAndIsDeletedFalseAndReconDayBetweenOrderByReconDayAsc(
            Long contractId, Long tenantId, LocalDate beginDay, LocalDate endDay);

    List<YbWiReconDaily> findAllByContractIdAndTenantIdAndIsDeletedFalseAndReconDayIn(
            Long contractId, Long tenantId, Collection<LocalDate> reconDays);
}
