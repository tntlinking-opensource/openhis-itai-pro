/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.booking.mapper;

import com.openhis.health.integration.booking.command.OutpatientBookingLookupCommand;
import com.openhis.health.integration.booking.command.OutpatientBookingRegistrationValidateCommand;
import com.openhis.health.integration.booking.command.OutpatientRegistrationCancelCommand;
import com.openhis.health.integration.booking.command.OutpatientWalkInSlotAllocateCommand;
import com.openhis.health.integration.booking.result.BookableServiceOptionSnapshot;
import com.openhis.health.integration.booking.result.BookableSlotHintSnapshot;
import com.openhis.health.integration.booking.result.OutpatientBookingSnapshot;
import com.openhis.whale.booking.contracts.command.BookingRegistrationValidateCommand;
import com.openhis.whale.booking.contracts.command.RegistrationCancelReason;
import com.openhis.whale.booking.contracts.command.WalkInSlotAllocateCommand;
import com.openhis.whale.booking.contracts.dto.BookableServiceOption;
import com.openhis.whale.booking.contracts.dto.BookableSlotHint;
import com.openhis.whale.booking.contracts.query.OutpatientBookingLookupQuery;

import java.util.ArrayList;
import java.util.List;

/** booking-contracts ↔ health-integration-contracts DTO 映射。 */
public final class BookingIntegrationMapper {

    private BookingIntegrationMapper() {
    }

    public static OutpatientBookingLookupQuery toLookupQuery(OutpatientBookingLookupCommand command) {
        if (command == null) {
            return null;
        }
        OutpatientBookingLookupQuery query = new OutpatientBookingLookupQuery();
        query.setPatientId(command.getPatientId());
        query.setAppointmentBusNo(command.getAppointmentBusNo());
        query.setRegistrationDate(command.getRegistrationDate());
        return query;
    }

    public static OutpatientBookingSnapshot toSnapshot(
            com.openhis.whale.booking.contracts.dto.OutpatientBookingSnapshot source) {
        if (source == null) {
            return null;
        }
        OutpatientBookingSnapshot target = new OutpatientBookingSnapshot();
        target.setAppointmentId(source.getAppointmentId());
        target.setAppointmentBusNo(source.getAppointmentBusNo());
        target.setSlotId(source.getSlotId());
        target.setOrganizationId(source.getOrganizationId());
        target.setPractitionerId(source.getPractitionerId());
        target.setHealthcareServiceId(source.getHealthcareServiceId());
        target.setSlotStart(source.getSlotStart());
        target.setSlotEnd(source.getSlotEnd());
        target.setOrganizationName(source.getOrganizationName());
        target.setPractitionerName(source.getPractitionerName());
        target.setServiceTypeName(source.getServiceTypeName());
        target.setStatusEnum(source.getStatusEnum());
        target.setEncounterId(source.getEncounterId());
        return target;
    }

    public static List<BookableServiceOptionSnapshot> toServiceOptionSnapshots(List<BookableServiceOption> sources) {
        List<BookableServiceOptionSnapshot> targets = new ArrayList<>();
        if (sources == null) {
            return targets;
        }
        for (BookableServiceOption source : sources) {
            if (source == null) {
                continue;
            }
            BookableServiceOptionSnapshot target = new BookableServiceOptionSnapshot();
            target.setHealthcareServiceId(source.getHealthcareServiceId());
            target.setServiceTypeName(source.getServiceTypeName());
            targets.add(target);
        }
        return targets;
    }

    public static List<BookableSlotHintSnapshot> toSlotHintSnapshots(List<BookableSlotHint> sources) {
        List<BookableSlotHintSnapshot> targets = new ArrayList<>();
        if (sources == null) {
            return targets;
        }
        for (BookableSlotHint source : sources) {
            if (source == null) {
                continue;
            }
            BookableSlotHintSnapshot target = new BookableSlotHintSnapshot();
            target.setSlotId(source.getSlotId());
            target.setSlotStart(source.getSlotStart());
            target.setSlotEnd(source.getSlotEnd());
            target.setRemainingCount(source.getRemainingCount());
            targets.add(target);
        }
        return targets;
    }

    public static BookingRegistrationValidateCommand toValidateCommand(
            OutpatientBookingRegistrationValidateCommand command) {
        if (command == null) {
            return null;
        }
        BookingRegistrationValidateCommand target = new BookingRegistrationValidateCommand();
        target.setAppointmentId(command.getAppointmentId());
        target.setOrganizationId(command.getOrganizationId());
        target.setPractitionerId(command.getPractitionerId());
        target.setHealthcareServiceId(command.getHealthcareServiceId());
        target.setPatientId(command.getPatientId());
        return target;
    }

    public static WalkInSlotAllocateCommand toWalkInCommand(OutpatientWalkInSlotAllocateCommand command) {
        if (command == null) {
            return null;
        }
        WalkInSlotAllocateCommand target = new WalkInSlotAllocateCommand();
        target.setOrganizationId(command.getOrganizationId());
        target.setPractitionerId(command.getPractitionerId());
        target.setHealthcareServiceId(command.getHealthcareServiceId());
        target.setRegistrationDate(command.getRegistrationDate());
        target.setPatientId(command.getPatientId());
        target.setOperatorId(command.getOperatorId());
        target.setSlotId(command.getSlotId());
        return target;
    }

    public static RegistrationCancelReason toCancelReason(OutpatientRegistrationCancelCommand command) {
        if (command == null) {
            return null;
        }
        RegistrationCancelReason target = new RegistrationCancelReason();
        target.setCancelReason(command.getCancelReason());
        target.setPolicyCode(command.getPolicyCode());
        return target;
    }
}
