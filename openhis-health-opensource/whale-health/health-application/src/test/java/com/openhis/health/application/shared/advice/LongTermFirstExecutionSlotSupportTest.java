/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.advice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * 开立日首次槽裁剪 + 停嘱日末槽识别。
 */
class LongTermFirstExecutionSlotSupportTest {

    @Test
    void applyOpenedDayFirstSlotRule_coversNearestPastAt1450() {
        LocalDateTime opened = LocalDateTime.of(2026, 7, 15, 14, 50, 15);
        List<LocalDateTime> expanded = List.of(
            LocalDateTime.of(2026, 7, 15, 8, 0),
            LocalDateTime.of(2026, 7, 15, 12, 0),
            LocalDateTime.of(2026, 7, 15, 18, 0),
            LocalDateTime.of(2026, 7, 16, 8, 0));
        Set<LocalDateTime> planned =
            LongTermFirstExecutionSlotSupport.applyOpenedDayFirstSlotRule(expanded, opened);
        assertEquals(3, planned.size());
        assertFalse(planned.contains(LocalDateTime.of(2026, 7, 15, 8, 0)));
        assertTrue(planned.contains(LocalDateTime.of(2026, 7, 15, 12, 0)));
        assertTrue(planned.contains(LocalDateTime.of(2026, 7, 15, 18, 0)));
        assertTrue(planned.contains(LocalDateTime.of(2026, 7, 16, 8, 0)));
        LocalDateTime first =
            LongTermFirstExecutionSlotSupport.resolveFirstPlanTimeOnOpenedDay(expanded, opened);
        assertEquals(LocalDateTime.of(2026, 7, 15, 12, 0), first);
        assertTrue(LongTermFirstExecutionSlotSupport.isFirstExecutionSlot(
            LocalDateTime.of(2026, 7, 15, 12, 0), first));
        assertFalse(LongTermFirstExecutionSlotSupport.isFirstExecutionSlot(
            LocalDateTime.of(2026, 7, 15, 18, 0), first));
    }

    @Test
    void resolveLastPlanTimeOnStopDay_keepsLastNotAfterStop() {
        LocalDateTime stop = LocalDateTime.of(2026, 7, 16, 14, 0);
        List<LocalDateTime> expanded = List.of(
            LocalDateTime.of(2026, 7, 16, 8, 0),
            LocalDateTime.of(2026, 7, 16, 12, 0),
            LocalDateTime.of(2026, 7, 16, 18, 0));
        // 展开右界已裁掉 18:00；本方法在已裁点集上取末槽
        List<LocalDateTime> clipped = List.of(
            LocalDateTime.of(2026, 7, 16, 8, 0),
            LocalDateTime.of(2026, 7, 16, 12, 0));
        LocalDateTime last =
            LongTermFirstExecutionSlotSupport.resolveLastPlanTimeOnStopDay(clipped, stop);
        assertEquals(LocalDateTime.of(2026, 7, 16, 12, 0), last);
        assertTrue(LongTermFirstExecutionSlotSupport.isLastExecutionSlotOnStopDay(
            LocalDateTime.of(2026, 7, 16, 12, 0), last));
        assertFalse(LongTermFirstExecutionSlotSupport.isLastExecutionSlotOnStopDay(
            LocalDateTime.of(2026, 7, 16, 8, 0), last));
        // 未裁剪传入含晚于停嘱点时，末槽仍取 ≤ 停嘱
        LocalDateTime lastFromRaw =
            LongTermFirstExecutionSlotSupport.resolveLastPlanTimeOnStopDay(expanded, stop);
        assertEquals(LocalDateTime.of(2026, 7, 16, 12, 0), lastFromRaw);
    }

    @Test
    void resolveLastPlanTimeOnStopDay_beforeFirstPoint_returnsNull() {
        LocalDateTime stop = LocalDateTime.of(2026, 7, 16, 7, 0);
        List<LocalDateTime> expanded = List.of(
            LocalDateTime.of(2026, 7, 16, 8, 0),
            LocalDateTime.of(2026, 7, 16, 12, 0));
        assertNull(LongTermFirstExecutionSlotSupport.resolveLastPlanTimeOnStopDay(expanded, stop));
    }

    @Test
    void resolveLastPlanTimeOnStopDay_equalStop_allowed() {
        LocalDateTime stop = LocalDateTime.of(2026, 7, 16, 12, 0);
        List<LocalDateTime> expanded = List.of(
            LocalDateTime.of(2026, 7, 16, 8, 0),
            LocalDateTime.of(2026, 7, 16, 12, 0));
        LocalDateTime last =
            LongTermFirstExecutionSlotSupport.resolveLastPlanTimeOnStopDay(expanded, stop);
        assertEquals(LocalDateTime.of(2026, 7, 16, 12, 0), last);
    }

    @Test
    void resolveFirstSlotDefaultActualTime_delaysTwoMinutes() {
        LocalDateTime opened = LocalDateTime.of(2026, 8, 5, 10, 0, 15);
        LocalDateTime actual =
            LongTermFirstExecutionSlotSupport.resolveFirstSlotDefaultActualTime(opened);
        assertEquals(LocalDateTime.of(2026, 8, 5, 10, 2, 15), actual);
    }

    @Test
    void resolveFirstSlotDefaultActualTime_crossMidnight_clampsToDayEnd() {
        LocalDateTime opened = LocalDateTime.of(2026, 8, 5, 23, 59, 0);
        LocalDateTime actual =
            LongTermFirstExecutionSlotSupport.resolveFirstSlotDefaultActualTime(opened);
        assertEquals(LocalDateTime.of(2026, 8, 5, 23, 59, 59), actual);
    }

    @Test
    void resolveFirstSlotDefaultActualTime_nullOpened_returnsNull() {
        assertNull(LongTermFirstExecutionSlotSupport.resolveFirstSlotDefaultActualTime(null));
    }
}
