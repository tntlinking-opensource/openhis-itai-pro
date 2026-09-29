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
 * 住院长嘱「不可拆 · 一盒一点」包装批次切分（列表读路径；写路径另按整包装量落账）。
 *
 * <p>在<strong>已应用首日/首次槽裁剪与首次槽换量</strong>之后，按槽拆零量顺序累加；
 * 累加值<strong>≥</strong>一包装拆零量即切一盒（ceil 封盒，余量结转下一盒；对齐 **BR-IP-EXE-038**
 * 「领一整盒、按临床量扣剩余」——剂量×频次不必整除包装比，如 3 片×BID / 盒 100 片）。
 * 筛选截止时刻落在第 N 盒时，仅回传第 1～N 盒的<strong>盒首锚点</strong>（每盒一个 planTime，
 * 形态对齐临嘱单点），<strong>禁止</strong>再展盒内全部频次点。
 * <strong>仅</strong>单槽超量（单次临床拆零 &gt; 一包装）时 FALLBACK 降级可拆；
 * <strong>禁止</strong>因「累加无法刚好整除」把盒内日频次点再拆出来。
 * <strong>停嘱/探测硬顶截断未凑满当前盒</strong>时<strong>不</strong>降级可拆，仅回传已凑满盒的盒首（无则空）。</p>
 */
public final class StandingOrderPackageBatchExpandSupport {

    private StandingOrderPackageBatchExpandSupport() {
    }

    /**
     * 切盒结果种类。
     */
    public enum CutKind {
        /** 已得到覆盖截止时刻的完整盒批次，回传第 1～N 盒首锚点。 */
        SUCCESS,
        /** 截止时刻落入尚未凑满的当前盒，或已越过第 N 盒末但下一盒未展开，调用方须继续向后展开频次点。 */
        NEED_MORE,
        /** 单槽超量或入参不足；调用方应按可拆口径展开（非整除不再走本态）。 */
        FALLBACK
    }

    /**
     * 切盒结果。
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
         * SUCCESS 时为第 1～N 盒<strong>盒首</strong>计划点（每盒一点）；其它种类为空列表。
         *
         * @return 计划点列表（不可变意图，调用方可再包 Set）
         */
        public List<LocalDateTime> getPlanTimes() {
            return planTimes;
        }

        /**
         * 构造成功结果。
         *
         * @param planTimes 第 1～N 盒首锚点
         * @return 结果
         */
        public static CutResult success(List<LocalDateTime> planTimes) {
            if (planTimes == null || planTimes.isEmpty()) {
                return new CutResult(CutKind.SUCCESS, Collections.emptyList());
            }
            return new CutResult(CutKind.SUCCESS, List.copyOf(planTimes));
        }

        /**
         * 构造成需继续向后展开。
         *
         * @return 结果
         */
        public static CutResult needMore() {
            return new CutResult(CutKind.NEED_MORE, Collections.emptyList());
        }

