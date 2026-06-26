/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.booking.outpatient;

import com.openhis.health.integration.booking.command.OutpatientBookingLookupCommand;
import com.openhis.health.integration.booking.command.OutpatientBookingRegistrationValidateCommand;
import com.openhis.health.integration.booking.result.BookableServiceOptionSnapshot;
import com.openhis.health.integration.booking.result.BookableSlotHintSnapshot;
import com.openhis.health.integration.booking.result.OutpatientBookingSnapshot;
import com.openhis.health.integration.booking.mapper.BookingIntegrationMapper;
import com.openhis.whale.booking.contracts.reader.OutpatientBookingReader;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** {@link OutpatientBookingQueryExtension} 实现，委托 {@link OutpatientBookingReader}。 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.booking.enabled", havingValue = "true")
public class OutpatientBookingQueryExtensionImpl implements OutpatientBookingQueryExtension {

    private final OutpatientBookingReader outpatientBookingReader;

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public Optional<OutpatientBookingSnapshot> findActiveOutpatientBooking(OutpatientBookingLookupCommand command) {
        return outpatientBookingReader.findActiveOutpatientBooking(BookingIntegrationMapper.toLookupQuery(command))
                .map(BookingIntegrationMapper::toSnapshot);
    }

    @Override
    public Optional<OutpatientBookingSnapshot> loadOutpatientBookingById(Long appointmentId) {
        return outpatientBookingReader.loadOutpatientBookingById(appointmentId)
                .map(BookingIntegrationMapper::toSnapshot);
    }

    @Override
    public List<BookableServiceOptionSnapshot> listBookableServiceTypes(
            Long organizationId, Long practitionerId, LocalDate date) {
        return BookingIntegrationMapper.toServiceOptionSnapshots(
                outpatientBookingReader.listBookableServiceTypes(organizationId, practitionerId, date));
    }

    @Override
    public List<BookableSlotHintSnapshot> listBookableSlots(
            Long organizationId, Long practitionerId, Long healthcareServiceId, LocalDate date) {
        return BookingIntegrationMapper.toSlotHintSnapshots(
                outpatientBookingReader.listBookableSlots(organizationId, practitionerId, healthcareServiceId, date));
    }

    @Override
    public void validateForRegistration(OutpatientBookingRegistrationValidateCommand command) {
        outpatientBookingReader.validateForRegistration(BookingIntegrationMapper.toValidateCommand(command));
    }
}
