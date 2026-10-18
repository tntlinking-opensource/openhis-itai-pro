/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.stopclinic.repository;

import com.zxplat.plugin.booking.domain.stopclinic.entity.StopClinicSlotFreeze;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 停诊预览号源冻结仓储。
 * <p>约束：禁止 {@code @Query}；有效冻结筛选在 {@code StopClinicSlotFreezeDomainService} 完成。</p>
 */
public interface StopClinicSlotFreezeRepository extends JpaRepository<StopClinicSlotFreeze, Long> {

    Optional<StopClinicSlotFreeze> findFirstBySlotIdAndIsDeletedFalse(Long slotId);

    List<StopClinicSlotFreeze> findByScheduleIdAndSlotIdInAndIsDeletedFalse(Long scheduleId, Collection<Long> slotIds);

    List<StopClinicSlotFreeze> findByScheduleIdAndIsDeletedFalse(Long scheduleId);

    List<StopClinicSlotFreeze> findByScheduleIdAndIsDeletedFalseAndExpiresAtAfter(
        Long scheduleId, LocalDateTime now);
}
