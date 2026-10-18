/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.bridge.inpatient.workflow;

import com.zxplat.plugin.booking.bridge.inpatient.context.InpatientBedBookingConsumeWriterImpl;
import com.zxplat.plugin.booking.domain.appointment.repository.AppointmentRepository;
import com.zxplat.plugin.booking.domain.channel.access.BookingChannelNotifyAccess;
import com.zxplat.plugin.booking.domain.channel.command.BookingChannelNotifyCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 住院床位预约消费编排（分床占床后关闭预留）。
 *
 * <p>职责：护士站分床成功后取消床位预约并软删扩展；成功后经 {@link BookingChannelNotifyAccess} 占位通知渠道。</p>
 */
@Component
@RequiredArgsConstructor
public class BookingInpatientBedConsumeOrchestrator {

    private final InpatientBedBookingConsumeWriterImpl inpatientBedBookingConsumeWriter;
    private final BookingChannelNotifyAccess channelNotifyAccess;
    private final AppointmentRepository appointmentRepository;

    /**
     * 分床占床后消费床位预约（取消 Appointment + 软删 BedReservationExt）。
     *
     * @param appointmentId 预约主键
     * @param operatorId 操作者主键
     */
    public void consumeReservationForBedAssign(Long appointmentId, Long operatorId) {
        inpatientBedBookingConsumeWriter.consumeReservationForBedAssign(appointmentId, operatorId);
        notifyChannel(appointmentId, "CANCELLED");
    }

    private void notifyChannel(Long appointmentId, String eventType) {
        if (appointmentId == null) {
            return;
        }
        BookingChannelNotifyCommand command = new BookingChannelNotifyCommand();
        command.setAppointmentId(appointmentId);
        command.setEventType(eventType);
        appointmentRepository.findById(appointmentId).ifPresent(appointment -> {
            if (appointment.getOrigin() != null) {
                command.setChannelCode(appointment.getOrigin().getOriginChannel());
            }
        });
        channelNotifyAccess.notify(command);
    }
}
