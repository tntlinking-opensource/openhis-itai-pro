package com.openhis.whale.booking.application.calendarexception.mapper;

import com.openhis.whale.booking.application.calendarexception.request.SaveCalendarExceptionRequest;
import com.openhis.whale.booking.application.calendarexception.response.CalendarExceptionDto;
import com.openhis.whale.booking.domain.calendarexception.entity.CalendarException;
import org.springframework.stereotype.Component;

/**
 * T3 日历例外映射。
 */
@Component
public class CalendarExceptionMapper {

    public CalendarExceptionDto toDto(CalendarException entity) {
        CalendarExceptionDto dto = new CalendarExceptionDto();
        dto.setId(entity.getId());
        dto.setOrganizationId(entity.getOrganizationId());
        dto.setExceptionDate(entity.getExceptionDate());
        dto.setRangeEnd(entity.getRangeEnd());
        dto.setExceptionType(entity.getExceptionType());
        dto.setOverrideScheduleTemplateId(entity.getOverrideScheduleTemplateId());
        dto.setReason(entity.getReason());
        dto.setAutoApplyOnGenerate(entity.getAutoApplyOnGenerate());
        return dto;
    }

    public CalendarException toEntity(SaveCalendarExceptionRequest request) {
        CalendarException entity = new CalendarException();
        entity.setId(request.getId());
        entity.setOrganizationId(request.getOrganizationId());
        entity.setExceptionDate(request.getExceptionDate());
        entity.setRangeEnd(request.getRangeEnd());
        entity.setExceptionType(request.getExceptionType());
        entity.setOverrideScheduleTemplateId(request.getOverrideScheduleTemplateId());
        entity.setReason(request.getReason());
        entity.setAutoApplyOnGenerate(request.getAutoApplyOnGenerate());
        return entity;
    }
}
