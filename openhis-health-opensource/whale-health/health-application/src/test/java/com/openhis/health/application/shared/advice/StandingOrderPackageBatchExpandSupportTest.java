/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.advice;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link StandingOrderPackageBatchExpandSupport} 切盒规则单测。
 */
class StandingOrderPackageBatchExpandSupportTest {

    @Test
    void cut_exactPackage_midDeadline_expandsFullFirstBox() {
        // 1 盒 = 6 片；槽量均为 1；截止落在第 3 槽 → 仍应展满前 6 槽
        List<LocalDateTime> times = new ArrayList<>();
        List<BigDecimal> qtys = new ArrayList<>();
        LocalDateTime day = LocalDateTime.of(2026, 7, 22, 8, 0);
        for (int i = 0; i < 9; i++) {
            times.add(day.plusHours(i * 4L));
            qtys.add(BigDecimal.ONE);
        }
        LocalDateTime deadline = times.get(2);
        StandingOrderPackageBatchExpandSupport.CutResult result =
            StandingOrderPackageBatchExpandSupport.cutThroughBatchContainingDeadline(
                times, qtys, new BigDecimal("6"), deadline, true);
        assertEquals(StandingOrderPackageBatchExpandSupport.CutKind.SUCCESS, result.getKind());
        assertEquals(6, result.getPlanTimes().size());
        assertEquals(times.get(5), result.getPlanTimes().get(5));
    }

    @Test
    void cut_deadlineInSecondBox_returnsTwoBoxes() {
        List<LocalDateTime> times = new ArrayList<>();
        List<BigDecimal> qtys = new ArrayList<>();
        LocalDateTime day = LocalDateTime.of(2026, 7, 22, 8, 0);
        for (int i = 0; i < 12; i++) {
            times.add(day.plusHours(i * 4L));
            qtys.add(BigDecimal.ONE);
        }
        LocalDateTime deadline = times.get(7);
        StandingOrderPackageBatchExpandSupport.CutResult result =
            StandingOrderPackageBatchExpandSupport.cutThroughBatchContainingDeadline(
                times, qtys, new BigDecimal("6"), deadline, true);
        assertEquals(StandingOrderPackageBatchExpandSupport.CutKind.SUCCESS, result.getKind());
        assertEquals(12, result.getPlanTimes().size());
    }

    @Test
    void cut_incompleteNeedsMore_whenCanExtend() {
        List<LocalDateTime> times = new ArrayList<>();
        List<BigDecimal> qtys = new ArrayList<>();
        LocalDateTime day = LocalDateTime.of(2026, 7, 22, 8, 0);
        for (int i = 0; i < 4; i++) {
            times.add(day.plusHours(i * 4L));
            qtys.add(BigDecimal.ONE);
        }
        StandingOrderPackageBatchExpandSupport.CutResult result =
            StandingOrderPackageBatchExpandSupport.cutThroughBatchContainingDeadline(
                times, qtys, new BigDecimal("6"), times.get(2), true);
        assertEquals(StandingOrderPackageBatchExpandSupport.CutKind.NEED_MORE, result.getKind());
    }

    @Test
    void cut_overshoot_fallsBack() {
        List<LocalDateTime> times = List.of(
            LocalDateTime.of(2026, 7, 22, 8, 0),
            LocalDateTime.of(2026, 7, 22, 12, 0));
        List<BigDecimal> qtys = List.of(new BigDecimal("5"), new BigDecimal("2"));
        StandingOrderPackageBatchExpandSupport.CutResult result =
            StandingOrderPackageBatchExpandSupport.cutThroughBatchContainingDeadline(
                times, qtys, new BigDecimal("6"), times.get(1), false);
        assertEquals(StandingOrderPackageBatchExpandSupport.CutKind.FALLBACK, result.getKind());
    }

    @Test
    void cut_deadlinePastFirstBoxEnd_needsMoreForSecondBox() {
        // 恰好凑满第一盒；截止晚于盒末且可再扩 → 须继续探第二盒，不能只展一盒就停
        List<LocalDateTime> times = new ArrayList<>();
        List<BigDecimal> qtys = new ArrayList<>();
        LocalDateTime day = LocalDateTime.of(2026, 7, 22, 8, 0);
        for (int i = 0; i < 6; i++) {
            times.add(day.plusHours(i * 4L));
            qtys.add(BigDecimal.ONE);
        }
        LocalDateTime deadline = times.get(5).plusDays(1);
        StandingOrderPackageBatchExpandSupport.CutResult result =
            StandingOrderPackageBatchExpandSupport.cutThroughBatchContainingDeadline(
                times, qtys, new BigDecimal("6"), deadline, true);
        assertEquals(StandingOrderPackageBatchExpandSupport.CutKind.NEED_MORE, result.getKind());
    }

    @Test
    void cut_deadlinePastFirstBoxEnd_hardCap_returnsFirstBox() {
        // 截止晚于盒末但不可再扩（停嘱/探测顶）→ 展满已完整的第 1 盒
        List<LocalDateTime> times = new ArrayList<>();
        List<BigDecimal> qtys = new ArrayList<>();
        LocalDateTime day = LocalDateTime.of(2026, 7, 22, 8, 0);
        for (int i = 0; i < 6; i++) {
            times.add(day.plusHours(i * 4L));
            qtys.add(BigDecimal.ONE);
        }
        LocalDateTime deadline = times.get(5).plusDays(1);
        StandingOrderPackageBatchExpandSupport.CutResult result =
            StandingOrderPackageBatchExpandSupport.cutThroughBatchContainingDeadline(
                times, qtys, new BigDecimal("6"), deadline, false);
        assertEquals(StandingOrderPackageBatchExpandSupport.CutKind.SUCCESS, result.getKind());
        assertEquals(6, result.getPlanTimes().size());
    }
}
