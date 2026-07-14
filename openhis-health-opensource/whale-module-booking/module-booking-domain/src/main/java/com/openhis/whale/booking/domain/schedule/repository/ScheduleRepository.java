/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.schedule.repository;

import com.openhis.whale.booking.domain.schedule.entity.Schedule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * 号源日程仓储。
 *
 * <p>约束：禁止 {@code @Query}；过滤条件必须下推数据库侧。</p>
 */
public interface ScheduleRepository extends JpaRepository<Schedule, Long>, JpaSpecificationExecutor<Schedule> {

    /**
     * 按主键查询。
     * @param id 条件
     * @return 查询结果
     */
    Optional<Schedule> findById(Long id);

    /**
     * 按条件查询。
     * @param organizationId 条件
     * @param statusEnum 条件
     * @param pageable 条件
     * @return 查询结果
     */
    Page<Schedule> findByOrganizationIdAndStatusEnum(Long organizationId, String statusEnum, Pageable pageable);
}
