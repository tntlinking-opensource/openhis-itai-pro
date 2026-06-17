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
import com.openhis.whale.booking.application.support.BookingOperatorResolver;
import com.openhis.whale.booking.application.support.BookingPagedResultBuilder;
import com.openhis.whale.booking.application.support.MonthCalendarRangeSupport;
import com.openhis.whale.booking.domain.calendarexception.entity.CalendarException;
import com.openhis.whale.booking.domain.calendarexception.service.CalendarExceptionDomainService;
import com.openhis.whale.booking.domain.calendarexception.service.CalendarExceptionQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
 * <p>接入：POST /api/app/calendarException/{methodName}</p>
 */
@Service
@Validated
@Slf4j
@RequiredArgsConstructor
public class CalendarExceptionAppService {

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final CalendarExceptionQueryService calendarExceptionQueryService;
    private final CalendarExceptionDomainService calendarExceptionDomainService;
    private final CalendarExceptionMapper calendarExceptionMapper;
    private final BookingOperatorResolver bookingOperatorResolver;

    /**
     * T3 分页列表。
     */
    public PageCalendarExceptionsResponse pageCalendarExceptions(@Valid PageCalendarExceptionsRequest request) {
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
        return BookingPagedResultBuilder.build(new PageCalendarExceptionsResponse(), rows, page);
    }

    /**
     * T3 详情。
     */
    public CalendarExceptionDto loadCalendarException(@Valid CalendarExceptionIdRequest request) {
        return calendarExceptionMapper.toDto(calendarExceptionQueryService.loadById(request.getId()));
    }

    /**
     * T3 保存。
     */
    @Transactional
    public SaveCalendarExceptionResponse saveCalendarException(@Valid SaveCalendarExceptionRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        CalendarException saved = calendarExceptionDomainService.save(
            calendarExceptionMapper.toEntity(request), operatorId);
        SaveCalendarExceptionResponse response = new SaveCalendarExceptionResponse();
        response.setId(saved.getId());
        return response;
    }

    /**
     * T3 删除。
     */
    @Transactional
    public void deleteCalendarException(@Valid CalendarExceptionIdRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        calendarExceptionDomainService.delete(request.getId(), operatorId);
    }

    /**
     * 月历标记（T3 Tab 左侧）。
     */
    public ListCalendarExceptionsByMonthResponse listCalendarExceptionsByMonth(
        @Valid ListCalendarExceptionsByMonthRequest request) {
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
        return response;
    }
}
