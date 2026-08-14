/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.advice;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 住院长嘱「拆零单位下限」批次切分（产品默认，**BR-IP-EXE-036**；列表读路径）。
 *
 * <p>在<strong>已应用首日/首次槽裁剪与首次槽临床换量</strong>之后，按槽临床拆零量顺序累加：
 * 累加 {@code >= 1} 关闭一单位并回传该单位<strong>首槽 planTime</strong>；余量滚入下一单位。
 * 硬顶截断时未凑满的当前单位仍回传首槽（产品：不足一整拆零也先按一整单位执行/计费）。
 * 单槽临床拆零 {@code >= 1} 或入参非法 → {@link CutKind#FALLBACK}，由调用方降级可拆日点。</p>
 *
 * <p>写路径抬量（Charge/Dispense/库存 Gate）见
 * {@link InpatientMedicationRequestExecutionMeasureHelper#resolveBillableDispenseMeasureForExecutionSlot}，
 * 与本类正交：本类只决定「出哪些锚点」，不改写量。</p>
 */
public final class StandingOrderMinSplitUnitBatchExpandSupport {

    private StandingOrderMinSplitUnitBatchExpandSupport() {
    }

    /**
     * 切批结果种类。
     */
    public enum CutKind {
        /** 已覆盖截止：回传各单位首槽（硬顶下可含未满单位首槽）。 */
        SUCCESS,
        /** 窗内信息不足（未满单位还需更多点 / 下一单位未展开），调用方继续向后扩频次窗。 */
        NEED_MORE,
        /** 单槽已满一整拆零或入参非法；调用方应按可拆日点展开。 */
        FALLBACK
    }

    /**
     * 切批结果。
     */
    public static final class CutResult {
        private final CutKind kind;
        private final List<LocalDateTime> planTimes;

        private CutResult(CutKind kind, List<LocalDateTime> planTimes) {
            this.kind = kind;
            this.planTimes = planTimes;
        }

        /**
         * @return 结果种类
         */
        public CutKind getKind() {
            return kind;
        }

        /**
         * SUCCESS 时为各拆零单位首槽；其它种类为空列表。
         *
         * @return 计划点
         */
        public List<LocalDateTime> getPlanTimes() {
            return planTimes;
        }

        /**
         * @param planTimes 单位首槽；可空
         * @return SUCCESS
         */
        public static CutResult success(List<LocalDateTime> planTimes) {
            if (planTimes == null || planTimes.isEmpty()) {
                return new CutResult(CutKind.SUCCESS, Collections.emptyList());
            }
            return new CutResult(CutKind.SUCCESS, List.copyOf(planTimes));
        }

        /**
         * @return NEED_MORE
         */
        public static CutResult needMore() {
            return new CutResult(CutKind.NEED_MORE, Collections.emptyList());
        }

        /**
         * @return FALLBACK
         */
        public static CutResult fallback() {
            return new CutResult(CutKind.FALLBACK, Collections.emptyList());
        }
    }

    /**
     * 按「满 1 拆零单位」切批，并按筛选截止决定回传哪些单位首槽。
     *
     * @param orderedPlanTimes  升序计划点（已做开嘱日首次槽裁剪）
     * @param splitQuantities   与计划点等长的<strong>临床</strong>拆零量（禁止传入已抬量后的 1）
     * @param selectionDeadline 列表筛选截止；首槽晚于此者不回传
     * @param canExtendFurther  {@code true}=调用方还能把频次窗再往后扩；{@code false}=已触停嘱/前瞻硬顶
     * @return 切批结果
     */
    public static CutResult cutThroughBatchContainingDeadline(
        List<LocalDateTime> orderedPlanTimes,
        List<BigDecimal> splitQuantities,
        LocalDateTime selectionDeadline,
        boolean canExtendFurther) {
        if (orderedPlanTimes == null || splitQuantities == null || selectionDeadline == null) {
            return CutResult.fallback();
        }
        if (orderedPlanTimes.size() != splitQuantities.size()) {
            return CutResult.fallback();
        }
        if (orderedPlanTimes.isEmpty()) {
            return CutResult.success(Collections.emptyList());
        }

        // 已凑满单位的首槽（按出现顺序）
        List<LocalDateTime> completedUnitFirstTimes = new ArrayList<>();
        // 当前未满单位的首槽下标；-1 表示尚未开批
        int openUnitFirstIndex = -1;
        BigDecimal openUnitCumulative = BigDecimal.ZERO;

        for (int index = 0; index < orderedPlanTimes.size(); index++) {
            LocalDateTime planTime = orderedPlanTimes.get(index);
            BigDecimal slotSplit = splitQuantities.get(index);
            if (planTime == null || slotSplit == null || slotSplit.compareTo(BigDecimal.ZERO) <= 0) {
                // 计划点缺失或槽量非正：切批口径不可靠
                return CutResult.fallback();
            }
            if (slotSplit.compareTo(BigDecimal.ONE) >= 0) {
                // 单槽已满一整拆零：应由可拆日点逐槽执行，不走下限切批
                return CutResult.fallback();
            }
            if (openUnitFirstIndex < 0) {
                // 新单位从本槽起锚
                openUnitFirstIndex = index;
                openUnitCumulative = BigDecimal.ZERO;
            }
            openUnitCumulative = openUnitCumulative.add(slotSplit);
            if (openUnitCumulative.compareTo(BigDecimal.ONE) >= 0) {
                completedUnitFirstTimes.add(orderedPlanTimes.get(openUnitFirstIndex));
                // 超额余量滚入下一单位临床额度；下一槽再记为新单位首槽
                openUnitCumulative = openUnitCumulative.subtract(BigDecimal.ONE);
                openUnitFirstIndex = -1;
            }
        }

        List<LocalDateTime> selected = new ArrayList<>();
        for (LocalDateTime unitFirst : completedUnitFirstTimes) {
            if (!unitFirst.isAfter(selectionDeadline)) {
                selected.add(unitFirst);
            }
        }

        boolean openUnitTouchesDeadline = false;
        if (openUnitFirstIndex >= 0) {
            LocalDateTime openFirst = orderedPlanTimes.get(openUnitFirstIndex);
            if (!openFirst.isAfter(selectionDeadline)) {
                openUnitTouchesDeadline = true;
            }
        }

        // 截止落在未满单位内：能扩窗则继续凑满；硬顶则仍回传首槽（与写路径「先发一整拆零」一致）
        if (openUnitTouchesDeadline) {
            if (canExtendFurther) {
                return CutResult.needMore();
            }
            selected.add(orderedPlanTimes.get(openUnitFirstIndex));
            return CutResult.success(selected);
        }

        if (selected.isEmpty()) {
            return CutResult.success(Collections.emptyList());
        }

        LocalDateTime lastKnown = orderedPlanTimes.get(orderedPlanTimes.size() - 1);
        // 截止晚于当前窗末槽：下一单位首槽可能还没展开到
        if (lastKnown.isBefore(selectionDeadline) && canExtendFurther) {
            return CutResult.needMore();
        }
        return CutResult.success(selected);
    }

    /**
     * 临床拆零量是否触发本规则（严格小于 1 个拆零单位）。
     *
     * @param clinicalSplitQuantity 临床拆零量
     * @return true=应切批（列表）/抬量（写路径）
     */
    public static boolean isBelowOneSplitUnit(BigDecimal clinicalSplitQuantity) {
        if (clinicalSplitQuantity == null) {
            return false;
        }
        if (clinicalSplitQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        return clinicalSplitQuantity.compareTo(BigDecimal.ONE) < 0;
    }

    /**
     * 切批成功计划点转有序 Set（与一盒一点 {@code StandingOrderPackageBatchExpandSupport} 同形）。
     *
     * @param planTimes 计划点
     * @return 保序集合
     */
    public static Set<LocalDateTime> toLinkedPlanTimeSet(List<LocalDateTime> planTimes) {
        if (planTimes == null || planTimes.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return new LinkedHashSet<>(planTimes);
    }
}
