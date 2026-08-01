/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.advice;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 长期医嘱开立日「首次执行槽」裁剪与停嘱日「末执行槽」识别。
 *
 * <p>开立日规则见 BR-IP-ORD-030⑥ / BR-IP-EXE-032；停嘱日末槽为对称易用性：
 * 识别最后一个 ≤ 停嘱的频次点，供列表/写路径 {@code defaultActualTime}=停嘱时刻
 * （同槽既是首次又是末槽时开嘱优先）。频次时钟禁止写死。</p>
 */
public final class LongTermFirstExecutionSlotSupport {

    private LongTermFirstExecutionSlotSupport() {
    }

    /**
     * 对频次展开结果应用开立日首次槽规则。
     *
     * <ul>
     *   <li>开立日已有 ≤ 开立时刻的点 → 首次 = 最近已过（含相等）点；更早点丢弃；开立后点保留</li>
     *   <li>开立日尚无已过点 → 保留开立日全部点（首次 = 当天第一个）</li>
     *   <li>开立已过当天最后一个点 → 当日仅保留最后一个点</li>
     * </ul>
     *
     * @param expandedPoints 频次域展开的计划点（可含多日）
     * @param openedTime     开立时刻；空则原样返回
     * @return 裁剪后的有序集合
     */
    public static Set<LocalDateTime> applyOpenedDayFirstSlotRule(
        List<LocalDateTime> expandedPoints,
        LocalDateTime openedTime) {
        if (expandedPoints == null || expandedPoints.isEmpty()) {
            return new LinkedHashSet<>();
        }
        if (openedTime == null) {
            return new LinkedHashSet<>(expandedPoints);
        }
        LocalDate openedDay = openedTime.toLocalDate();
        List<LocalDateTime> sameDay = new ArrayList<>();
        List<LocalDateTime> afterDays = new ArrayList<>();
        for (LocalDateTime point : expandedPoints) {
            if (point == null) {
                continue;
            }
            // 开立日与之后日拆分；开立日之前的点不应出现（展开左界为开立日零点）
            if (point.toLocalDate().isEqual(openedDay)) {
                sameDay.add(point);
            } else if (point.toLocalDate().isAfter(openedDay)) {
                afterDays.add(point);
            }
        }
        Collections.sort(sameDay);
        Collections.sort(afterDays);
        Set<LocalDateTime> result = new LinkedHashSet<>();
        if (sameDay.isEmpty()) {
            result.addAll(afterDays);
            return result;
        }
        List<LocalDateTime> pastOrEqual = new ArrayList<>();
        for (LocalDateTime point : sameDay) {
            // 已过或恰好等于开立时刻 → 候选首次槽
            if (!point.isAfter(openedTime)) {
                pastOrEqual.add(point);
            }
        }
        if (!pastOrEqual.isEmpty()) {
            // 首次 = 最近已过点；丢弃更早的已过点
            LocalDateTime first = pastOrEqual.get(pastOrEqual.size() - 1);
            result.add(first);
            for (LocalDateTime point : sameDay) {
                if (point.isAfter(openedTime)) {
                    result.add(point);
                }
            }
        } else {
            // 尚未到当天第一个频次点：整日点均保留，首次落在第一个
            result.addAll(sameDay);
        }
        result.addAll(afterDays);
        return result;
    }

    /**
     * 解析开立日首次计划点（与 {@link #applyOpenedDayFirstSlotRule} 同口径）。
     *
     * @param expandedPoints 频次展开点
     * @param openedTime     开立时刻
     * @return 首次 planTime；无法解析时 null
     */
    public static LocalDateTime resolveFirstPlanTimeOnOpenedDay(
        List<LocalDateTime> expandedPoints,
        LocalDateTime openedTime) {
        if (openedTime == null || expandedPoints == null || expandedPoints.isEmpty()) {
            return null;
        }
        LocalDate openedDay = openedTime.toLocalDate();
        List<LocalDateTime> sameDay = new ArrayList<>();
        for (LocalDateTime point : expandedPoints) {
            if (point != null && point.toLocalDate().isEqual(openedDay)) {
                sameDay.add(point);
            }
        }
        if (sameDay.isEmpty()) {
            return null;
        }
        Collections.sort(sameDay);
        List<LocalDateTime> pastOrEqual = new ArrayList<>();
        for (LocalDateTime point : sameDay) {
            if (!point.isAfter(openedTime)) {
                pastOrEqual.add(point);
            }
        }
        if (!pastOrEqual.isEmpty()) {
            return pastOrEqual.get(pastOrEqual.size() - 1);
        }
        return sameDay.get(0);
    }

    /**
     * 槽位是否为开立日首次执行点（秒级对齐）。
     *
     * @param planTime      槽位计划时刻
     * @param firstPlanTime 首次 planTime
     * @return true 表示本槽为首次
     */
    public static boolean isFirstExecutionSlot(LocalDateTime planTime, LocalDateTime firstPlanTime) {
        if (planTime == null || firstPlanTime == null) {
            return false;
        }
        return planTime.withNano(0).equals(firstPlanTime.withNano(0));
    }

    /**
     * 解析停嘱自然日最后一个计划点（末槽；与列表 defaultActualTime=停嘱时刻同源）。
     *
     * <p>仅在已展开且已按开立日规则裁剪后的点集上取「停嘱日最后一个 ≤ 停嘱」的点；
     * 停嘱早于当日第一个频次点时返回 null（当日无点）。</p>
     *
     * @param expandedPoints 频次展开并裁剪后的计划点
     * @param stopTime       停嘱时刻；空则无法解析
     * @return 停嘱日末槽 planTime；无则 null
     */
    public static LocalDateTime resolveLastPlanTimeOnStopDay(
        List<LocalDateTime> expandedPoints,
        LocalDateTime stopTime) {
        if (stopTime == null || expandedPoints == null || expandedPoints.isEmpty()) {
            return null;
        }
        LocalDate stopDay = stopTime.toLocalDate();
        List<LocalDateTime> sameDay = new ArrayList<>();
        for (LocalDateTime point : expandedPoints) {
            if (point == null) {
                continue;
            }
            // 仅停嘱自然日且不晚于停嘱时刻的候选末槽
            if (point.toLocalDate().isEqual(stopDay) && !point.isAfter(stopTime)) {
                sameDay.add(point);
            }
        }
        if (sameDay.isEmpty()) {
            return null;
        }
        Collections.sort(sameDay);
        return sameDay.get(sameDay.size() - 1);
    }

    /**
     * 槽位是否为停嘱日末执行点（秒级对齐）。
     *
     * @param planTime     槽位计划时刻
     * @param lastPlanTime 停嘱日末槽 planTime
     * @return true 表示本槽为停嘱日末槽
     */
    public static boolean isLastExecutionSlotOnStopDay(LocalDateTime planTime, LocalDateTime lastPlanTime) {
        if (planTime == null || lastPlanTime == null) {
            return false;
        }
        return planTime.withNano(0).equals(lastPlanTime.withNano(0));
    }
}
