/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.calendarexception.mapper;

import com.openhis.whale.booking.application.calendarexception.request.SaveCalendarExceptionRequest;
import com.openhis.whale.booking.application.calendarexception.response.CalendarExceptionDto;
import com.openhis.whale.booking.domain.calendarexception.entity.CalendarException;
import org.springframework.stereotype.Component;

/**
 * T3 日历例外应用层映射器。
 * <p>职责：日历例外 Request/Entity/DTO 转换；不含业务门禁与事务。</p>
 */
@Component
public class CalendarExceptionMapper {

    /**
     * 日历例外实体 → 详情/列表 DTO。
     *
     * @param entity 日历例外实体
     * @return 日历例外 DTO
     */
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

    /**
     * 保存日历例外请求 → 领域实体。
     *
     * @param request 保存日历例外请求
     * @return 日历例外领域实体
     */
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
