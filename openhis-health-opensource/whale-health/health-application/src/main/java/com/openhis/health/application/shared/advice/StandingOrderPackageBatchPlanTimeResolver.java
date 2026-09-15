/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.advice;

import com.openhis.health.domain.administration.product.support.ProductDefinitionDisplaySupport;
import com.openhis.health.domain.custom.frequency.queries.model.FrequencyExecutionTimePointQueryModel;
import com.openhis.health.domain.custom.frequency.service.FrequencyQueryService;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.enums.TherapyType;
import com.openhis.health.domain.share.product.MedicationProductUnitConversionSource;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.workflow.inventoryitem.support.MedicationProductQuantityToSplitUnitConverter;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院长嘱计划点解析（可拆频次 + 院点一盒一点 + 产品拆零下限）。
 *
 * <p>IF-IP-EXE-01 列表与 BR-IP-NUR-005 待执计数共用，避免两套展槽漂移。</p>
 * <ul>
 *   <li><strong>BR-IP-EXE-035</strong>（院点）：不可拆开关 + 口服 + 包装≠拆零 → 按 {@code packageUnitRatio}
 *       刚好整除切盒，回传盒首锚点</li>
 *   <li><strong>BR-IP-EXE-036</strong>（产品默认）：单槽临床拆零 &lt; 1 → 按满 1 拆零单位切批，回传单位首槽；
 *       与 035 正交，命中 035 终态后不再叠 036</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class StandingOrderPackageBatchPlanTimeResolver {

    /** 频次时钟展开（禁止 App 写死点钟）。 */
    private final FrequencyQueryService frequencyQueryService;

    /**
     * 请求内原始频次展开缓存（rateCode|start|end → 未裁剪开立日的时点）；
     * 护士站列表/待执计数同窗复用，避免同频次重复 CPU 展开。
     */
    private final ThreadLocal<Map<String, List<LocalDateTime>>> rawExpandPointsCache =
        ThreadLocal.withInitial(HashMap::new);

    /**
     * 清空当前线程的频次展开缓存（列表/计数编排结束时调用）。
     */
    public void clearRawExpandPointsCache() {
        rawExpandPointsCache.remove();
    }

    /**
     * 药嘱槽量换算入参（数量/单位/用法；不依赖 Feature QueryModel）。
     */
    public static final class MeasureInput {
        /** 治疗类型 code（长临）。 */
        private String therapyEnum;
        /** 常规单次量（长嘱）或总量（临嘱）。 */
        private BigDecimal quantity;
        /** 常规发药单位。 */
        private String unitCode;
        /** 首日/首次用量；可空。 */
        private BigDecimal firstDose;
        /** 首日单位；可空。 */
        private String firstDoseUnitCode;
        /** 用法 code（一盒一点口服类判定）。 */
        private String methodCode;

        /**
         * @return 治疗类型
         */
        public String getTherapyEnum() {
            return therapyEnum;
        }

        /**
         * @param therapyEnum 治疗类型
         */
        public void setTherapyEnum(String therapyEnum) {
            this.therapyEnum = therapyEnum;
        }

        /**
         * @return 常规数量
         */
        public BigDecimal getQuantity() {
            return quantity;
        }

        /**
         * @param quantity 常规数量
         */
        public void setQuantity(BigDecimal quantity) {
            this.quantity = quantity;
        }

        /**
         * @return 常规单位
         */
        public String getUnitCode() {
            return unitCode;
        }

        /**
         * @param unitCode 常规单位
         */
        public void setUnitCode(String unitCode) {
            this.unitCode = unitCode;
        }

        /**
         * @return 首日量
         */
        public BigDecimal getFirstDose() {
            return firstDose;
        }

        /**
         * @param firstDose 首日量
         */
        public void setFirstDose(BigDecimal firstDose) {
            this.firstDose = firstDose;
        }

        /**
         * @return 首日单位
         */
        public String getFirstDoseUnitCode() {
            return firstDoseUnitCode;
        }

        /**
         * @param firstDoseUnitCode 首日单位
         */
        public void setFirstDoseUnitCode(String firstDoseUnitCode) {
            this.firstDoseUnitCode = firstDoseUnitCode;
        }

        /**
         * @return 用法 code
         */
        public String getMethodCode() {
            return methodCode;
        }

        /**
         * @param methodCode 用法 code
         */
        public void setMethodCode(String methodCode) {
            this.methodCode = methodCode;
        }
    }

    /**
     * 长期计划点展开结果。
     */
    public static final class ExpandResult {
        /** 计划点：可拆=日频次点；批次模式=盒首/单位首槽。 */
        private Set<LocalDateTime> planTimes = new LinkedHashSet<>();
        /** true=已按一盒一点回传盒首（非空 SUCCESS）。 */
        private boolean packageBatchApplied;
        /** true=已按拆零下限回传单位首槽（BR-IP-EXE-036）。 */
        private boolean minSplitUnitBatchApplied;
        /**
         * true=一盒一点路径已得出终态（含 SUCCESS 空锚点）。
         * 为 true 时禁止再叠拆零下限日点，避免停嘱硬顶空结果被 036 冲掉。
         */
        private boolean packageBatchResolved;

        /**
         * @return 计划点
         */
        public Set<LocalDateTime> getPlanTimes() {
            return planTimes;
        }

        /**
         * @param planTimes 计划点
         */
        public void setPlanTimes(Set<LocalDateTime> planTimes) {
            this.planTimes = planTimes;
        }

        /**
         * @return 是否一盒一点
         */
        public boolean isPackageBatchApplied() {
            return packageBatchApplied;
        }

        /**
         * @param packageBatchApplied 是否一盒一点
         */
        public void setPackageBatchApplied(boolean packageBatchApplied) {
            this.packageBatchApplied = packageBatchApplied;
        }

        /**
         * @return 是否拆零下限切批
         */
        public boolean isMinSplitUnitBatchApplied() {
            return minSplitUnitBatchApplied;
        }

        /**
         * @param minSplitUnitBatchApplied 是否拆零下限切批
         */
        public void setMinSplitUnitBatchApplied(boolean minSplitUnitBatchApplied) {
            this.minSplitUnitBatchApplied = minSplitUnitBatchApplied;
        }

        /**
         * @return 一盒一点是否已结算
         */
        public boolean isPackageBatchResolved() {
            return packageBatchResolved;
        }

        /**
         * @param packageBatchResolved 一盒一点是否已结算
         */
        public void setPackageBatchResolved(boolean packageBatchResolved) {
            this.packageBatchResolved = packageBatchResolved;
        }

        /**
         * 列表 pending 是否按「同时至多一个锚点」收窄（一盒一点或拆零下限）。
         *
         * @return true=批次锚点模式
         */
        public boolean isBatchedAnchorApplied() {
            if (packageBatchApplied) {
                return true;
            }
            return minSplitUnitBatchApplied;
        }
    }

    /**
     * 解析长期医嘱在截止窗内的计划点。
     *
     * <p>顺序：可拆日点 →（可选）一盒一点 →（否则）拆零下限切批 → 回落可拆。</p>
     *
     * @param rateCode                       频次 code
     * @param openedTime                     开立时刻
     * @param executionDeadline              列表/计数截止上界
     * @param stopHardCap                    停嘱硬顶；可空
     * @param applyStandingOrderPackageBatch 全局不可拆时为 true
     * @param measure                        槽量/用法；批次切分必填
     * @param product                        药品目录；批次切分用
     * @param methodLabelByCode              用法 code→展示名
     * @return 计划点与切批标志
     * @throws BusinessException 频次域不可用（与执行列表同口径）
     */
    public ExpandResult resolveLongTermPlanTimes(
        String rateCode,
        LocalDateTime openedTime,
        LocalDateTime executionDeadline,
        LocalDateTime stopHardCap,
        boolean applyStandingOrderPackageBatch,
        MeasureInput measure,
        ProductDefinitionDisplayDto product,
        Map<String, String> methodLabelByCode) {
        ExpandResult result = new ExpandResult();
        result.setPlanTimes(new LinkedHashSet<>());
        result.setPackageBatchApplied(false);
        result.setMinSplitUnitBatchApplied(false);
        if (executionDeadline == null || openedTime == null || StringUtils.isEmpty(rateCode)) {
            return result;
        }
        LocalDateTime splittableEnd = executionDeadline;
        if (stopHardCap != null && stopHardCap.isBefore(splittableEnd)) {
            // 可拆右界不得超过停嘱，否则会展出停嘱后仍待执的假点
            splittableEnd = stopHardCap;
        }
        LocalDateTime expandStart = openedTime.toLocalDate().atStartOfDay();
        if (!splittableEnd.isAfter(expandStart)) {
            return result;
        }
        Set<LocalDateTime> splittablePlanned =
            this.expandLongTermPlanTimes(rateCode, expandStart, splittableEnd, openedTime);
        if (measure == null) {
            // 无槽量：无法切批，仅回传可拆日点
            result.setPlanTimes(splittablePlanned);
            return result;
        }
        if (!TherapyType.LONG_TERM.getCode().equals(measure.getTherapyEnum())) {
            result.setPlanTimes(splittablePlanned);
            return result;
        }
        if (applyStandingOrderPackageBatch
            && StandingOrderPackageBatchEligibilitySupport.isEligibleWithoutSplittableSwitch(
                measure.getTherapyEnum(),
                measure.getMethodCode(),
                methodLabelByCode,
                product)) {
            ExpandResult packageBatchResult = this.tryPackageBatchPlanTimes(
                rateCode,
                openedTime,
                executionDeadline,
                stopHardCap,
                splittableEnd,
                expandStart,
                splittablePlanned,
                measure,
                product);
            // 一盒一点已 SUCCESS（含空锚点）：停嘱硬顶未凑满盒时禁止再被拆零下限展成日点
            if (packageBatchResult.isPackageBatchResolved()) {
                return packageBatchResult;
            }
        }
        // 未命中一盒一点：尝试产品拆零下限（半片/0.0x 支等）
        ExpandResult minSplitResult = this.tryMinSplitUnitBatchPlanTimes(
            rateCode,
            openedTime,
            executionDeadline,
            stopHardCap,
            expandStart,
            splittablePlanned,
            measure,
            product);
        if (minSplitResult.isMinSplitUnitBatchApplied()) {
            return minSplitResult;
        }
        result.setPlanTimes(splittablePlanned);
        return result;
    }

    /**
     * 院点一盒一点：按包装拆零比刚好切盒；失败回落可拆日点。
     *
     * @param rateCode          频次
     * @param openedTime        开立
     * @param executionDeadline 列表截止（选盒上界）
     * @param stopHardCap       停嘱硬顶
     * @param splittableEnd     可拆右界（作探测起点）
     * @param expandStart       展开左界（开立日零点）
     * @param splittablePlanned 可拆日点（降级用）
     * @param measure           槽量
     * @param product           目录（取 packageUnitRatio）
     * @return 切盒或降级结果
     */
    private ExpandResult tryPackageBatchPlanTimes(
        String rateCode,
        LocalDateTime openedTime,
        LocalDateTime executionDeadline,
        LocalDateTime stopHardCap,
        LocalDateTime splittableEnd,
        LocalDateTime expandStart,
        Set<LocalDateTime> splittablePlanned,
        MeasureInput measure,
        ProductDefinitionDisplayDto product) {
        ExpandResult result = this.newEmptyExpandResult();
        if (product == null
            || product.getPackageUnitRatio() == null
            || product.getPackageUnitRatio().compareTo(BigDecimal.ZERO) <= 0) {
            // 无包装拆零比：切不了盒
            result.setPlanTimes(splittablePlanned);
            return result;
        }
        BigDecimal packageSplitQuantity = product.getPackageUnitRatio();
        LocalDateTime maxProbeEnd = this.resolveBatchProbeHardCap(openedTime, stopHardCap);
        LocalDateTime probeEnd = splittableEnd;
        for (int round = 0; round < InpatientNurseStationConstants.STANDING_ORDER_BATCH_PROBE_MAX_ROUNDS; round++) {
            List<LocalDateTime> ordered = this.expandOrderedPlanTimes(
                rateCode, expandStart, probeEnd, openedTime);
            List<BigDecimal> splitQuantities = this.resolveSlotSplitQuantities(
                ordered, openedTime, measure, product);
            if (splitQuantities == null) {
                // 单位换算失败：整单降级可拆，避免半切半不切
                result.setPlanTimes(splittablePlanned);
                return result;
            }
            boolean canExtendFurther = probeEnd.isBefore(maxProbeEnd);
            StandingOrderPackageBatchExpandSupport.CutResult cut =
                StandingOrderPackageBatchExpandSupport.cutThroughBatchContainingDeadline(
                    ordered,
                    splitQuantities,
                    packageSplitQuantity,
                    executionDeadline,
                    canExtendFurther);
            if (StandingOrderPackageBatchExpandSupport.CutKind.SUCCESS.equals(cut.getKind())) {
                result.setPlanTimes(
                    StandingOrderPackageBatchExpandSupport.toLinkedPlanTimeSet(cut.getPlanTimes()));
                result.setPackageBatchApplied(!cut.getPlanTimes().isEmpty());
                result.setPackageBatchResolved(true);
                return result;
            }
            if (StandingOrderPackageBatchExpandSupport.CutKind.FALLBACK.equals(cut.getKind())) {
                // 累加无法刚好整除包装：与可拆同形，禁止硬凑
                result.setPlanTimes(splittablePlanned);
                return result;
            }
            // NEED_MORE：截止落在未满盒或下一盒未展开 → 把频次窗再往后推
            probeEnd = this.advanceBatchProbeEnd(probeEnd, maxProbeEnd);
            if (!canExtendFurther) {
                // 已触停嘱/前瞻硬顶：只收已完整盒首，禁止把开嘱～停嘱日点再拆出来（BR-IP-EXE-035⑤）
                StandingOrderPackageBatchExpandSupport.CutResult hardCapCut =
                    StandingOrderPackageBatchExpandSupport.cutThroughBatchContainingDeadline(
                        ordered,
                        splitQuantities,
                        packageSplitQuantity,
                        executionDeadline,
                        false);
                return this.finishPackageBatchWhenCannotExtend(hardCapCut, splittablePlanned);
            }
        }
        // 探测轮次耗尽仍未切出完整盒：按硬顶未满盒收口为空锚点，禁止降级全日点
        return this.emptyResolvedPackageBatch();
    }

    /**
     * 停嘱/探测硬顶后收口一盒一点：SUCCESS 用切盒结果；无法整除才降级可拆；其余禁止日点。
     *
     * @param hardCapCut         硬顶下第二次切盒结果
     * @param splittablePlanned  可拆日点（仅 BR-IP-EXE-035⑥ 无法整除时使用）
     * @return 已结算的一盒一点结果
     */
    private ExpandResult finishPackageBatchWhenCannotExtend(
        StandingOrderPackageBatchExpandSupport.CutResult hardCapCut,
        Set<LocalDateTime> splittablePlanned) {
        if (StandingOrderPackageBatchExpandSupport.CutKind.SUCCESS.equals(hardCapCut.getKind())) {
            ExpandResult success = this.newEmptyExpandResult();
            success.setPlanTimes(
                StandingOrderPackageBatchExpandSupport.toLinkedPlanTimeSet(hardCapCut.getPlanTimes()));
            List<LocalDateTime> hardCapPlanTimes = hardCapCut.getPlanTimes();
            if (hardCapPlanTimes != null && !hardCapPlanTimes.isEmpty()) {
                success.setPackageBatchApplied(true);
            }
            success.setPackageBatchResolved(true);
            return success;
        }
        if (StandingOrderPackageBatchExpandSupport.CutKind.FALLBACK.equals(hardCapCut.getKind())) {
            // 单槽超量或累加无法整除包装：BR-IP-EXE-035⑥ 允许降级可拆
            ExpandResult fallback = this.newEmptyExpandResult();
            fallback.setPlanTimes(splittablePlanned);
            return fallback;
        }
        // NEED_MORE 等：硬顶未凑满当前盒，禁止 FALLBACK 成整窗日点，否则出院门禁会误拦
        return this.emptyResolvedPackageBatch();
    }

    /**
     * 一盒一点已得出终态但无可展盒首（停嘱硬顶未满盒 / 探测耗尽）。
     *
     * @return packageBatchResolved=true 且计划点为空
     */
    private ExpandResult emptyResolvedPackageBatch() {
        ExpandResult result = this.newEmptyExpandResult();
        result.setPlanTimes(new LinkedHashSet<>());
        result.setPackageBatchResolved(true);
        return result;
    }

    /**
     * 产品拆零下限：临床单次拆零 &lt; 1 时按满 1 拆零单位切批。
     *
     * @param rateCode          频次
     * @param openedTime        开立
     * @param executionDeadline 列表截止
     * @param stopHardCap       停嘱硬顶
     * @param expandStart       展开左界
     * @param splittablePlanned 可拆日点（降级用）
     * @param measure           槽量
     * @param product           目录（取 splitUnitCode）
     * @return 切批或降级结果
     */
    private ExpandResult tryMinSplitUnitBatchPlanTimes(
        String rateCode,
        LocalDateTime openedTime,
        LocalDateTime executionDeadline,
        LocalDateTime stopHardCap,
        LocalDateTime expandStart,
        Set<LocalDateTime> splittablePlanned,
        MeasureInput measure,
        ProductDefinitionDisplayDto product) {
        ExpandResult result = this.newEmptyExpandResult();
        if (product == null || StringUtils.isEmpty(product.getSplitUnitCode())) {
            result.setPlanTimes(splittablePlanned);
            return result;
        }
        BigDecimal regularSplit = this.resolveRegularSlotSplitQuantity(measure, product);
        if (!StandingOrderMinSplitUnitBatchExpandSupport.isBelowOneSplitUnit(regularSplit)) {
            // 常规单次已满一整拆零：无需切批，保持日点
            result.setPlanTimes(splittablePlanned);
            return result;
        }
        LocalDateTime maxProbeEnd = this.resolveBatchProbeHardCap(openedTime, stopHardCap);
        LocalDateTime probeEnd = executionDeadline;
        if (stopHardCap != null && stopHardCap.isBefore(probeEnd)) {
            // 探测起点也不越过停嘱
            probeEnd = stopHardCap;
        }
        for (int round = 0; round < InpatientNurseStationConstants.STANDING_ORDER_BATCH_PROBE_MAX_ROUNDS; round++) {
            List<LocalDateTime> ordered = this.expandOrderedPlanTimes(
                rateCode, expandStart, probeEnd, openedTime);
            List<BigDecimal> splitQuantities = this.resolveSlotSplitQuantities(
                ordered, openedTime, measure, product);
            if (splitQuantities == null) {
                result.setPlanTimes(splittablePlanned);
                return result;
            }
            boolean canExtendFurther = probeEnd.isBefore(maxProbeEnd);
            StandingOrderMinSplitUnitBatchExpandSupport.CutResult cut =
                StandingOrderMinSplitUnitBatchExpandSupport.cutThroughBatchContainingDeadline(
                    ordered,
                    splitQuantities,
                    executionDeadline,
                    canExtendFurther);
            if (StandingOrderMinSplitUnitBatchExpandSupport.CutKind.SUCCESS.equals(cut.getKind())) {
                result.setPlanTimes(
                    StandingOrderMinSplitUnitBatchExpandSupport.toLinkedPlanTimeSet(cut.getPlanTimes()));
                result.setMinSplitUnitBatchApplied(!cut.getPlanTimes().isEmpty());
                return result;
            }
            if (StandingOrderMinSplitUnitBatchExpandSupport.CutKind.FALLBACK.equals(cut.getKind())) {
                result.setPlanTimes(splittablePlanned);
                return result;
            }
            // NEED_MORE：未满单位还需更多频次点才能判定下一单位首槽
            probeEnd = this.advanceBatchProbeEnd(probeEnd, maxProbeEnd);
            if (!canExtendFurther) {
                // 硬顶：未满单位仍回传首槽（首滴即开整拆零单位）
                StandingOrderMinSplitUnitBatchExpandSupport.CutResult hardCapCut =
                    StandingOrderMinSplitUnitBatchExpandSupport.cutThroughBatchContainingDeadline(
                        ordered,
                        splitQuantities,
                        executionDeadline,
                        false);
                if (StandingOrderMinSplitUnitBatchExpandSupport.CutKind.SUCCESS.equals(hardCapCut.getKind())) {
                    result.setPlanTimes(
                        StandingOrderMinSplitUnitBatchExpandSupport.toLinkedPlanTimeSet(
                            hardCapCut.getPlanTimes()));
                    result.setMinSplitUnitBatchApplied(!hardCapCut.getPlanTimes().isEmpty());
                    return result;
                }
                result.setPlanTimes(splittablePlanned);
                return result;
            }
        }
        result.setPlanTimes(splittablePlanned);
        return result;
    }

    /**
     * 构造空展开结果（切批标志均为 false）。
     *
     * @return 空结果
     */
    private ExpandResult newEmptyExpandResult() {
        ExpandResult result = new ExpandResult();
        result.setPackageBatchApplied(false);
        result.setMinSplitUnitBatchApplied(false);
        result.setPackageBatchResolved(false);
        return result;
    }

    /**
     * 批次探测硬顶：开嘱日 + 前瞻天数的自然日末，且不得超过停嘱。
     *
     * <p>日末取 {@link InpatientNurseStationConstants#DAY_END_CLOCK}（23:59:59），
     * 表示「该自然日全天频次点均可参与凑批」，而非把某个业务执行钟点写死。</p>
     *
     * @param openedTime  开立时刻
     * @param stopHardCap 停嘱硬顶；可空
     * @return 探测窗最大右界
     */
    private LocalDateTime resolveBatchProbeHardCap(LocalDateTime openedTime, LocalDateTime stopHardCap) {
        LocalDateTime maxProbeEnd = openedTime.toLocalDate()
            .plusDays(InpatientNurseStationConstants.STANDING_ORDER_PACKAGE_BATCH_MAX_LOOKAHEAD_DAYS)
            .atTime(InpatientNurseStationConstants.DAY_END_CLOCK);
        if (stopHardCap != null && stopHardCap.isBefore(maxProbeEnd)) {
            // 停嘱后不再执行：探测不得越过停嘱凑下一盒/下一单位
            return stopHardCap;
        }
        return maxProbeEnd;
    }

    /**
     * 将探测右界按步进天数后推，且不超过硬顶。
     *
     * @param probeEnd    当前探测右界
     * @param maxProbeEnd 硬顶
     * @return 下一探测右界
     */
    private LocalDateTime advanceBatchProbeEnd(LocalDateTime probeEnd, LocalDateTime maxProbeEnd) {
        LocalDateTime nextProbe = probeEnd.plusDays(
            InpatientNurseStationConstants.STANDING_ORDER_PACKAGE_BATCH_EXPAND_STEP_DAYS);
        if (!nextProbe.isBefore(maxProbeEnd)) {
            return maxProbeEnd;
        }
        return nextProbe;
    }

    /**
     * 频次展开 + 开嘱日首次槽裁剪后，按时间升序的计划点列表。
     *
     * @param rateCode    频次
     * @param expandStart 左界
     * @param expandEnd   右界
     * @param openedTime  开立
     * @return 有序计划点
     */
    private List<LocalDateTime> expandOrderedPlanTimes(
        String rateCode,
        LocalDateTime expandStart,
        LocalDateTime expandEnd,
        LocalDateTime openedTime) {
        Set<LocalDateTime> probed =
            this.expandLongTermPlanTimes(rateCode, expandStart, expandEnd, openedTime);
        List<LocalDateTime> ordered = new ArrayList<>(probed);
        Collections.sort(ordered);
        return ordered;
    }

    /**
     * 解析常规单次（非首日槽）临床拆零量，判定是否触发拆零下限（BR-IP-EXE-036 / 038 MIN_SPLIT）。
     *
     * @param measure 槽量
     * @param product 目录
     * @return 拆零量；失败 null
     */
    public BigDecimal resolveRegularClinicalSplitQuantity(
        MeasureInput measure,
        ProductDefinitionDisplayDto product) {
        return this.resolveRegularSlotSplitQuantity(measure, product);
    }

    /**
     * 解析常规单次（非首日槽）临床拆零量，判定是否触发拆零下限。
     *
     * @param measure 槽量
     * @param product 目录
     * @return 拆零量；失败 null
     */
    private BigDecimal resolveRegularSlotSplitQuantity(
        MeasureInput measure,
        ProductDefinitionDisplayDto product) {
        if (measure == null || product == null) {
            return null;
        }
        BigDecimal qty = InpatientMedicationRequestExecutionMeasureHelper.resolveDispenseQuantityFromRequest(
            measure.getQuantity());
        String unitCode = InpatientMedicationRequestExecutionMeasureHelper.resolveDispenseUnitCodeFromRequest(
            measure.getUnitCode());
        MedicationProductUnitConversionSource conversionSource =
            ProductDefinitionDisplaySupport.toUnitConversionSource(product);
        if (conversionSource == null || StringUtils.isEmpty(unitCode)) {
            return null;
        }
        try {
            return MedicationProductQuantityToSplitUnitConverter.medicationQuantityToSplitUnit(
                conversionSource, unitCode, qty);
        } catch (BusinessException conversionFailure) {
            // 换算失败：上层按「不触发切批」处理
            return null;
        }
    }

    /**
     * 长期频次展开 + 开立日首次槽裁剪。
     *
     * @param rateCode    频次
     * @param expandStart 左界（开立日零点）
     * @param expandEnd   右界
     * @param openedTime  开立时刻
     * @return 裁剪后计划点
     * @throws BusinessException 频次不可用
     */
    public Set<LocalDateTime> expandLongTermPlanTimes(
        String rateCode,
        LocalDateTime expandStart,
        LocalDateTime expandEnd,
        LocalDateTime openedTime) {
        Set<LocalDateTime> planned = new LinkedHashSet<>();
        if (StringUtils.isEmpty(rateCode) || expandStart == null || expandEnd == null || openedTime == null) {
            return planned;
        }
        if (!expandEnd.isAfter(expandStart)) {
            return planned;
        }
        // Pre：同 rateCode + 窗复用原始展开点，再按本嘱开立日裁首次槽
        String cacheKey = rateCode + "|" + expandStart + "|" + expandEnd;
        Map<String, List<LocalDateTime>> cache = rawExpandPointsCache.get();
        List<LocalDateTime> rawTimes = cache.get(cacheKey);
        if (rawTimes == null) {
            List<FrequencyExecutionTimePointQueryModel> points =
                frequencyQueryService.computeExecutionTimePoints(rateCode, expandStart, expandEnd);
            rawTimes = new ArrayList<>();
            for (FrequencyExecutionTimePointQueryModel point : points) {
                if (point != null && point.getExecutionAt() != null) {
                    // 秒级归一，与 Procedure occurrence 分组合并键一致
                    rawTimes.add(point.getExecutionAt().withNano(0));
                }
            }
            cache.put(cacheKey, rawTimes);
        }
        planned.addAll(LongTermFirstExecutionSlotSupport.applyOpenedDayFirstSlotRule(rawTimes, openedTime));
        return planned;
    }

    /**
     * 按槽解析<strong>临床</strong>拆零用量（首次槽可换 firstDose；禁止抬量后再用于切批累加）。
     *
     * <p>供一盒一点切盒与口服剩余计数（BR-IP-EXE-038）同源换算，禁止两套槽量口径。</p>
     *
     * @param orderedPlanTimes 有序计划点
     * @param openedTime       开立时刻
     * @param measure          数量/单位
     * @param product          目录
     * @return 与计划点等长的拆零量；任一槽换算失败返回 null
     */
    public List<BigDecimal> resolveSlotSplitQuantities(
        List<LocalDateTime> orderedPlanTimes,
        LocalDateTime openedTime,
        MeasureInput measure,
        ProductDefinitionDisplayDto product) {
        if (orderedPlanTimes == null || measure == null || product == null) {
            return null;
        }
        LocalDateTime firstPlanTime = LongTermFirstExecutionSlotSupport.resolveFirstPlanTimeOnOpenedDay(
            orderedPlanTimes, openedTime);
        MedicationProductUnitConversionSource conversionSource =
            ProductDefinitionDisplaySupport.toUnitConversionSource(product);
        if (conversionSource == null) {
            return null;
        }
        List<BigDecimal> splitQuantities = new ArrayList<>(orderedPlanTimes.size());
        for (LocalDateTime planTime : orderedPlanTimes) {
            BigDecimal qty = InpatientMedicationRequestExecutionMeasureHelper.resolveDispenseQuantityForExecutionSlot(
                measure.getTherapyEnum(),
                measure.getQuantity(),
                measure.getFirstDose(),
                planTime,
                firstPlanTime);
            String unitCode = InpatientMedicationRequestExecutionMeasureHelper.resolveDispenseUnitCodeForExecutionSlot(
                measure.getTherapyEnum(),
                measure.getUnitCode(),
                measure.getFirstDoseUnitCode(),
                planTime,
                firstPlanTime);
            try {
                BigDecimal split = MedicationProductQuantityToSplitUnitConverter.medicationQuantityToSplitUnit(
                    conversionSource, unitCode, qty);
                splitQuantities.add(split);
            } catch (BusinessException conversionFailure) {
                // 任一槽无法换算：整单放弃切批，由调用方降级可拆
                return null;
            }
        }
        return splitQuantities;
    }
}
