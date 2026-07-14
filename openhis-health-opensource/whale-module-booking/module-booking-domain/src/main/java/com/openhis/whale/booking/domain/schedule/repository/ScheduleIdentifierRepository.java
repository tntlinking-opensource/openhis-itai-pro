/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.schedule.repository;

import com.openhis.whale.booking.domain.schedule.entity.ScheduleIdentifier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 日程标识仓储。
 *
 * <p>约束：禁止 {@code @Query}；过滤条件必须下推数据库侧。</p>
 */
public interface ScheduleIdentifierRepository extends JpaRepository<ScheduleIdentifier, Long> {

    /**
     * 按条件查询。
     * @param scheduleId 条件
     * @return 查询结果
     */
    List<ScheduleIdentifier> findByScheduleId(Long scheduleId);
}
