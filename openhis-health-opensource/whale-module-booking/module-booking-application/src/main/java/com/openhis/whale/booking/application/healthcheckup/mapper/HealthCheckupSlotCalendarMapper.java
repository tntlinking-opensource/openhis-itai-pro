/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.healthcheckup.mapper;

import com.openhis.whale.booking.application.healthcheckup.dto.HcBookableSlotDto;
import com.openhis.whale.booking.application.healthcheckup.dto.HcDayPlanDto;
import com.openhis.whale.booking.application.healthcheckup.dto.HcDaySlotDto;
import com.openhis.whale.booking.application.healthcheckup.dto.HcDayTypeDto;
import com.openhis.whale.booking.application.healthcheckup.request.CreateHcSlotBookingRequest;
import com.openhis.whale.booking.application.healthcheckup.request.SaveHcSlotTemplateRequest;
import com.openhis.whale.booking.application.healthcheckup.response.CreateHcSlotBookingResponse;
import com.openhis.whale.booking.application.healthcheckup.response.HcSlotTemplateDto;
import com.openhis.whale.booking.domain.appointment.command.CreateAppointmentCommand;
import com.openhis.whale.booking.domain.appointment.entity.Appointment;
import com.openhis.whale.booking.domain.healthcheckup.entity.HcDayPlan;
import com.openhis.whale.booking.domain.healthcheckup.entity.HcDaySlot;
import com.openhis.whale.booking.domain.healthcheckup.entity.HcDayType;
import com.openhis.whale.booking.domain.healthcheckup.entity.HcSlotTemplate;
import com.openhis.whale.booking.domain.healthcheckup.entity.HealthCheckBookingExt;
import com.openhis.whale.booking.domain.healthcheckup.entity.HealthCheckPackage;
import com.openhis.whale.booking.domain.healthcheckup.support.HcSlotCalendarJsonSupport;
import com.openhis.whale.booking.domain.share.AppointmentKindConstants;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 体检号源日历 Request/Entity/DTO 映射。
 */
@Component
public class HealthCheckupSlotCalendarMapper {

    private static final String HEALTH_CHECKUP_BOOKING_KIND = AppointmentKindConstants.HEALTH_CHECKUP_BOOKING;
    private static final String DEFAULT_ORIGIN_CHANNEL = "WINDOW";

    /**
     * 模板实体 → DTO。
     *
     * @param entity 模板实体
     * @return 模板 DTO
     */
    public HcSlotTemplateDto toHcSlotTemplateDto(HcSlotTemplate entity) {
        HcSlotTemplateDto dto = new HcSlotTemplateDto();
        dto.setId(entity.getId());
        dto.setOrganizationId(entity.getOrganizationId());
        dto.setSlotMinutes(entity.getSlotMinutes());
        dto.setAmStart(entity.getAmStart());
        dto.setAmEnd(entity.getAmEnd());
        dto.setPmStart(entity.getPmStart());
        dto.setPmEnd(entity.getPmEnd());
        dto.setCapacityByType(HcSlotCalendarJsonSupport.parseCapacityMap(entity.getCapacityJson()));
        dto.setRestWeekdays(HcSlotCalendarJsonSupport.parseRestWeekdays(entity.getRestWeekdays()));
        dto.setCampusCodes(HcSlotCalendarJsonSupport.parseCampusCodes(entity.getCampusCodesJson()));
        return dto;
    }

    /**
     * 保存模板请求 → 模板实体。
     *
     * @param request 保存模板请求
     * @return 模板实体
     */
    public HcSlotTemplate toHcSlotTemplateEntity(SaveHcSlotTemplateRequest request) {
        HcSlotTemplate entity = new HcSlotTemplate();
        entity.setOrganizationId(request.getOrganizationId());
        entity.setSlotMinutes(request.getSlotMinutes());
        entity.setAmStart(request.getAmStart());
        entity.setAmEnd(request.getAmEnd());
        entity.setPmStart(request.getPmStart());
        entity.setPmEnd(request.getPmEnd());
        entity.setCapacityJson(HcSlotCalendarJsonSupport.writeCapacityMap(request.getCapacityByType()));
        entity.setRestWeekdays(HcSlotCalendarJsonSupport.writeRestWeekdays(request.getRestWeekdays()));
        entity.setCampusCodesJson(HcSlotCalendarJsonSupport.writeCampusCodes(request.getCampusCodes()));
        return entity;
    }

    /**
     * 日计划实体 → DTO。
     *
     * @param entity 日计划实体
     * @return 日计划 DTO
     */
    public HcDayPlanDto toHcDayPlanDto(HcDayPlan entity) {
        HcDayPlanDto dto = new HcDayPlanDto();
        dto.setId(entity.getId());
        dto.setOrganizationId(entity.getOrganizationId());
        dto.setPlanDate(entity.getPlanDate());
        dto.setRestFlag(entity.getRestFlag());
        dto.setRemark(entity.getRemark());
        return dto;
    }

    /**
     * 日类型实体 → DTO。
     *
     * @param entity 日类型实体
     * @return 日类型 DTO
     */
    public HcDayTypeDto toHcDayTypeDto(HcDayType entity) {
        HcDayTypeDto dto = new HcDayTypeDto();
        dto.setId(entity.getId());
        dto.setContractTypeCode(entity.getContractTypeCode());
        return dto;
    }

