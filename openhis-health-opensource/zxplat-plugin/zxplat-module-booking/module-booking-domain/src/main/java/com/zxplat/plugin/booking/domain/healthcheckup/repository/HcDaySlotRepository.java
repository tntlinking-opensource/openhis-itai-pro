/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.healthcheckup.repository;

import com.zxplat.plugin.booking.domain.healthcheckup.entity.HcDaySlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 体检日时段配额仓储。
 */
public interface HcDaySlotRepository extends JpaRepository<HcDaySlot, Long>, JpaSpecificationExecutor<HcDaySlot> {

    List<HcDaySlot> findByOrganizationIdAndPlanDateAndIsDeletedFalseOrderByStartTimeAsc(
        Long organizationId, LocalDate planDate);

    List<HcDaySlot> findByDayPlanIdAndIsDeletedFalse(Long dayPlanId);

    Optional<HcDaySlot> findByIdAndIsDeletedFalse(Long id);

    List<HcDaySlot> findByOrganizationIdAndPlanDateInAndIsDeletedFalse(
        Long organizationId, Collection<LocalDate> planDates);

    Optional<HcDaySlot> findByOrganizationIdAndPlanDateAndStartTimeAndEndTimeAndContractTypeCodeAndCampusCodeAndIsDeletedFalse(
        Long organizationId, LocalDate planDate, LocalTime startTime, LocalTime endTime,
        String contractTypeCode, String campusCode);

    Optional<HcDaySlot> findByOrganizationIdAndPlanDateAndStartTimeAndEndTimeAndContractTypeCodeAndCampusCodeIsNullAndIsDeletedFalse(
        Long organizationId, LocalDate planDate, LocalTime startTime, LocalTime endTime, String contractTypeCode);
}
