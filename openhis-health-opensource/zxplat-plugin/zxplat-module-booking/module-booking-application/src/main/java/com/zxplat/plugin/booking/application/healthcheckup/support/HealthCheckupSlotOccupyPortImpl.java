/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.healthcheckup.support;

import com.zxplat.domain.share.extension.booking.HcBookableDayView;
import com.zxplat.domain.share.extension.booking.HcBookableSlotView;
import com.zxplat.domain.share.extension.booking.HcCreateBookingCommand;
import com.zxplat.domain.share.extension.booking.HcCreateBookingResult;
import com.zxplat.domain.share.extension.booking.HealthCheckupSlotOccupyPort;
import com.zxplat.plugin.booking.application.healthcheckup.HealthCheckupSlotOccupyAppService;
import com.zxplat.plugin.booking.application.healthcheckup.dto.HcBookableDayDto;
import com.zxplat.plugin.booking.application.healthcheckup.dto.HcBookableSlotDto;
import com.zxplat.plugin.booking.application.healthcheckup.request.CreateHcSlotBookingRequest;
import com.zxplat.plugin.booking.application.healthcheckup.request.ListHcBookableDaysRequest;
import com.zxplat.plugin.booking.application.healthcheckup.request.ListHcBookableSlotsRequest;
import com.zxplat.plugin.booking.application.healthcheckup.response.CreateHcSlotBookingResponse;
import com.zxplat.plugin.booking.application.healthcheckup.response.ListHcBookableDaysResponse;
import com.zxplat.plugin.booking.application.healthcheckup.response.ListHcBookableSlotsResponse;
import com.zxplat.framework.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 体检号源占号 Port：委托 {@link HealthCheckupSlotOccupyAppService}，供 miniapp BFF 调用。
 */
@Component
@RequiredArgsConstructor
public class HealthCheckupSlotOccupyPortImpl implements HealthCheckupSlotOccupyPort {

    private final HealthCheckupSlotOccupyAppService healthCheckupSlotOccupyAppService;

    /**
     * 查询可约日期并映射为跨模块视图。
     *
     * @param organizationId   机构
     * @param contractTypeCode 合同类型
     * @param campusCode       校区（可空）
     * @param fromDate         起
     * @param toDate           止
     * @return 可约日视图
     */
    @Override
    public List<HcBookableDayView> listBookableDays(
            Long organizationId,
            String contractTypeCode,
            String campusCode,
            LocalDate fromDate,
            LocalDate toDate) {
        // Gate：机构与区间必填
        if (organizationId == null || fromDate == null || toDate == null) {
            throw new ValidationException("查询可约日失败：机构与日期区间不能为空");
        }
        ListHcBookableDaysRequest request = new ListHcBookableDaysRequest();
        request.setOrganizationId(organizationId);
        request.setContractTypeCode(contractTypeCode);
        request.setCampusCode(campusCode);
        request.setFromDate(fromDate);
        request.setToDate(toDate);
        // Pre：委托占号读侧
        ListHcBookableDaysResponse response = healthCheckupSlotOccupyAppService.listBookableDays(request);
        List<HcBookableDayView> views = new ArrayList<>();
        if (response == null || response.getDays() == null) {
            return views;
        }
        for (HcBookableDayDto dto : response.getDays()) {
            HcBookableDayView view = new HcBookableDayView();
            view.setPlanDate(dto.getPlanDate());
            view.setRemaining(dto.getRemaining());
            views.add(view);
        }
        // Asm：返回 SPI 视图
        return views;
    }

    /**
     * 查询某日可约时段并映射为跨模块视图。
     *
     * @param organizationId   机构
     * @param planDate         日期
     * @param contractTypeCode 合同类型
     * @param campusCode       校区（可空）
     * @return 可约时段
     */
    @Override
    public List<HcBookableSlotView> listBookableSlots(
            Long organizationId, LocalDate planDate, String contractTypeCode, String campusCode) {
        // Gate：机构与日期必填
        if (organizationId == null || planDate == null) {
            throw new ValidationException("查询可约时段失败：机构与日期不能为空");
        }
        ListHcBookableSlotsRequest request = new ListHcBookableSlotsRequest();
        request.setOrganizationId(organizationId);
        request.setPlanDate(planDate);
        request.setContractTypeCode(contractTypeCode);
        request.setCampusCode(campusCode);
        ListHcBookableSlotsResponse response = healthCheckupSlotOccupyAppService.listBookableSlots(request);
        List<HcBookableSlotView> views = new ArrayList<>();
        if (response == null || response.getSlots() == null) {
            return views;
        }
        for (HcBookableSlotDto dto : response.getSlots()) {
            HcBookableSlotView view = new HcBookableSlotView();
            view.setDaySlotId(dto.getDaySlotId());
            view.setStartTime(dto.getStartTime());
            view.setEndTime(dto.getEndTime());
            view.setContractTypeCode(contractTypeCode);
            view.setCampusCode(dto.getCampusCode());
            view.setRemaining(dto.getRemaining());
            views.add(view);
        }
        // Asm：返回 SPI 视图
        return views;
    }

    /**
     * 创建占号预约；会话操作人由 AppService 内 BookingOperatorResolver 解析。
     *
     * @param command 下单命令
     * @return 预约结果
     */
    @Override
    public HcCreateBookingResult createBooking(HcCreateBookingCommand command) {
        // Gate：命令与关键主键必填
        if (command == null
            || command.getOrganizationId() == null
            || command.getPatientId() == null
            || command.getPackageId() == null
            || command.getDaySlotId() == null) {
            throw new ValidationException("创建体检预约失败：机构、患者、套餐与时段不能为空");
        }
        CreateHcSlotBookingRequest request = new CreateHcSlotBookingRequest();
        request.setOrganizationId(command.getOrganizationId());
        request.setPatientId(command.getPatientId());
        request.setPackageId(command.getPackageId());
        request.setDaySlotId(command.getDaySlotId());
        request.setContractId(command.getContractId());
        request.setContractTypeCode(command.getContractTypeCode());
        request.setOriginChannel(command.getOriginChannel());
        // Cmd：委托占号写侧
        CreateHcSlotBookingResponse response = healthCheckupSlotOccupyAppService.createBooking(request);
        HcCreateBookingResult result = new HcCreateBookingResult();
        if (response != null) {
            result.setAppointmentId(response.getAppointmentId());
            result.setAppointmentBusNo(response.getBusNo());
        }
        // Asm：返回 SPI 结果
        return result;
    }
}
