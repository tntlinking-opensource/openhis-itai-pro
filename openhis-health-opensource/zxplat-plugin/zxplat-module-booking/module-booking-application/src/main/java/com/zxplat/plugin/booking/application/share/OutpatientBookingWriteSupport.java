/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.share;

import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.plugin.booking.application.register.mapper.BookingRegisterMapper;
import com.zxplat.plugin.booking.domain.appointment.command.CreateAppointmentCommand;
import com.zxplat.plugin.booking.domain.appointment.entity.Appointment;
import com.zxplat.plugin.booking.domain.appointment.service.AppointmentDomainService;
import com.zxplat.plugin.booking.domain.appointment.service.AppointmentQueryService;
import com.zxplat.plugin.booking.domain.slot.service.SlotDomainService;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 门诊预约占号 + 建 Appointment 编排（GAP-BKG-001：唯一冲突时补偿释号）。
 */
@Component
@RequiredArgsConstructor
public class OutpatientBookingWriteSupport {

    private final AppointmentQueryService appointmentQueryService;
    private final SlotDomainService slotDomainService;
    private final AppointmentDomainService appointmentDomainService;
    private final BookingRegisterMapper bookingRegisterMapper;
    private final BookingConcurrencySupport bookingConcurrencySupport;

    /**
     * 读 Gate：同日同患者同服务不可重复预约。
     */
    public void assertNoDuplicateOutpatientBooking(
        Long patientId,
        LocalDate bookingDate,
        Long healthcareServiceId,
        Long excludeAppointmentId) {
        if (healthcareServiceId == null) {
            return;
        }
        if (appointmentQueryService.findDuplicateOutpatientBooking(
            patientId, bookingDate, healthcareServiceId, excludeAppointmentId).isPresent()) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_RULE_CONFLICT, "同日同患者同服务重复预约");
        }
    }

    /**
     * 占号并创建预约；创建失败时释放已占号源。
     */
    public Appointment allocateAndCreate(CreateAppointmentCommand command, Long slotId, Long operatorId) {
        slotDomainService.allocateBooking(bookingRegisterMapper.toAllocateBookingCommand(slotId, operatorId));
        try {
            return appointmentDomainService.create(command);
        } catch (RuntimeException ex) {
            slotDomainService.releaseBooking(bookingRegisterMapper.toReleaseBookingCommand(slotId, operatorId));
            if (bookingConcurrencySupport.isOutpatientBookingDuplicate(ex)) {
                throw bookingConcurrencySupport.duplicateOutpatientBookingException();
            }
            throw ex;
        }
    }
}
