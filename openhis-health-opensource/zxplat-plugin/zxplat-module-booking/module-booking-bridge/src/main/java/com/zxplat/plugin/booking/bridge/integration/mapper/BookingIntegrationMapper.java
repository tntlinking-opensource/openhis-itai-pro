/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.bridge.integration.mapper;

import com.zxplat.domain.infrastructure.integration.booking.ExtensionIn.OutpatientBookingLookupExtensionIn;
import com.zxplat.domain.infrastructure.integration.booking.ExtensionIn.OutpatientBookingRegistrationValidateExtensionIn;
import com.zxplat.domain.infrastructure.integration.booking.ExtensionIn.OutpatientRegistrationCancelExtensionIn;
import com.zxplat.domain.infrastructure.integration.booking.ExtensionIn.OutpatientWalkInSlotAllocateExtensionIn;
import com.zxplat.domain.infrastructure.integration.booking.ExtensionOut.BookableServiceOptionQueryExtensionOut;
import com.zxplat.domain.infrastructure.integration.booking.ExtensionOut.BookableSlotHintQueryExtensionOut;
import com.zxplat.plugin.booking.domain.appointment.command.BookingRegistrationValidateCommand;
import com.zxplat.plugin.booking.domain.appointment.command.RegistrationCancelReason;
import com.zxplat.plugin.booking.domain.appointment.command.WalkInSlotAllocateCommand;
import com.zxplat.plugin.booking.domain.appointment.result.BookableServiceOption;
import com.zxplat.plugin.booking.domain.slot.result.BookableSlotHint;
import com.zxplat.plugin.booking.domain.bed.result.InpatientBedReservationSnapshot;
import com.zxplat.plugin.booking.domain.appointment.result.OutpatientBookingSnapshot;
import com.zxplat.plugin.booking.domain.appointment.queries.OutpatientBookingLookupQuery;

import java.util.ArrayList;
import java.util.List;

/** booking-domain ↔ health-integration-contracts DTO 映射。 */
public final class BookingIntegrationMapper {

    private BookingIntegrationMapper() {
    }

    public static OutpatientBookingLookupQuery toLookupQuery(OutpatientBookingLookupExtensionIn command) {
        if (command == null) {
            return null;
        }
        OutpatientBookingLookupQuery query = new OutpatientBookingLookupQuery();
        query.setPatientId(command.getPatientId());
        query.setAppointmentBusNo(command.getAppointmentBusNo());
        query.setRegistrationDate(command.getRegistrationDate());
        return query;
    }

    public static com.zxplat.domain.infrastructure.integration.booking.ExtensionOut.OutpatientBookingQueryExtensionOut toSnapshot(
            OutpatientBookingSnapshot source) {
        if (source == null) {
            return null;
        }
        com.zxplat.domain.infrastructure.integration.booking.ExtensionOut.OutpatientBookingQueryExtensionOut target =
            new com.zxplat.domain.infrastructure.integration.booking.ExtensionOut.OutpatientBookingQueryExtensionOut();
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

    public static com.zxplat.domain.infrastructure.integration.booking.ExtensionOut.InpatientBedReservationQueryExtensionOut toBedReservationSnapshot(
            InpatientBedReservationSnapshot source) {
        if (source == null) {
            return null;
        }
        com.zxplat.domain.infrastructure.integration.booking.ExtensionOut.InpatientBedReservationQueryExtensionOut target =
            new com.zxplat.domain.infrastructure.integration.booking.ExtensionOut.InpatientBedReservationQueryExtensionOut();
        target.setAppointmentId(source.getAppointmentId());
        target.setBedLocationId(source.getBedLocationId());
        target.setPatientId(source.getPatientId());
        target.setIntendedAdmissionDate(source.getIntendedAdmissionDate());
        return target;
    }

    public static List<BookableServiceOptionQueryExtensionOut> toServiceOptionSnapshots(List<BookableServiceOption> sources) {
        List<BookableServiceOptionQueryExtensionOut> targets = new ArrayList<>();
        if (sources == null) {
            return targets;
        }
        for (BookableServiceOption source : sources) {
            if (source == null) {
                continue;
            }
            BookableServiceOptionQueryExtensionOut target = new BookableServiceOptionQueryExtensionOut();
            target.setHealthcareServiceId(source.getHealthcareServiceId());
            target.setServiceTypeName(source.getServiceTypeName());
            targets.add(target);
        }
        return targets;
    }

    public static List<BookableSlotHintQueryExtensionOut> toSlotHintSnapshots(List<BookableSlotHint> sources) {
        List<BookableSlotHintQueryExtensionOut> targets = new ArrayList<>();
        if (sources == null) {
            return targets;
        }
        for (BookableSlotHint source : sources) {
            if (source == null) {
                continue;
            }
            BookableSlotHintQueryExtensionOut target = new BookableSlotHintQueryExtensionOut();
            target.setSlotId(source.getSlotId());
            target.setSlotStart(source.getSlotStart());
            target.setSlotEnd(source.getSlotEnd());
            target.setRemainingCount(source.getRemainingCount());
            targets.add(target);
        }
        return targets;
    }

    public static BookingRegistrationValidateCommand toValidateCommand(
            OutpatientBookingRegistrationValidateExtensionIn command) {
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

    public static WalkInSlotAllocateCommand toWalkInCommand(OutpatientWalkInSlotAllocateExtensionIn command) {
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
        target.setEncounterId(command.getEncounterId());
        return target;
    }

    public static RegistrationCancelReason toCancelReason(OutpatientRegistrationCancelExtensionIn command) {
        if (command == null) {
            return null;
        }
        RegistrationCancelReason target = new RegistrationCancelReason();
        target.setCancelReason(command.getCancelReason());
        target.setPolicyCode(command.getPolicyCode());
        target.setOperatorId(command.getOperatorId());
        return target;
    }
}
