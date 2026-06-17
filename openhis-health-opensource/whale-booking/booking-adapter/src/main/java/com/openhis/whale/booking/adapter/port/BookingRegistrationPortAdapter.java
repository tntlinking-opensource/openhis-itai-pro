package com.openhis.whale.booking.adapter.port;



import com.openhis.whale.booking.adapter.support.BookingSlotLookupSupport;

import com.openhis.whale.booking.contracts.command.RegistrationCancelReason;

import com.openhis.whale.booking.contracts.command.WalkInSlotAllocateCommand;

import com.openhis.whale.booking.contracts.port.BookingRegistrationPort;

import com.openhis.whale.booking.domain.appointment.command.CancelAppointmentCommand;

import com.openhis.whale.booking.domain.appointment.command.CheckInOutpatientBookingCommand;

import com.openhis.whale.booking.domain.appointment.command.LinkEncounterCommand;

import com.openhis.whale.booking.domain.appointment.command.ReleaseEncounterLinkCommand;

import com.openhis.whale.booking.domain.appointment.entity.Appointment;

import com.openhis.whale.booking.domain.appointment.repository.AppointmentRepository;

import com.openhis.whale.booking.domain.appointment.service.AppointmentDomainService;

import com.openhis.whale.booking.domain.policy.entity.RegistrationPolicy;

import com.openhis.whale.booking.domain.policy.service.PolicyQueryService;

import com.openhis.whale.booking.domain.share.AppointmentKindConstants;

import com.openhis.whale.booking.domain.slot.command.AllocateBookingCommand;

import com.openhis.whale.booking.domain.slot.command.ReleaseBookingCommand;

import com.openhis.whale.booking.domain.slot.service.SlotDomainService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;



import java.time.LocalDate;

import java.util.List;



/**

 * {@link BookingRegistrationPort} 默认实现：写 booking-domain 预约关联。

 */

@Component

@RequiredArgsConstructor

public class BookingRegistrationPortAdapter implements BookingRegistrationPort {



    private static final String CANCEL_APPOINTMENT_POLICY = "CANCEL_APPOINTMENT";



    private final AppointmentDomainService appointmentDomainService;

    private final AppointmentRepository appointmentRepository;

    private final BookingSlotLookupSupport bookingSlotLookupSupport;

    private final SlotDomainService slotDomainService;

    private final PolicyQueryService policyQueryService;



    @Override

    public void linkEncounter(Long appointmentId, Long encounterId) {

        if (appointmentId == null || encounterId == null) {

            return;

        }

        LinkEncounterCommand command = new LinkEncounterCommand();

        LinkEncounterCommand.LinkEncounterItemCommand item = new LinkEncounterCommand.LinkEncounterItemCommand();

        item.setAppointmentId(appointmentId);

        item.setEncounterId(encounterId);

        command.setItems(List.of(item));

        appointmentDomainService.linkEncounter(command);

    }



    @Override

    public Long allocateWalkInSlot(WalkInSlotAllocateCommand command) {

        if (command == null || command.getOrganizationId() == null || command.getHealthcareServiceId() == null) {

            return null;

        }

        LocalDate registrationDate = command.getRegistrationDate() != null

            ? command.getRegistrationDate() : LocalDate.now();

        Long slotId = bookingSlotLookupSupport.findFirstAvailableSlotId(

            command.getOrganizationId(),

            registrationDate,

            command.getPractitionerId(),

            command.getHealthcareServiceId());

        if (slotId == null) {

            return null;

        }

        AllocateBookingCommand allocateCommand = new AllocateBookingCommand();

        AllocateBookingCommand.AllocateBookingItemCommand item = new AllocateBookingCommand.AllocateBookingItemCommand();

        item.setSlotId(slotId);

        allocateCommand.setOperatorId(command.getOperatorId());

        allocateCommand.setItems(List.of(item));

        slotDomainService.allocateBooking(allocateCommand);

        return slotId;

    }



    @Override

    public void checkInAfterRegister(Long appointmentId, Long encounterId) {

        if (appointmentId == null || encounterId == null) {

            return;

        }

        Appointment appointment = appointmentRepository.findById(appointmentId).orElse(null);

        if (appointment == null || Boolean.TRUE.equals(appointment.getIsDeleted())) {

            return;

        }

        RegistrationPolicy policy = policyQueryService.findRegistrationPolicyByOrganization(appointment.getOrganizationId())

            .orElse(null);

        if (policy == null || !Boolean.TRUE.equals(policy.getAutoCheckInOnRegister())) {

            return;

        }

        CheckInOutpatientBookingCommand command = new CheckInOutpatientBookingCommand();

        command.setOperatorId(appointment.getOrigin() != null ? appointment.getOrigin().getOriginOperatorId() : null);

        CheckInOutpatientBookingCommand.CheckInOutpatientBookingItemCommand item =

            new CheckInOutpatientBookingCommand.CheckInOutpatientBookingItemCommand();

        item.setAppointmentId(appointmentId);

        item.setOrganizationId(appointment.getOrganizationId());

        item.setDestinationLocationId(appointment.getDestinationLocationId());

        command.setItems(List.of(item));

        appointmentDomainService.checkInOutpatientBooking(command);

    }



    @Override

    public void onRegistrationCancelled(Long encounterId, RegistrationCancelReason reason) {

        if (encounterId == null) {

            return;

        }

        Appointment appointment = appointmentRepository.findByEncounterId(encounterId).orElse(null);

        if (appointment == null || Boolean.TRUE.equals(appointment.getIsDeleted())) {

            return;

        }

        if (!AppointmentKindConstants.OUTPATIENT_BOOKING.equals(appointment.getAppointmentKind())) {

            return;

        }

        RegistrationPolicy policy = policyQueryService.findRegistrationPolicyByOrganization(appointment.getOrganizationId())

            .orElse(null);

        String cancelPolicy = policy != null ? policy.getOnCancelPolicy() : "RELEASE_SLOT_ONLY";

        if (CANCEL_APPOINTMENT_POLICY.equals(cancelPolicy)) {

            CancelAppointmentCommand cancelCommand = new CancelAppointmentCommand();

            cancelCommand.setOperatorId(null);

            CancelAppointmentCommand.CancelAppointmentItemCommand cancelItem =

                new CancelAppointmentCommand.CancelAppointmentItemCommand();

            cancelItem.setAppointmentId(appointment.getId());

            cancelItem.setReason(
                reason != null && reason.getCancelReason() != null
                    ? reason.getCancelReason() : "退号取消预约");

            cancelItem.setCancellationReasonCode("REGISTRATION_CANCELLED");

            cancelCommand.setItems(List.of(cancelItem));

            appointmentDomainService.cancelAppointment(cancelCommand);

            if (appointment.getSlotId() != null) {

                ReleaseBookingCommand releaseCommand = new ReleaseBookingCommand();

                releaseCommand.setOperatorId(null);

                ReleaseBookingCommand.ReleaseBookingItemCommand releaseItem =

                    new ReleaseBookingCommand.ReleaseBookingItemCommand();

                releaseItem.setSlotId(appointment.getSlotId());

                releaseCommand.setItems(List.of(releaseItem));

                slotDomainService.releaseBooking(releaseCommand);

            }

            return;

        }

        ReleaseEncounterLinkCommand command = new ReleaseEncounterLinkCommand();

        ReleaseEncounterLinkCommand.ReleaseEncounterLinkItemCommand item =

            new ReleaseEncounterLinkCommand.ReleaseEncounterLinkItemCommand();

        item.setEncounterId(encounterId);

        command.setItems(List.of(item));

        appointmentDomainService.releaseEncounterLink(command);

    }

}