        /**
         * 构造降级可拆。
         *
         * @return 结果
         */
        public static CutResult fallback() {
            return new CutResult(CutKind.FALLBACK, Collections.emptyList());
        }
    }

    /**
     * 在已展开的有序计划点上按包装拆零量切盒，并按筛选截止时刻决定回传几盒的<strong>盒首锚点</strong>。
     *
     * <p>规则：</p>
     * <ul>
     *   <li>累加拆零量<strong>≥</strong>{@code packageSplitQuantity} 即关闭一盒（ceil）；超出部分结转下一盒累计，
     *       避免「3 片×日二次 / 盒 100」等非整除场景 FALLBACK 成全日频次点</li>
     *   <li>单槽拆零量 &gt; 一包装 → FALLBACK（一盒装不下单次临床量）</li>
     *   <li>N = 最大满足「该盒首槽 planTime ≤ deadline」的完整盒序号</li>
     *   <li>SUCCESS 仅回传各盒<strong>首槽</strong>时刻（一盒一点），不回传盒内其余频次点</li>
     *   <li>若截止时刻已落入尚未凑满的当前盒：可继续展开则 NEED_MORE；已达停嘱等硬上界则
     *       SUCCESS（仅已凑满盒首，无则空）——<strong>禁止</strong>降级可拆日点（停嘱后不再执行尾巴）</li>
     *   <li>若第 N 盒已凑满但截止仍晚于该盒末槽、且窗内尚无更晚点：可继续展开则 NEED_MORE（探测下一盒锚点）</li>
     * </ul>
     *
     * @param orderedPlanTimes       已按时间升序、已做首次槽裁剪的计划点
     * @param splitQuantities        与计划点一一对应的拆零数量（含首次槽换量后的量）
     * @param packageSplitQuantity   一包装单位对应的拆零量（如 1 盒 = 10 片）
     * @param selectionDeadline      列表筛选截止时刻（executionDeadline）
     * @param canExtendFurther       {@code true} 表示调用方还能把频次窗再往后扩；{@code false} 表示已触及停嘱/探测上限
     * @return 切盒结果
     */
    public static CutResult cutThroughBatchContainingDeadline(
        List<LocalDateTime> orderedPlanTimes,
        List<BigDecimal> splitQuantities,
        BigDecimal packageSplitQuantity,
        LocalDateTime selectionDeadline,
        boolean canExtendFurther) {
        if (orderedPlanTimes == null || splitQuantities == null || selectionDeadline == null) {
            return CutResult.fallback();
        }
        if (packageSplitQuantity == null || packageSplitQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            return CutResult.fallback();
        }
        if (orderedPlanTimes.size() != splitQuantities.size()) {
            return CutResult.fallback();
        }
        if (orderedPlanTimes.isEmpty()) {
            return CutResult.success(Collections.emptyList());
        }

        List<List<Integer>> completeBatches = new ArrayList<>();
        List<Integer> currentBatch = new ArrayList<>();
        BigDecimal cumulative = BigDecimal.ZERO;

        for (int index = 0; index < orderedPlanTimes.size(); index++) {
            LocalDateTime planTime = orderedPlanTimes.get(index);
            BigDecimal slotSplit = splitQuantities.get(index);
            if (planTime == null) {
                return CutResult.fallback();
            }
            if (slotSplit == null || slotSplit.compareTo(BigDecimal.ZERO) <= 0) {
                // 槽量非法：无法切盒，线下自行处理
                return CutResult.fallback();
            }
            // 单槽已超过一包装：一盒装不下该次临床量 → 降级可拆（BR-IP-EXE-035⑥）
            if (slotSplit.compareTo(packageSplitQuantity) > 0) {
                return CutResult.fallback();
            }
            // ceil 封盒：累加满一包装即关盒；余量结转，禁止因非整除 FALLBACK 成日点
            cumulative = cumulative.add(slotSplit);
            currentBatch.add(index);
            while (cumulative.compareTo(packageSplitQuantity) >= 0 && !currentBatch.isEmpty()) {
                // 关盒后清空当前槽列表；余量留在 cumulative 供下一盒累加
                completeBatches.add(new ArrayList<>(currentBatch));
                currentBatch = new ArrayList<>();
                cumulative = cumulative.subtract(packageSplitQuantity);
            }
        }

        int batchCountCoveringDeadline = 0;
        for (int batchIndex = 0; batchIndex < completeBatches.size(); batchIndex++) {
            List<Integer> batch = completeBatches.get(batchIndex);
            LocalDateTime batchFirst = orderedPlanTimes.get(batch.get(0));
            // 盒首不晚于截止 → 截止至少「触及」本盒，计入展出上界 N
            if (!batchFirst.isAfter(selectionDeadline)) {
                batchCountCoveringDeadline = batchIndex + 1;
            }
        }

        boolean incompleteTouchesDeadline = false;
        if (!currentBatch.isEmpty()) {
            LocalDateTime incompleteFirst = orderedPlanTimes.get(currentBatch.get(0));
            if (!incompleteFirst.isAfter(selectionDeadline)) {
                incompleteTouchesDeadline = true;
            }
        }

        if (incompleteTouchesDeadline) {
            // 截止落在未凑满盒内：能继续扩窗则再探；已到停嘱/探测顶则不硬凑、也不降级可拆日点
            if (canExtendFurther) {
                return CutResult.needMore();
            }
            // 硬顶截断：只回传此前已凑满盒的盒首；无完整盒则空（已执行盒首仍由 Procedure 并集展示）
            if (batchCountCoveringDeadline <= 0) {
                return CutResult.success(Collections.emptyList());
            }
            List<LocalDateTime> hardCapSelected = new ArrayList<>();
            for (int batchIndex = 0; batchIndex < batchCountCoveringDeadline; batchIndex++) {
                List<Integer> batch = completeBatches.get(batchIndex);
                hardCapSelected.add(orderedPlanTimes.get(batch.get(0)));
            }
            return CutResult.success(hardCapSelected);
        }

        if (batchCountCoveringDeadline <= 0) {
            // 截止早于任何完整盒首槽：无可展包装批次
            return CutResult.success(Collections.emptyList());
        }

        List<Integer> coveringBatch = completeBatches.get(batchCountCoveringDeadline - 1);
        int lastIndexOfCovering = coveringBatch.get(coveringBatch.size() - 1);
        LocalDateTime lastOfCovering = orderedPlanTimes.get(lastIndexOfCovering);
        // 截止已晚于当前第 N 盒末槽，且探测窗内尚无更晚时点 → 下一盒未展开，须继续探测盒首锚点
        if (lastOfCovering.isBefore(selectionDeadline)
            && lastIndexOfCovering >= orderedPlanTimes.size() - 1
            && canExtendFurther) {
            return CutResult.needMore();
        }

        // 一盒一点：仅取各完整盒的首槽作为待执行/已执行锚点
        List<LocalDateTime> selected = new ArrayList<>();
        for (int batchIndex = 0; batchIndex < batchCountCoveringDeadline; batchIndex++) {
            List<Integer> batch = completeBatches.get(batchIndex);
            selected.add(orderedPlanTimes.get(batch.get(0)));
        }
        return CutResult.success(selected);
    }

    /**
     * 将切盒成功的计划点转为 LinkedHashSet（保序、秒级归一由调用方保证）。
     *
     * @param planTimes 计划点
     * @return 有序集合
     */
    public static Set<LocalDateTime> toLinkedPlanTimeSet(List<LocalDateTime> planTimes) {
        if (planTimes == null || planTimes.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return new LinkedHashSet<>(planTimes);
    }
}
