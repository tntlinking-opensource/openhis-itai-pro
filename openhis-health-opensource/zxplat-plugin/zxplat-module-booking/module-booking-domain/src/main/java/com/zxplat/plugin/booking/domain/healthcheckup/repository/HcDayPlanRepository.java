/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.healthcheckup.repository;

import com.zxplat.plugin.booking.domain.healthcheckup.entity.HcDayPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 体检号源日计划仓储。
 */
public interface HcDayPlanRepository extends JpaRepository<HcDayPlan, Long>, JpaSpecificationExecutor<HcDayPlan> {

    Optional<HcDayPlan> findByOrganizationIdAndPlanDateAndIsDeletedFalse(Long organizationId, LocalDate planDate);

    List<HcDayPlan> findByOrganizationIdAndPlanDateBetweenAndIsDeletedFalseOrderByPlanDateAsc(
        Long organizationId, LocalDate fromDate, LocalDate toDate);
}
