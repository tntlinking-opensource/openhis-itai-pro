package com.openhis.whale.booking.application.support;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.booking.application.register.mapper.BookingRegisterMapper;
import com.openhis.whale.booking.application.register.request.CreateOutpatientBookingRequest;
import com.openhis.whale.booking.domain.appointment.command.CancelAppointmentCommand;
import com.openhis.whale.booking.domain.appointment.command.CreateAppointmentCommand;
import com.openhis.whale.booking.domain.appointment.entity.Appointment;
import com.openhis.whale.booking.domain.appointment.service.AppointmentDomainService;
import com.openhis.whale.booking.domain.schedule.queries.model.ScheduleCalendarItemModel;
import com.openhis.whale.booking.domain.schedule.queries.model.ScheduleDetailModel;
import com.openhis.whale.booking.domain.schedule.service.ScheduleQueryService;
import com.openhis.whale.booking.domain.share.BookingDateSupport;
import com.openhis.whale.booking.domain.slot.queries.model.SlotDetailModel;
import com.openhis.whale.booking.domain.slot.service.SlotDomainService;
import com.openhis.whale.booking.domain.slot.service.SlotQueryService;
import com.openhis.whale.booking.domain.waitlist.entity.WaitlistEntry;
import com.openhis.whale.booking.domain.waitlist.service.WaitlistDomainService;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 候补自动提升（GAP-BKG-005：slot 乐观锁 + 候补条件更新）。
 */
@Component
@RequiredArgsConstructor
public class OutpatientWaitlistPromoteSupport {

    private static final String SLOT_BUSY_UNAVAILABLE = "busy-unavailable";

    private final SlotQueryService slotQueryService;
    private final ScheduleQueryService scheduleQueryService;
    private final WaitlistDomainService waitlistDomainService;
    private final OutpatientBookingWriteSupport outpatientBookingWriteSupport;
    private final BookingRegisterMapper bookingRegisterMapper;
    private final SlotDomainService slotDomainService;
    private final AppointmentDomainService appointmentDomainService;
    private final BookingAuditSupport bookingAuditSupport;

    /**
     * slot 释放容量后按 queue_position 提升队首候补为正式预约。
     */
    public Optional<Appointment> tryPromoteWaitlist(Long slotId, Long operatorId) {
        if (slotId == null) {
            return Optional.empty();
        }
        SlotDetailModel slot = slotQueryService.loadSlotById(slotId);
        if (this.isSlotFull(slot) || SLOT_BUSY_UNAVAILABLE.equals(slot.getStatusCode())) {
            return Optional.empty();
        }
        Optional<WaitlistEntry> next = waitlistDomainService.findNextWaiting(slotId);
        if (next.isEmpty()) {
            return Optional.empty();
        }
        WaitlistEntry entry = next.get();
        ScheduleCalendarItemModel scheduleMeta = scheduleQueryService.loadCalendarItemById(slot.getScheduleId());
        Long healthcareServiceId = scheduleMeta.getHealthcareServiceId();
        LocalDate bookingDate = BookingDateSupport.resolveBookingDate(slot.getStartTime());
        if (healthcareServiceId != null) {
            try {
                outpatientBookingWriteSupport.assertNoDuplicateOutpatientBooking(
                    entry.getPatientId(), bookingDate, healthcareServiceId, null);
            } catch (BusinessException ex) {
                waitlistDomainService.cancel(entry.getId(), "同日重复预约，自动跳过", operatorId);
                return this.tryPromoteWaitlist(slotId, operatorId);
            }
        }
        CreateOutpatientBookingRequest promoteRequest = new CreateOutpatientBookingRequest();
        promoteRequest.setOrganizationId(entry.getOrganizationId());
        promoteRequest.setPatientId(entry.getPatientId());
        promoteRequest.setSlotId(slotId);
        promoteRequest.setOriginChannel("WAITLIST");
        ScheduleDetailModel schedule = scheduleQueryService.loadScheduleById(slot.getScheduleId());
        Long destinationLocationId = bookingRegisterMapper.resolveLocationIdFromActors(schedule.getActors());
        CreateAppointmentCommand createCommand = bookingRegisterMapper.toCreateAppointmentCommand(
            promoteRequest, slot, slotId, destinationLocationId, healthcareServiceId, bookingDate, operatorId);
        Appointment appointment;
        try {
            appointment = outpatientBookingWriteSupport.allocateAndCreate(createCommand, slotId, operatorId);
        } catch (BusinessException ex) {
            if (ErrorCodeConstants.APPOINTMENT_RULE_CONFLICT.equals(ex.getErrorCode())
                || ErrorCodeConstants.SLOT_CONFLICT.equals(ex.getErrorCode())) {
                return Optional.empty();
            }
            throw ex;
        }
        boolean promoted = waitlistDomainService.markPromotedIfWaiting(entry.getId(), appointment.getId(), operatorId);
        if (!promoted) {
            this.rollbackPromotedAppointment(appointment.getId(), slotId, operatorId);
            return Optional.empty();
        }
        bookingAuditSupport.record("Appointment", appointment.getId(), "PROMOTE_WAITLIST", slotId.toString());
        return Optional.of(appointment);
    }

    private void rollbackPromotedAppointment(Long appointmentId, Long slotId, Long operatorId) {
        CancelAppointmentCommand command = new CancelAppointmentCommand();
        command.setOperatorId(operatorId);
        CancelAppointmentCommand.CancelAppointmentItemCommand item = new CancelAppointmentCommand.CancelAppointmentItemCommand();
        item.setAppointmentId(appointmentId);
        item.setReason("候补提升并发回滚");
        command.setItem(item);
        try {
            appointmentDomainService.cancelAppointment(command);
        } catch (RuntimeException ex) {
            // 回滚取消失败时仍释放号源，避免 slot 泄漏。
        }
        slotDomainService.releaseBooking(bookingRegisterMapper.toReleaseBookingCommand(slotId, operatorId));
    }

    private boolean isSlotFull(SlotDetailModel slot) {
        return slot.getCapacity() != null
            && slot.getBookedCount() != null
            && slot.getBookedCount() >= slot.getCapacity();
    }
}
