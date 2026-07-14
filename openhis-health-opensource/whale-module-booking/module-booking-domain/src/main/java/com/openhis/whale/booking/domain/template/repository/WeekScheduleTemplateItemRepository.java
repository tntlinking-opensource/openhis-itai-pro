/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.template.repository;

import com.openhis.whale.booking.domain.template.entity.WeekScheduleTemplateItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * TW 周模板明细仓储。
 */
public interface WeekScheduleTemplateItemRepository extends JpaRepository<WeekScheduleTemplateItem, Long> {

    List<WeekScheduleTemplateItem> findByWeekScheduleTemplateIdAndIsDeletedFalseOrderByWeekdayAscSortNoAsc(
        Long weekScheduleTemplateId);

    List<WeekScheduleTemplateItem> findByWeekScheduleTemplateIdAndWeekdayAndIsDeletedFalseAndEnabledTrueOrderBySortNoAsc(
        Long weekScheduleTemplateId, Integer weekday);

    long countByDayScheduleTemplateIdAndIsDeletedFalse(Long dayScheduleTemplateId);
}
