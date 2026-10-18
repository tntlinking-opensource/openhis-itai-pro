/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.schedule.repository;

import com.zxplat.plugin.booking.domain.schedule.entity.ScheduleActor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 日程参与者仓储。
 *
 * <p>约束：禁止 {@code @Query}；过滤条件必须下推数据库侧。</p>
 */
public interface ScheduleActorRepository extends JpaRepository<ScheduleActor, Long> {

    /**
     * 按条件查询。
     * @param scheduleId 条件
     * @return 查询结果
     */
    List<ScheduleActor> findByScheduleIdOrderBySortNoAsc(Long scheduleId);

    /**
     * 按条件查询。
     * @param actorType 条件
     * @param actorId 条件
     * @return 查询结果
     */
    List<ScheduleActor> findByActorTypeAndActorId(Integer actorType, Long actorId);

    /**
     * 按条件查询。
     * @param actorType 条件
     * @param actorId 条件
     * @return 查询结果
     */
    List<ScheduleActor> findByActorTypeAndActorIdOrderByScheduleIdAsc(Integer actorType, Long actorId);
}
