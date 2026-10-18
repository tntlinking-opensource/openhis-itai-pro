/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.slot.entity;

import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Slot 实体关键 BR：满号拒绝、允许超售可占。
 */
class SlotTest {

    @Test
    void assertBookable_rejectsWhenFullWithoutOverbook() {
        Slot slot = freeSlot(1);
        slot.setBookedCount(1);

        BusinessException ex = assertThrows(BusinessException.class, () -> slot.assertBookable(false));
        assertEquals(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, ex.getErrorCode());
        assertEquals("号源已满", ex.getMessage());
    }

    @Test
    void increaseBookedCount_allowsOverbookWhenFlagTrue() {
        Slot slot = freeSlot(1);
        slot.setBookedCount(1);

        Integer booked = slot.increaseBookedCountAndRefreshStatus(true);
        assertEquals(2, booked);
    }

    @Test
    void assertBookable_rejectsBusyUnavailable() {
        Slot slot = freeSlot(2);
        slot.toBusyUnavailable("停诊");

        BusinessException ex = assertThrows(BusinessException.class, () -> slot.assertBookable(false));
        assertEquals("封锁时段不可预约", ex.getMessage());
    }

    private static Slot freeSlot(int capacity) {
        Slot slot = new Slot();
        LocalDateTime start = LocalDateTime.of(2026, 9, 24, 9, 0);
        slot.initFree(1L, 10L, start, start.plusMinutes(30), capacity);
        return slot;
    }
}
