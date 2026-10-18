/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.bridge.inpatient.context;

import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.plugin.booking.domain.appointment.command.CancelAppointmentCommand;
import com.zxplat.plugin.booking.domain.appointment.service.AppointmentDomainService;
import com.zxplat.plugin.booking.domain.bed.entity.BedReservationExt;
import com.zxplat.plugin.booking.domain.bed.service.BedReservationDomainService;
import com.zxplat.plugin.booking.domain.bed.service.BedReservationQueryService;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 分床占床后关闭预约扩展（orchestrator 专用，不释 Location）。
 */
@Component
@RequiredArgsConstructor
public class InpatientBedBookingConsumeWriterImpl {

    private final AppointmentDomainService appointmentDomainService;
    private final BedReservationQueryService bedReservationQueryService;
    private final BedReservationDomainService bedReservationDomainService;

    public void consumeReservationForBedAssign(Long appointmentId, Long operatorId) {
        if (appointmentId == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "appointmentId 不能为空");
        }
        BedReservationExt ext = bedReservationQueryService.findByAppointmentId(appointmentId)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.APPOINTMENT_NOT_FOUND, "床位预留扩展不存在"));
        appointmentDomainService.cancelAppointment(this.toCancelCommand(appointmentId, operatorId));
        bedReservationDomainService.softDelete(ext.getId(), operatorId);
    }

    private CancelAppointmentCommand toCancelCommand(
        Long appointmentId,
        Long operatorId) {
        CancelAppointmentCommand command = new CancelAppointmentCommand();
        command.setOperatorId(operatorId);
        CancelAppointmentCommand.CancelAppointmentItemCommand item =
            new CancelAppointmentCommand.CancelAppointmentItemCommand();
        item.setAppointmentId(appointmentId);
        item.setReason("护士站分床占用");
        command.setItem(item);
        return command;
    }
}
