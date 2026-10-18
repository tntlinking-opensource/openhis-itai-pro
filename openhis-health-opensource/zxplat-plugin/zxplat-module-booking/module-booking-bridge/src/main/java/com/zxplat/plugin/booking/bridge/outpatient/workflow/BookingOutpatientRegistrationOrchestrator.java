/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.bridge.outpatient.workflow;

import com.zxplat.plugin.booking.bridge.outpatient.context.OutpatientBookingWriterImpl;
import com.zxplat.plugin.booking.domain.appointment.command.RegistrationCancelReason;
import com.zxplat.plugin.booking.domain.appointment.command.WalkInSlotAllocateCommand;
import com.zxplat.plugin.booking.domain.appointment.entity.Appointment;
import com.zxplat.plugin.booking.domain.appointment.repository.AppointmentRepository;
import com.zxplat.plugin.booking.domain.channel.access.BookingChannelNotifyAccess;
import com.zxplat.plugin.booking.domain.channel.command.BookingChannelNotifyCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 门诊预约登记编排（IF-REG 写回链）。
 *
 * <p>职责：health 挂号回调后关联 encounter、现场取号、自动签到、退号处置；写侧委托
 * {@link OutpatientBookingWriterImpl}。成功后经 {@link BookingChannelNotifyAccess} 占位通知渠道（no-op）。</p>
 */
@Component
@RequiredArgsConstructor
public class BookingOutpatientRegistrationOrchestrator {

    private final OutpatientBookingWriterImpl outpatientBookingWriter;
    private final BookingChannelNotifyAccess channelNotifyAccess;
    private final AppointmentRepository appointmentRepository;

    /**
     * 挂号成功后把 encounter 写回预约。
     *
     * @param appointmentId 预约主键
     * @param encounterId 就诊主键
     */
    public void linkEncounter(Long appointmentId, Long encounterId) {
        outpatientBookingWriter.linkEncounter(appointmentId, encounterId);
        notifyChannel(appointmentId, "BOOKED");
    }

    /**
     * 现场取号：占用当日首个可用 slot。
     *
     * @param command 取号上下文
     * @return 占用的 slotId；无可用号源时 {@code null}
     */
    public Long allocateWalkInSlot(WalkInSlotAllocateCommand command) {
        return outpatientBookingWriter.allocateWalkInSlot(command);
    }

    /**
     * 挂号后按院点策略自动签到。
     *
     * @param appointmentId 预约主键
     * @param encounterId 就诊主键
     */
    public void checkInAfterRegister(Long appointmentId, Long encounterId) {
        outpatientBookingWriter.checkInAfterRegister(appointmentId, encounterId);
        notifyChannel(appointmentId, "BOOKED");
    }

    /**
     * 退号回调：按策略取消预约或仅解除 encounter 关联。
     *
     * @param encounterId 就诊主键
     * @param reason 退号原因
     */
    public void onRegistrationCancelled(Long encounterId, RegistrationCancelReason reason) {
        outpatientBookingWriter.onRegistrationCancelled(encounterId, reason);
        appointmentRepository.findByEncounterId(encounterId)
                .map(Appointment::getId)
                .ifPresent(id -> notifyChannel(id, "CANCELLED"));
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