    /**
     * 日类型实体列表 → DTO 列表。
     *
     * @param entities 日类型实体列表
     * @return 日类型 DTO 列表
     */
    public List<HcDayTypeDto> toHcDayTypeDtos(List<HcDayType> entities) {
        List<HcDayTypeDto> rows = new ArrayList<>();
        for (HcDayType entity : entities) {
            rows.add(this.toHcDayTypeDto(entity));
        }
        return rows;
    }

    /**
     * 时段配额实体 → DTO。
     *
     * @param entity 时段配额实体
     * @return 时段配额 DTO
     */
    public HcDaySlotDto toHcDaySlotDto(HcDaySlot entity) {
        HcDaySlotDto dto = new HcDaySlotDto();
        dto.setId(entity.getId());
        dto.setDayPlanId(entity.getDayPlanId());
        dto.setPlanDate(entity.getPlanDate());
        dto.setStartTime(entity.getStartTime());
        dto.setEndTime(entity.getEndTime());
        dto.setContractTypeCode(entity.getContractTypeCode());
        dto.setCampusCode(entity.getCampusCode());
        dto.setCapacity(entity.getCapacity());
        dto.setBookedCount(entity.getBookedCount());
        dto.setRemaining(entity.remainingCount());
        dto.setOpenFlag(entity.getOpenFlag());
        return dto;
    }

    /**
     * 时段配额实体列表 → DTO 列表。
     *
     * @param entities 时段配额实体列表
     * @return 时段配额 DTO 列表
     */
    public List<HcDaySlotDto> toHcDaySlotDtos(List<HcDaySlot> entities) {
        List<HcDaySlotDto> rows = new ArrayList<>();
        for (HcDaySlot entity : entities) {
            rows.add(this.toHcDaySlotDto(entity));
        }
        return rows;
    }

    /**
     * 时段配额实体 → 可约时段 DTO。
     *
     * @param entity 时段配额实体
     * @return 可约时段 DTO
     */
    public HcBookableSlotDto toHcBookableSlotDto(HcDaySlot entity) {
        HcBookableSlotDto dto = new HcBookableSlotDto();
        dto.setDaySlotId(entity.getId());
        dto.setStartTime(entity.getStartTime());
        dto.setEndTime(entity.getEndTime());
        dto.setCampusCode(entity.getCampusCode());
        dto.setRemaining(entity.remainingCount());
        return dto;
    }

    /**
     * 号源预约请求 + 时段配额 → 创建预约命令（不占用 wkf_slot）。
     *
     * @param request 创建预约请求
     * @param daySlot 时段配额实体
     * @param operatorId 操作人
     * @return 创建预约命令
     */
    public CreateAppointmentCommand toHcSlotBookingAppointmentCommand(
        CreateHcSlotBookingRequest request,
        HcDaySlot daySlot,
        Long operatorId) {
        CreateAppointmentCommand command = new CreateAppointmentCommand();
        command.setOperatorId(operatorId);
        CreateAppointmentCommand.CreateAppointmentItemCommand item = new CreateAppointmentCommand.CreateAppointmentItemCommand();
        item.setAppointmentKind(HEALTH_CHECKUP_BOOKING_KIND);
        item.setOrganizationId(request.getOrganizationId());
        item.setPatientId(request.getPatientId());
        item.setBookingDate(daySlot.getPlanDate());
        LocalDateTime startInstant = LocalDateTime.of(daySlot.getPlanDate(), daySlot.getStartTime());
        LocalDateTime endInstant = LocalDateTime.of(daySlot.getPlanDate(), daySlot.getEndTime());
        item.setStartInstant(startInstant);
        item.setEndInstant(endInstant);
        CreateAppointmentCommand.AppointmentOriginCommand origin = new CreateAppointmentCommand.AppointmentOriginCommand();
        String channel = request.getOriginChannel();
        if (channel == null || channel.trim().isEmpty()) {
            channel = DEFAULT_ORIGIN_CHANNEL;
        }
        origin.setOriginChannel(channel.trim());
        origin.setOriginOperatorId(operatorId);
        item.setOrigin(origin);
        command.setItem(item);
        return command;
    }

    /**
     * 预约 + 套餐 + 号源扩展字段 → 体检预约扩展实体。
     *
     * @param appointmentId 预约主键
     * @param request 创建预约请求
     * @param resolvedContractTypeCode 解析后的合同类型编码
     * @param campusCode 校区编码
     * @param pkg 体检套餐实体
     * @return 体检预约扩展实体
     */
    public HealthCheckBookingExt toHcSlotBookingExt(
        Long appointmentId,
        CreateHcSlotBookingRequest request,
        String resolvedContractTypeCode,
        String campusCode,
        HealthCheckPackage pkg) {
        HealthCheckBookingExt ext = new HealthCheckBookingExt();
        ext.setAppointmentId(appointmentId);
        ext.setPackageId(request.getPackageId());
        ext.setContractId(request.getContractId());
        ext.setContractTypeCode(resolvedContractTypeCode);
        ext.setCampusCode(campusCode);
        ext.setDaySlotId(request.getDaySlotId());
        if (pkg != null) {
            ext.setEstimatedAmount(pkg.getEstimatedAmount());
            ext.setPackageSnapshotJson(pkg.getPackageName());
        }
        return ext;
    }

    /**
     * 预约实体 → 创建号源预约响应。
     *
     * @param appointment 预约实体
     * @return 创建号源预约响应
     */
    public CreateHcSlotBookingResponse toCreateHcSlotBookingResponse(Appointment appointment) {
        CreateHcSlotBookingResponse response = new CreateHcSlotBookingResponse();
        response.setAppointmentId(appointment.getId());
        response.setBusNo(appointment.getBusNo());
        return response;
    }
}
