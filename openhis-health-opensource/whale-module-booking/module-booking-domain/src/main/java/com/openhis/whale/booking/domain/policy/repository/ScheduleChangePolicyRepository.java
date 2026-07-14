/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.policy.repository;

import com.openhis.whale.booking.domain.policy.entity.ScheduleChangePolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * 停诊处置策略仓储。
 */
public interface ScheduleChangePolicyRepository extends JpaRepository<ScheduleChangePolicy, Long>,
    JpaSpecificationExecutor<ScheduleChangePolicy> {

    /**
     * 按科室加载有效策略。
     *
     * @param organizationId 科室主键
     * @return 策略
     */
    Optional<ScheduleChangePolicy> findFirstByOrganizationIdAndIsDeletedFalse(Long organizationId);
}
