/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.waitlist.repository;

import com.zxplat.plugin.booking.domain.waitlist.entity.WaitlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 候补队列仓储。
 * <p>约束：禁止 {@code @Query}；聚合统计在 {@code WaitlistDomainService} 内存分组。</p>
 */
public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, Long> {

    Optional<WaitlistEntry> findByPatientIdAndSlotIdAndStatusAndIsDeletedFalse(
        Long patientId, Long slotId, String status);

    Optional<WaitlistEntry> findFirstBySlotIdAndStatusAndIsDeletedFalseOrderByQueuePositionAsc(
        Long slotId, String status);

    Optional<WaitlistEntry> findTopBySlotIdAndStatusAndIsDeletedFalseOrderByQueuePositionDesc(
        Long slotId, String status);

    List<WaitlistEntry> findBySlotIdAndStatusAndIsDeletedFalseOrderByQueuePositionAsc(
        Long slotId, String status);

    List<WaitlistEntry> findBySlotIdInAndStatusAndIsDeletedFalse(Collection<Long> slotIds, String status);

    long countBySlotIdAndStatusAndIsDeletedFalse(Long slotId, String status);
}
