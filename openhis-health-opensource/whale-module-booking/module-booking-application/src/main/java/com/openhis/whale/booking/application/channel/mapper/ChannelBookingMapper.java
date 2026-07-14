/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.channel.mapper;

import com.openhis.whale.booking.application.channel.request.ChannelCancelBookingRequest;
import com.openhis.whale.booking.application.channel.request.ChannelCreateBookingRequest;
import com.openhis.whale.booking.application.channel.request.SaveChannelConfigRequest;
import com.openhis.whale.booking.application.channel.response.ChannelConfigDto;
import com.openhis.whale.booking.application.channel.response.ChannelCreateBookingResponse;
import com.openhis.whale.booking.application.channel.response.ChannelQueryBookingResponse;
import com.openhis.whale.booking.domain.appointment.command.CancelAppointmentCommand;
import com.openhis.whale.booking.domain.appointment.command.CreateAppointmentCommand;
import com.openhis.whale.booking.domain.appointment.entity.Appointment;
import com.openhis.whale.booking.domain.appointment.queries.model.AppointmentBookingDetailModel;
import com.openhis.whale.booking.domain.channel.entity.ChannelConfig;
import com.openhis.whale.booking.domain.healthcheckup.entity.HealthCheckPackage;
import com.openhis.whale.booking.domain.healthcheckup.entity.HealthCheckBookingExt;
import com.openhis.whale.booking.domain.slot.command.AllocateBookingCommand;
import com.openhis.whale.booking.domain.slot.command.ReleaseBookingCommand;
import com.openhis.whale.booking.domain.slot.queries.model.SlotDetailModel;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Component
public class ChannelBookingMapper {

    /**
     * 渠道配置实体 → DTO。
     *
     * @param config 渠道配置实体
     * @return 渠道配置 DTO
     */
    public ChannelConfigDto toChannelConfigDto(ChannelConfig config) {
        ChannelConfigDto dto = new ChannelConfigDto();
        dto.setId(config.getId());
        dto.setChannelCode(config.getChannelCode());
        dto.setChannelName(config.getChannelName());
        dto.setEnabledFlag(config.getEnabledFlag());
        dto.setQuotaPercent(config.getQuotaPercent());
        dto.setRemarkText(config.getNotifyTemplateCode());
        return dto;
    }

    /**
     * 保存渠道配置请求 → 领域实体。
     *
     * @param request 保存渠道配置请求
     * @return 渠道配置实体
     */
    public ChannelConfig toChannelConfigEntity(SaveChannelConfigRequest request) {
        ChannelConfig entity = new ChannelConfig();
        entity.setId(request.getId());
        entity.setOrganizationId(request.getOrganizationId());
        entity.setChannelCode(request.getChannelCode());
        entity.setChannelName(request.getChannelName());
        if (request.getEnabledFlag() != null) {
            entity.setEnabledFlag(request.getEnabledFlag());
        }
        if (request.getQuotaPercent() != null) {
            entity.setQuotaPercent(request.getQuotaPercent());
        }
        entity.setNotifyTemplateCode(request.getRemarkText());
        return entity;
    }

    /**
     * 按关键字过滤渠道配置列表。
     *
     * @param rows 渠道配置 DTO 列表
     * @param keyword 关键字（匹配渠道编码或名称）
     * @return 过滤后的渠道配置 DTO 列表
     */
    public List<ChannelConfigDto> filterByKeyword(List<ChannelConfigDto> rows, String keyword) {
        if (!StringUtils.hasText(keyword) || rows == null) {
            return rows;
        }
        String like = keyword.trim().toLowerCase();
        List<ChannelConfigDto> filtered = new ArrayList<>();
        for (ChannelConfigDto row : rows) {
            if ((row.getChannelCode() != null && row.getChannelCode().toLowerCase().contains(like))
                || (row.getChannelName() != null && row.getChannelName().toLowerCase().contains(like))) {
                filtered.add(row);
            }
        }
        return filtered;
    }

    /**
     * 占号命令。
     *
     * @param slotId 号源主键
     * @param operatorId 操作人主键
     * @return 占号命令
     */
    public AllocateBookingCommand toAllocateBookingCommand(Long slotId, Long operatorId) {
        AllocateBookingCommand command = new AllocateBookingCommand();
        command.setOperatorId(operatorId);
        AllocateBookingCommand.AllocateBookingItemCommand item = new AllocateBookingCommand.AllocateBookingItemCommand();
        item.setSlotId(slotId);
        item.setAllowOverbook(false);
        item.setTentative(false);
        command.setItem(item);
        return command;
    }

