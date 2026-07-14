/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.slot.repository;

import com.openhis.whale.booking.domain.slot.entity.Slot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 号源时段仓储接口（DM-WKF-010）。
 */
public interface SlotRepository extends JpaRepository<Slot, Long>, JpaSpecificationExecutor<Slot> {

    /**
     * 按主键加载（仅未软删）。
     *
     * @param id 时段标识
     * @return 时段
     */
    Optional<Slot> findById(Long id);

    /**
     * 查询与给定时间窗相交的时段（用于批量出号的重叠校验）。
     *
     * <p>相交判定：startTime &lt; windowEnd 且 endTime &gt; windowStart。</p>
     *
     * @param scheduleId 日程标识
     * @param windowEnd 时间窗止（exclusive）
     * @param windowStart 时间窗起（exclusive）
     * @return 相交时段
     */
    List<Slot> findByScheduleIdAndStartTimeLessThanAndEndTimeGreaterThan(Long scheduleId, LocalDateTime windowEnd, LocalDateTime windowStart);

    /**
     * 按日程与时间范围查询。
     *
     * @param scheduleId 日程标识
     * @param start 起（包含）
     * @param end 止（不包含）
     * @param statusCodeList 状态码集合
     * @return 时段列表
     */
    List<Slot> findByScheduleIdAndStartTimeGreaterThanEqualAndEndTimeLessThanEqualAndStatusCodeIn(
        Long scheduleId, LocalDateTime start, LocalDateTime end, List<String> statusCodeList);

    /**
     * 按日程与时间范围查询（不限定状态）。
     *
     * @param scheduleId 日程标识
     * @param start 起（包含）
     * @param end 止（不包含）
     * @return 时段列表
     */
    List<Slot> findByScheduleIdAndStartTimeGreaterThanEqualAndEndTimeLessThanEqual(
        Long scheduleId, LocalDateTime start, LocalDateTime end);

    /**
     * 按机构与日期范围分页查询。
     *
     * @param organizationId 机构标识
     * @param startTimeFrom 起（包含）
     * @param startTimeTo 止（不包含）
     * @param pageable 分页
     * @return 分页结果
     */
    Page<Slot> findByOrganizationIdAndStartTimeGreaterThanEqualAndStartTimeLessThan(
        Long organizationId, LocalDateTime startTimeFrom, LocalDateTime startTimeTo, Pageable pageable);

    /**
     * 按机构与精确时段查号源（lazy 物化幂等）。
     */
    Optional<Slot> findFirstByOrganizationIdAndStartTimeAndEndTimeAndIsDeletedFalse(
        Long organizationId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 按日程加载全部未软删号源（停诊回收机构时间窗）。
     *
     * @param scheduleId 日程标识
     * @return 号源列表
     */
    List<Slot> findByScheduleIdAndIsDeletedFalse(Long scheduleId);
}
