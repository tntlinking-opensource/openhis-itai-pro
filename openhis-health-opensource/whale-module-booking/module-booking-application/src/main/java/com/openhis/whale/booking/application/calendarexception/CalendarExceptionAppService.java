/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.calendarexception;

import com.openhis.whale.booking.application.calendarexception.mapper.CalendarExceptionMapper;
import com.openhis.whale.booking.application.calendarexception.request.CalendarExceptionIdRequest;
import com.openhis.whale.booking.application.calendarexception.request.ListCalendarExceptionsByMonthRequest;
import com.openhis.whale.booking.application.calendarexception.request.PageCalendarExceptionsRequest;
import com.openhis.whale.booking.application.calendarexception.request.SaveCalendarExceptionRequest;
import com.openhis.whale.booking.application.calendarexception.response.CalendarExceptionDto;
import com.openhis.whale.booking.application.calendarexception.response.CalendarExceptionMonthDayDto;
import com.openhis.whale.booking.application.calendarexception.response.ListCalendarExceptionsByMonthResponse;
import com.openhis.whale.booking.application.calendarexception.response.PageCalendarExceptionsResponse;
import com.openhis.whale.booking.application.calendarexception.response.SaveCalendarExceptionResponse;
import com.openhis.whale.booking.application.share.BookingOperatorResolver;
import com.openhis.whale.booking.application.share.BookingPagedResultBuilder;
import com.openhis.whale.booking.application.share.MonthCalendarRangeSupport;
import com.openhis.whale.booking.domain.calendarexception.entity.CalendarException;
import com.openhis.whale.booking.domain.calendarexception.service.CalendarExceptionDomainService;
import com.openhis.whale.booking.domain.calendarexception.service.CalendarExceptionQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * T3 日历例外应用服务（2.15）。
 *
 * <p>职责边界：节假日/调休/临时停诊等日历例外的查询与维护；供排班生成与可约解析引用。</p>
 * <p>接入：AppServiceControllerRegistrar → {@code POST /api/app/calendarException/{methodName}}。</p>
 */
@Service
@Validated
@Slf4j
@RequiredArgsConstructor
public class CalendarExceptionAppService {

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    /** T3 日历例外读侧。 */
    private final CalendarExceptionQueryService calendarExceptionQueryService;
    /** T3 日历例外写侧。 */
    private final CalendarExceptionDomainService calendarExceptionDomainService;
    /** Request/Entity/DTO 映射。 */
    private final CalendarExceptionMapper calendarExceptionMapper;
    /** 写路径操作人。 */
    private final BookingOperatorResolver bookingOperatorResolver;

    /**
     * T3 分页列表。
     */
    public PageCalendarExceptionsResponse pageCalendarExceptions(@Valid PageCalendarExceptionsRequest request) {
        // Pre：按机构、类型与日期区间分页查询日历例外。
        Page<CalendarException> page = calendarExceptionQueryService.pageCalendarExceptions(
            request.getOrganizationId(),
            request.getExceptionType(),
            request.getRangeStart(),
            request.getRangeEnd(),
            request.getKeyword(),
            PageRequest.of(request.getPageNo() - 1, request.getPageSize()));
        List<CalendarExceptionDto> rows = new ArrayList<>();
        for (CalendarException entity : page.getContent()) {
            rows.add(calendarExceptionMapper.toDto(entity));
        }
        // Asm：映射 DTO 并包装分页结果。
        return BookingPagedResultBuilder.build(new PageCalendarExceptionsResponse(), rows, page);
    }

    /**
     * T3 详情。
     */
    public CalendarExceptionDto loadCalendarException(@Valid CalendarExceptionIdRequest request) {
        // Pre：按主键加载日历例外实体。
        // Asm：映射为详情 DTO。
        return calendarExceptionMapper.toDto(calendarExceptionQueryService.loadById(request.getId()));
    }

    /**
     * T3 保存。
     */
    public SaveCalendarExceptionResponse saveCalendarException(@Valid SaveCalendarExceptionRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Cmd：持久化日历例外。
        CalendarException saved = calendarExceptionDomainService.save(
            calendarExceptionMapper.toEntity(request), operatorId);
        // Asm：返回主键。
        SaveCalendarExceptionResponse response = new SaveCalendarExceptionResponse();
        response.setId(saved.getId());
        return response;
    }

    /**
     * T3 删除。
     */
    public void deleteCalendarException(@Valid CalendarExceptionIdRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Cmd：软删日历例外。
        calendarExceptionDomainService.delete(request.getId(), operatorId);
    }

    /**
     * 月历标记（T3 Tab 左侧）。
     */
    public ListCalendarExceptionsByMonthResponse listCalendarExceptionsByMonth(
        @Valid ListCalendarExceptionsByMonthRequest request) {
        // Pre：解析月历网格区间并构建例外类型标记表。
        LocalDate[] monthRange = MonthCalendarRangeSupport.resolveMonthRange(request.getMonth());
        LocalDate monthStart = monthRange[0];
        LocalDate monthEnd = monthRange[1];
        YearMonth ym = YearMonth.from(monthStart);
        LocalDate[] gridRange = MonthCalendarRangeSupport.resolveGridRange(monthStart, monthEnd);
        Map<java.time.LocalDate, List<String>> markers = calendarExceptionQueryService.buildMonthMarkerMap(
            request.getOrganizationId(), ym);

        ListCalendarExceptionsByMonthResponse response = new ListCalendarExceptionsByMonthResponse();
        LocalDate cursor = gridRange[0];
        while (!cursor.isAfter(gridRange[1])) {
            CalendarExceptionMonthDayDto day = new CalendarExceptionMonthDayDto();
            day.setDate(cursor.format(ISO_DATE));
            day.setInMonth(!cursor.isBefore(monthStart) && !cursor.isAfter(monthEnd));
            List<String> types = markers.get(cursor);
            if (types != null) {
                day.getExceptionTypes().addAll(types);
            }
            response.getDays().add(day);
            cursor = cursor.plusDays(1);
        }
        // Asm：返回月历逐日例外标记响应。
        return response;
    }
}
