package com.openhis.whale.booking.domain.calendarexception.repository;

import com.openhis.whale.booking.domain.calendarexception.entity.CalendarException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * T3 日历例外仓储。
 * <p>约束：禁止 {@code @Query}；复杂条件在 {@code CalendarExceptionQueryService} 用 Specification 下推。</p>
 */
public interface CalendarExceptionRepository extends JpaRepository<CalendarException, Long>,
    JpaSpecificationExecutor<CalendarException> {

    long countByOverrideScheduleTemplateIdAndIsDeletedFalse(Long overrideScheduleTemplateId);
}
