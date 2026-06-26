/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.booking.outpatient;

import com.openhis.health.integration.booking.command.OutpatientBookingLookupCommand;
import com.openhis.health.integration.booking.command.OutpatientBookingRegistrationValidateCommand;
import com.openhis.health.integration.booking.result.BookableServiceOptionSnapshot;
import com.openhis.health.integration.booking.result.BookableSlotHintSnapshot;
import com.openhis.health.integration.booking.result.OutpatientBookingSnapshot;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 门诊挂号台只读消费预约事实（IF-REG-12～14）。
 *
 * <p><b>实现</b>：{@code OutpatientBookingQueryExtensionImpl}（booking-adapter）。</p>
 */
public interface OutpatientBookingQueryExtension {

    default boolean isEnabled() {
        return false;
    }

    default Optional<OutpatientBookingSnapshot> findActiveOutpatientBooking(OutpatientBookingLookupCommand command) {
        return Optional.empty();
    }

    default Optional<OutpatientBookingSnapshot> loadOutpatientBookingById(Long appointmentId) {
        return Optional.empty();
    }

    default List<BookableServiceOptionSnapshot> listBookableServiceTypes(
            Long organizationId, Long practitionerId, LocalDate date) {
        return Collections.emptyList();
    }

    default List<BookableSlotHintSnapshot> listBookableSlots(
            Long organizationId, Long practitionerId, Long healthcareServiceId, LocalDate date) {
        return Collections.emptyList();
    }

    default void validateForRegistration(OutpatientBookingRegistrationValidateCommand command) {
        throw new UnsupportedOperationException("预约查询 Extension 未装配");
    }
}
