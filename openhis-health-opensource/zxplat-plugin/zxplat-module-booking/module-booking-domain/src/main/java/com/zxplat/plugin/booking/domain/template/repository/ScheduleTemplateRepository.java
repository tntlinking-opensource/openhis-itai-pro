/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.template.repository;

import com.zxplat.plugin.booking.domain.template.entity.ScheduleTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * T2 排班日程模板仓储。
 */
public interface ScheduleTemplateRepository extends JpaRepository<ScheduleTemplate, Long>,
    JpaSpecificationExecutor<ScheduleTemplate> {

    /**
     * 按科室与编码查有效模板。
     */
    Optional<ScheduleTemplate> findFirstByOrganizationIdAndTemplateCodeAndIsDeletedFalse(
        Long organizationId, String templateCode);

    long countBySlotProvisionTemplateIdAndIsDeletedFalse(Long slotProvisionTemplateId);
}
