/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
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
