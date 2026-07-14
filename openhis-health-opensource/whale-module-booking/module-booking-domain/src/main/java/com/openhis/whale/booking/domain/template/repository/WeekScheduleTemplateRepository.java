/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.template.repository;

import com.openhis.whale.booking.domain.template.entity.WeekScheduleTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * TW 周排班模板仓储。
 */
public interface WeekScheduleTemplateRepository extends JpaRepository<WeekScheduleTemplate, Long>,
    JpaSpecificationExecutor<WeekScheduleTemplate> {

    Optional<WeekScheduleTemplate> findFirstByOrganizationIdAndTemplateCodeAndIsDeletedFalse(
        Long organizationId, String templateCode);

    Optional<WeekScheduleTemplate> findFirstByOrganizationIdAndIsDefaultTrueAndIsDeletedFalseAndEnabledTrue(
        Long organizationId);

    List<WeekScheduleTemplate> findByOrganizationIdAndIsDefaultTrueAndIsDeletedFalse(Long organizationId);
}
