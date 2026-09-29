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

/**
 * {@link StandingOrderPackageBatchExpandSupport} 一盒一点切盒规则单测。
 */
class StandingOrderPackageBatchExpandSupportTest {

    @Test
    void cut_exactPackage_midDeadline_returnsOnlyFirstBoxAnchor() {
        // 1 盒 = 6 片；槽量均为 1；截止落在第 3 槽 → 仅回传第 1 盒首锚点（一点，非整盒 6 点）
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
        assertEquals(1, result.getPlanTimes().size());
        assertEquals(times.get(0), result.getPlanTimes().get(0));
    }

    @Test
    void cut_deadlineInSecondBox_returnsTwoBoxAnchors() {
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
        assertEquals(2, result.getPlanTimes().size());
        assertEquals(times.get(0), result.getPlanTimes().get(0));
        assertEquals(times.get(6), result.getPlanTimes().get(1));
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
    void cut_overshootCeil_closesBoxWithCarry_notFallback() {
        // 5+2 越过包装 6：旧「刚好等于」会 FALLBACK；ceil 关盒后余量 1 结转
        List<LocalDateTime> times = List.of(
            LocalDateTime.of(2026, 7, 22, 8, 0),
            LocalDateTime.of(2026, 7, 22, 12, 0));
        List<BigDecimal> qtys = List.of(new BigDecimal("5"), new BigDecimal("2"));
        StandingOrderPackageBatchExpandSupport.CutResult result =
            StandingOrderPackageBatchExpandSupport.cutThroughBatchContainingDeadline(
                times, qtys, new BigDecimal("6"), times.get(1), false);
        assertEquals(StandingOrderPackageBatchExpandSupport.CutKind.SUCCESS, result.getKind());
        assertEquals(1, result.getPlanTimes().size());
        assertEquals(times.get(0), result.getPlanTimes().get(0));
    }

    @Test
    void cut_threeTabletsBidPackage100_ceilClosesFirstBox_notDayPointsFallback() {
        // 复现：优甲乐 一次 3 片 × BID、盒 100 片——旧 exact 切盒在累加越过 100 时 FALLBACK 成日点
        List<LocalDateTime> times = new ArrayList<>();
        List<BigDecimal> qtys = new ArrayList<>();
        LocalDateTime day = LocalDateTime.of(2026, 9, 26, 10, 51);
        // 34 槽 × 3 片 = 102 ≥ 100 → ceil 封第 1 盒；截止落在开嘱日第二点仍须探测满盒
        for (int i = 0; i < 40; i++) {
            // 日二次：偶数槽取当日首点锚，奇数槽 +6h
            if (i % 2 == 0) {
                times.add(day.plusDays(i / 2L));
            } else {
                times.add(day.plusDays(i / 2L).withHour(16).withMinute(0));
            }
            qtys.add(new BigDecimal("3"));
        }
        LocalDateTime deadline = times.get(1);
        StandingOrderPackageBatchExpandSupport.CutResult needMore =
            StandingOrderPackageBatchExpandSupport.cutThroughBatchContainingDeadline(
                times.subList(0, 4), qtys.subList(0, 4), new BigDecimal("100"), deadline, true);
        assertEquals(StandingOrderPackageBatchExpandSupport.CutKind.NEED_MORE, needMore.getKind());

        StandingOrderPackageBatchExpandSupport.CutResult result =
            StandingOrderPackageBatchExpandSupport.cutThroughBatchContainingDeadline(
                times, qtys, new BigDecimal("100"), deadline, true);
        assertEquals(StandingOrderPackageBatchExpandSupport.CutKind.SUCCESS, result.getKind());
        assertEquals(1, result.getPlanTimes().size());
        assertEquals(times.get(0), result.getPlanTimes().get(0));
    }

    @Test
    void cut_singleSlotOverPackage_fallsBack() {
        // 单槽 7 > 包装 6：仍降级可拆
        List<LocalDateTime> times = List.of(LocalDateTime.of(2026, 7, 22, 8, 0));
        List<BigDecimal> qtys = List.of(new BigDecimal("7"));
        StandingOrderPackageBatchExpandSupport.CutResult result =
            StandingOrderPackageBatchExpandSupport.cutThroughBatchContainingDeadline(
                times, qtys, new BigDecimal("6"), times.get(0), false);
        assertEquals(StandingOrderPackageBatchExpandSupport.CutKind.FALLBACK, result.getKind());
    }

    @Test
    void cut_deadlinePastFirstBoxEnd_needsMoreForSecondBox() {
        // 恰好凑满第一盒；截止晚于盒末且可再扩 → 须继续探第二盒首锚点
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
    void cut_incompleteHardCap_returnsOnlyCompleteBoxAnchors_notFallback() {
        // 停嘱硬顶：窗内仅 4 槽凑不满 6 片盒 → 不降级可拆，回传空（无完整盒首）
        List<LocalDateTime> times = new ArrayList<>();
        List<BigDecimal> qtys = new ArrayList<>();
        LocalDateTime day = LocalDateTime.of(2026, 8, 4, 8, 0);
        for (int i = 0; i < 4; i++) {
            times.add(day.plusDays(i));
            qtys.add(BigDecimal.ONE);
        }
        StandingOrderPackageBatchExpandSupport.CutResult result =
            StandingOrderPackageBatchExpandSupport.cutThroughBatchContainingDeadline(
                times, qtys, new BigDecimal("6"), times.get(3), false);
        assertEquals(StandingOrderPackageBatchExpandSupport.CutKind.SUCCESS, result.getKind());
        assertEquals(0, result.getPlanTimes().size());
    }

    @Test
    void cut_incompleteHardCap_keepsPriorCompleteBoxAnchor() {
        // 已凑满第 1 盒 + 第 2 盒未满触及硬顶 → 仅回传第 1 盒首，不降级展日点
        List<LocalDateTime> times = new ArrayList<>();
        List<BigDecimal> qtys = new ArrayList<>();
        LocalDateTime day = LocalDateTime.of(2026, 8, 4, 8, 0);
        for (int i = 0; i < 9; i++) {
            times.add(day.plusDays(i));
            qtys.add(BigDecimal.ONE);
        }
        StandingOrderPackageBatchExpandSupport.CutResult result =
            StandingOrderPackageBatchExpandSupport.cutThroughBatchContainingDeadline(
                times, qtys, new BigDecimal("6"), times.get(8), false);
        assertEquals(StandingOrderPackageBatchExpandSupport.CutKind.SUCCESS, result.getKind());
        assertEquals(1, result.getPlanTimes().size());
        assertEquals(times.get(0), result.getPlanTimes().get(0));
    }

    @Test
    void cut_deadlinePastFirstBoxEnd_hardCap_returnsFirstBoxAnchor() {
        // 截止晚于盒末但不可再扩（停嘱/探测顶）→ 仅回传已完整第 1 盒首锚点
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
        assertEquals(1, result.getPlanTimes().size());
        assertEquals(times.get(0), result.getPlanTimes().get(0));
    }
}
