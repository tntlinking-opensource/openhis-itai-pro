/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.booking.outpatient;

import com.openhis.health.integration.booking.command.OutpatientRegistrationCancelCommand;
import com.openhis.health.integration.booking.command.OutpatientWalkInSlotAllocateCommand;

/**
 * 门诊挂号写侧衔接预约（IF-REG-07/10、现场占号）。
 *
 * <p><b>实现</b>：{@code OutpatientBookingRegistrationExtensionImpl}（booking-adapter）。</p>
 */
public interface OutpatientBookingRegistrationExtension {

    default boolean isEnabled() {
        return false;
    }

    default void linkEncounter(Long appointmentId, Long encounterId) {
        // NoOp
    }

    default Long allocateWalkInSlot(OutpatientWalkInSlotAllocateCommand command) {
        return null;
    }

    default void checkInAfterRegister(Long appointmentId, Long encounterId) {
        // NoOp
    }

    default void onRegistrationCancelled(Long encounterId, OutpatientRegistrationCancelCommand reason) {
        // NoOp
    }
}
