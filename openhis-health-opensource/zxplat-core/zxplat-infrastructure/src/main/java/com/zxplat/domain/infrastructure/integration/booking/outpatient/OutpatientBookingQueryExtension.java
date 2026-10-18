/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.booking.outpatient;

import com.zxplat.domain.infrastructure.integration.booking.ExtensionIn.BookableServiceTypesQueryExtensionIn;
import com.zxplat.domain.infrastructure.integration.booking.ExtensionIn.BookableSlotsQueryExtensionIn;
import com.zxplat.domain.infrastructure.integration.booking.ExtensionIn.OutpatientBookingLoadExtensionIn;
import com.zxplat.domain.infrastructure.integration.booking.ExtensionIn.OutpatientBookingLookupExtensionIn;
import com.zxplat.domain.infrastructure.integration.booking.ExtensionIn.OutpatientBookingRegistrationValidateExtensionIn;
import com.zxplat.domain.infrastructure.integration.booking.ExtensionOut.BookableServiceOptionQueryExtensionOut;
import com.zxplat.domain.infrastructure.integration.booking.ExtensionOut.BookableSlotHintQueryExtensionOut;
import com.zxplat.domain.infrastructure.integration.booking.ExtensionOut.OutpatientBookingQueryExtensionOut;

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

    default Optional<OutpatientBookingQueryExtensionOut> findActiveOutpatientBooking(
            OutpatientBookingLookupExtensionIn command) {
        return Optional.empty();
    }

    default Optional<OutpatientBookingQueryExtensionOut> loadOutpatientBookingById(
            OutpatientBookingLoadExtensionIn query) {
        return Optional.empty();
    }

    default List<BookableServiceOptionQueryExtensionOut> listBookableServiceTypes(
            BookableServiceTypesQueryExtensionIn query) {
        return Collections.emptyList();
    }

    default List<BookableSlotHintQueryExtensionOut> listBookableSlots(BookableSlotsQueryExtensionIn query) {
        return Collections.emptyList();
    }

    default void validateForRegistration(OutpatientBookingRegistrationValidateExtensionIn command) {
        throw new UnsupportedOperationException("预约查询 Extension 未装配");
    }

    default boolean shouldIntersectRegisterServicesWithReleasedSlots(Long organizationId) {
        // 挂号 IF-REG-01 号源类型与排班解绑，不再与放号求交；方法保留兼容，恒返回 false
        return false;
    }

    /** 现场挂号（无 appointmentId）是否须占用当日 Slot；默认 false（未装配 booking）。 */
    default boolean shouldAllocateWalkInSlotForWalkInRegistration(Long organizationId) {
        return false;
    }
}
