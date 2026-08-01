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
 * 住院长嘱「不可拆」包装批次切分（仅列表读路径消费）。
 *
 * <p>在<strong>已应用首日/首次槽裁剪与首次槽换量</strong>之后，按槽拆零量顺序累加；累加值须
 * <strong>刚好等于</strong>一包装拆零量才切一盒。筛选截止时刻落在第 N 盒时，返回第 1～N 盒全部时点
 * （可晚于截止时刻，展满该盒）。凑不出刚好一盒、或单槽超量导致无法整除时，调用方须降级为可拆展开。</p>
 */
public final class StandingOrderPackageBatchExpandSupport {

    private StandingOrderPackageBatchExpandSupport() {
    }

    /**
     * 切盒结果种类。
     */
    public enum CutKind {
        /** 已得到覆盖截止时刻的完整盒批次，可安全展满到第 N 盒末。 */
        SUCCESS,
        /** 截止时刻落入尚未凑满的当前盒，且调用方仍可继续向后展开频次点。 */
        NEED_MORE,
        /** 无法刚好整除或入参不足；调用方应按可拆口径展开。 */
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
         * SUCCESS 时为第 1～N 盒计划点；其它种类为空列表。
         *
         * @return 计划点列表（不可变意图，调用方可再包 Set）
         */
        public List<LocalDateTime> getPlanTimes() {
            return planTimes;
        }

        /**
         * 构造成功结果。
         *
         * @param planTimes 第 1～N 盒时点
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
     * 在已展开的有序计划点上按包装拆零量切盒，并按筛选截止时刻决定展到第几盒。
     *
     * <p>规则：</p>
     * <ul>
     *   <li>累加拆零量须刚好等于 {@code packageSplitQuantity} 才关闭一盒；一旦 {@code cum + qty > package} 则 FALLBACK</li>
     *   <li>N = 最大满足「该盒首槽 planTime ≤ deadline」的完整盒序号</li>
     *   <li>若截止时刻已落入尚未凑满的当前盒：可继续展开则 NEED_MORE；调用方已达停嘱等硬上界则 FALLBACK（不硬凑盒）</li>
     *   <li>若第 N 盒已凑满但截止仍晚于该盒末槽、且窗内尚无更晚点：可继续展开则 NEED_MORE（避免截止已进下一盒区间却只展到 N）</li>
     * </ul>
     *
     * @param orderedPlanTimes       已按时间升序、已做首次槽裁剪的计划点
     * @param splitQuantities        与计划点一一对应的拆零数量（含首次槽换量后的量）
     * @param packageSplitQuantity   一包装单位对应的拆零量（如 1 盒 = 24 片）
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
                // 槽量非法：无法按包装整除切盒，线下自行处理
                return CutResult.fallback();
            }
            // 单槽已超过一包装，或累加将越过包装且不能刚好落在包装边界 → 不可拆切盒失败
            if (slotSplit.compareTo(packageSplitQuantity) > 0) {
                return CutResult.fallback();
            }
            BigDecimal nextCumulative = cumulative.add(slotSplit);
            if (nextCumulative.compareTo(packageSplitQuantity) > 0) {
                return CutResult.fallback();
            }
            cumulative = nextCumulative;
            currentBatch.add(index);
            if (cumulative.compareTo(packageSplitQuantity) == 0) {
                completeBatches.add(new ArrayList<>(currentBatch));
                currentBatch = new ArrayList<>();
                cumulative = BigDecimal.ZERO;
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
            // 截止落在未凑满盒内：能继续扩窗则再探；已到停嘱/探测顶则降级可拆（不硬凑）
            if (canExtendFurther) {
                return CutResult.needMore();
            }
            return CutResult.fallback();
        }

        if (batchCountCoveringDeadline <= 0) {
            // 截止早于任何完整盒首槽：无可展包装批次
            return CutResult.success(Collections.emptyList());
        }

        List<Integer> coveringBatch = completeBatches.get(batchCountCoveringDeadline - 1);
        int lastIndexOfCovering = coveringBatch.get(coveringBatch.size() - 1);
        LocalDateTime lastOfCovering = orderedPlanTimes.get(lastIndexOfCovering);
        // 截止已晚于当前第 N 盒末槽，且探测窗内尚无更晚时点 → 下一盒未展开，须继续探测
        if (lastOfCovering.isBefore(selectionDeadline)
            && lastIndexOfCovering >= orderedPlanTimes.size() - 1
            && canExtendFurther) {
            return CutResult.needMore();
        }

        List<LocalDateTime> selected = new ArrayList<>();
        for (int batchIndex = 0; batchIndex < batchCountCoveringDeadline; batchIndex++) {
            List<Integer> batch = completeBatches.get(batchIndex);
            for (Integer slotIndex : batch) {
                selected.add(orderedPlanTimes.get(slotIndex));
            }
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
