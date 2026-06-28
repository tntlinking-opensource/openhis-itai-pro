package com.openhis.whale.booking.adapter.writer;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.booking.contracts.writer.InpatientBedBookingConsumeWriter;
import com.openhis.whale.booking.domain.appointment.service.AppointmentDomainService;
import com.openhis.whale.booking.domain.bed.entity.BedReservationExt;
import com.openhis.whale.booking.domain.bed.service.BedReservationDomainService;
import com.openhis.whale.booking.domain.bed.service.BedReservationQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link InpatientBedBookingConsumeWriter} 实现：分床占床后关闭预约扩展（不释 Location）。
 */
@Component
@RequiredArgsConstructor
public class InpatientBedBookingConsumeWriterAdapter implements InpatientBedBookingConsumeWriter {

    private final AppointmentDomainService appointmentDomainService;
    private final BedReservationQueryService bedReservationQueryService;
    private final BedReservationDomainService bedReservationDomainService;

    @Override
    public void consumeReservationForBedAssign(Long appointmentId, Long operatorId) {
        if (appointmentId == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "appointmentId 不能为空");
        }
        BedReservationExt ext = bedReservationQueryService.findByAppointmentId(appointmentId)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.APPOINTMENT_NOT_FOUND, "床位预留扩展不存在"));
        appointmentDomainService.cancelAppointment(this.toCancelCommand(appointmentId, operatorId));
        bedReservationDomainService.softDelete(ext.getId(), operatorId);
    }

    private com.openhis.whale.booking.domain.appointment.command.CancelAppointmentCommand toCancelCommand(
        Long appointmentId,
        Long operatorId) {
        com.openhis.whale.booking.domain.appointment.command.CancelAppointmentCommand command =
            new com.openhis.whale.booking.domain.appointment.command.CancelAppointmentCommand();
        command.setOperatorId(operatorId);
        com.openhis.whale.booking.domain.appointment.command.CancelAppointmentCommand.CancelAppointmentItemCommand item =
            new com.openhis.whale.booking.domain.appointment.command.CancelAppointmentCommand.CancelAppointmentItemCommand();
        item.setAppointmentId(appointmentId);
        item.setReason("护士站分床占用");
        command.setItem(item);
        return command;
    }
}
