package com.openhis.whale.booking.domain.calendarexception.repository;

import com.openhis.whale.booking.domain.calendarexception.entity.CalendarException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * T3 日历例外仓储。
 */
public interface CalendarExceptionRepository extends JpaRepository<CalendarException, Long>,
    JpaSpecificationExecutor<CalendarException> {

    @Query("""
        SELECT e FROM CalendarException e
        WHERE e.isDeleted = false
          AND e.autoApplyOnGenerate = true
          AND e.exceptionDate <= :date
          AND (e.rangeEnd IS NULL OR e.rangeEnd >= :date)
          AND (e.organizationId IS NULL OR e.organizationId = :organizationId)
        ORDER BY CASE WHEN e.organizationId IS NULL THEN 1 ELSE 0 END, e.id DESC
        """)
    List<CalendarException> findApplicableForDate(
        @Param("organizationId") Long organizationId,
        @Param("date") LocalDate date);

    @Query("""
        SELECT e FROM CalendarException e
        WHERE e.isDeleted = false
          AND e.exceptionDate <= :rangeEnd
          AND (e.rangeEnd IS NULL OR e.rangeEnd >= :rangeStart)
          AND (e.organizationId IS NULL OR e.organizationId = :organizationId)
        ORDER BY e.exceptionDate ASC, e.id ASC
        """)
    List<CalendarException> findOverlappingRange(
        @Param("organizationId") Long organizationId,
        @Param("rangeStart") LocalDate rangeStart,
        @Param("rangeEnd") LocalDate rangeEnd);
}