    /**
     * 释放占号命令。
     *
     * @param slotId 号源主键
     * @param operatorId 操作人主键
     * @return 释放占号命令
     */
    public ReleaseBookingCommand toReleaseBookingCommand(Long slotId, Long operatorId) {
        ReleaseBookingCommand command = new ReleaseBookingCommand();
        command.setOperatorId(operatorId);
        ReleaseBookingCommand.ReleaseBookingItemCommand item = new ReleaseBookingCommand.ReleaseBookingItemCommand();
        item.setSlotId(slotId);
        item.setReleaseCount(1);
        command.setItem(item);
        return command;
    }

    /**
     * 渠道创建预约请求 → 创建预约命令。
     *
     * @param request 渠道创建预约请求
     * @param slot 目标号源明细
     * @param operatorId 操作人主键
     * @return 创建预约命令
     */
    public CreateAppointmentCommand toChannelCreateAppointmentCommand(
        ChannelCreateBookingRequest request,
        SlotDetailModel slot,
        Long healthcareServiceId,
        java.time.LocalDate bookingDate,
        Long operatorId) {
        CreateAppointmentCommand command = new CreateAppointmentCommand();
        command.setOperatorId(operatorId);
        CreateAppointmentCommand.CreateAppointmentItemCommand item = new CreateAppointmentCommand.CreateAppointmentItemCommand();
        item.setAppointmentKind(request.getAppointmentKind());
        item.setOrganizationId(request.getOrganizationId());
        item.setPatientId(request.getPatientId());
        item.setSlotId(request.getSlotId());
        item.setHealthcareServiceId(healthcareServiceId);
        item.setBookingDate(bookingDate);
        item.setStartInstant(slot.getStartTime());
        item.setEndInstant(slot.getEndTime());
        if (request.getServiceRequestId() != null) {
            item.setAppointmentTypeCode(String.valueOf(request.getServiceRequestId()));
        }
        CreateAppointmentCommand.AppointmentOriginCommand origin = new CreateAppointmentCommand.AppointmentOriginCommand();
        origin.setOriginChannel(request.getChannelCode());
        origin.setOriginOperatorId(operatorId);
        item.setOrigin(origin);
        command.setItem(item);
        return command;
    }

    /**
     * 预约主键 + 体检套餐 → 体检预约扩展实体。
     *
     * @param appointmentId 预约主键
     * @param pkg 体检套餐实体
     * @return 体检预约扩展实体
     */
    public HealthCheckBookingExt toHealthCheckBookingExt(Long appointmentId, HealthCheckPackage pkg) {
        HealthCheckBookingExt ext = new HealthCheckBookingExt();
        ext.setAppointmentId(appointmentId);
        ext.setPackageId(pkg.getId());
        ext.setEstimatedAmount(pkg.getEstimatedAmount());
        ext.setPackageSnapshotJson(pkg.getPackageName());
        return ext;
    }

    /**
     * 预约实体 → 渠道创建预约响应。
     *
     * @param appointment 预约实体
     * @return 渠道创建预约响应
     */
    public ChannelCreateBookingResponse toChannelCreateBookingResponse(Appointment appointment) {
        ChannelCreateBookingResponse response = new ChannelCreateBookingResponse();
        response.setAppointmentId(appointment.getId());
        response.setBusNo(appointment.getBusNo());
        return response;
    }

    /**
     * 渠道取消预约请求 → 取消预约命令。
     *
     * @param request 渠道取消预约请求
     * @param operatorId 操作人主键
     * @return 取消预约命令
     */
    public CancelAppointmentCommand toChannelCancelAppointmentCommand(ChannelCancelBookingRequest request, Long operatorId) {
        CancelAppointmentCommand command = new CancelAppointmentCommand();
        command.setOperatorId(operatorId);
        CancelAppointmentCommand.CancelAppointmentItemCommand item = new CancelAppointmentCommand.CancelAppointmentItemCommand();
        item.setAppointmentId(request.getAppointmentId());
        item.setReason(request.getReason());
        command.setItem(item);
        return command;
    }

    /**
     * 预约详情模型 → 渠道查询预约响应。
     *
     * @param booking 预约详情模型
     * @return 渠道查询预约响应
     */
    public ChannelQueryBookingResponse toChannelQueryBookingResponse(AppointmentBookingDetailModel booking) {
        ChannelQueryBookingResponse response = new ChannelQueryBookingResponse();
        response.setAppointmentId(booking.getId());
        response.setBusNo(booking.getBusNo());
        response.setAppointmentKind(booking.getAppointmentKind());
        response.setPatientId(booking.getPatientId());
        response.setSlotId(booking.getSlotId());
        response.setStartInstant(booking.getStartInstant());
        response.setEndInstant(booking.getEndInstant());
        response.setStatusView(booking.getStatusView());
        return response;
    }
}
