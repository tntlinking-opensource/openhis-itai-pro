/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.advice;

import com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.domain.clinical.procedure.queries.model.ProcedureRecordQueryModel;
import com.openhis.health.domain.medication.medicationrequest.entity.MedicationRequest;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.EventStatus;
import com.openhis.health.domain.share.enums.TherapyType;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 住院长嘱「单位领用」理论剩余计数与待执行按天抑制（BR-IP-EXE-038）。
 *
 * <p>覆盖两类领用形态（算法同源，仅<strong>每次领入拆零量</strong>不同）：</p>
 * <ul>
 *   <li><strong>一盒一点（PACKAGE）</strong>：院点不可拆 + 口服包装切盒；每次净完成领入
 *       {@code packageUnitRatio} 拆零；写路径整盒仍由 BR-IP-EXE-035④ 负责。</li>
 *   <li><strong>拆零下限（MIN_SPLIT）</strong>：单次临床拆零 &lt; 1（如胰岛素 0.03 支、半片）；
 *       每次净完成领入 {@code 1} 拆零；写路径抬量仍由 BR-IP-EXE-036 负责。</li>
 * </ul>
 *
 * <p>读路径实时计算，不落库；医生站列表与护士站执行列表 / 待执计数 / 出院门禁 pending
 * 须同源调用本 Support。本类不改写路径 quantity。</p>
 */
@Component
@RequiredArgsConstructor
public class StandingOrderOralPackageInventorySupport {

    /** 向前模拟下次领用日的最大自然日数，避免无限循环。 */
    private static final int NEXT_CLAIM_LOOKAHEAD_DAYS = 120;

    private final MedicationRequestQueryService medicationRequestQueryService;
    private final StandingOrderPackageBatchPlanTimeResolver planTimeResolver;

    /**
     * 单位领用模式：决定每次净完成执行计入的领入拆零量。
     */
    public enum UnitClaimMode {
        /**
         * 一盒一点：领入 = 有效领盒次数 × packageUnitRatio。
         */
        PACKAGE,
        /**
         * 拆零下限：领入 = 有效单位首槽执行次数 × 1。
         */
        MIN_SPLIT
    }

    /**
     * 单位领用剩余快照（展示 + 抑制判定；字段名沿用 oralPackage* 契约）。
     */
    public static final class OralPackageInventorySnapshot {
        /** 是否命中本规则。 */
        private boolean applied;
        /** 是否为当前展示宿主行（链尾或观察期已停无子）。 */
        private boolean displayHost;
        /** 领入拆零量。 */
        private BigDecimal claimedSplitQuantity;
        /** 已吃拆零量。 */
        private BigDecimal consumedSplitQuantity;
        /** 剩余拆零量（≥0）。 */
        private BigDecimal remainSplitQuantity;
        /** 拆零单位展示名。 */
        private String splitUnitDisplay;
        /** 预计下次领盒日；无可空。 */
        private LocalDate nextClaimDate;

        /**
         * @return 是否命中规则
         */
        public boolean isApplied() {
            return applied;
        }

        /**
         * @param applied 命中标志
         */
        public void setApplied(boolean applied) {
            this.applied = applied;
        }

        /**
         * @return 是否展示宿主
         */
        public boolean isDisplayHost() {
            return displayHost;
        }

        /**
         * @param displayHost 展示宿主
         */
        public void setDisplayHost(boolean displayHost) {
            this.displayHost = displayHost;
        }

        /**
         * @return 领入拆零
         */
        public BigDecimal getClaimedSplitQuantity() {
            return claimedSplitQuantity;
        }

        /**
         * @param claimedSplitQuantity 领入拆零
         */
        public void setClaimedSplitQuantity(BigDecimal claimedSplitQuantity) {
            this.claimedSplitQuantity = claimedSplitQuantity;
        }

        /**
         * @return 已吃拆零
         */
        public BigDecimal getConsumedSplitQuantity() {
            return consumedSplitQuantity;
        }

        /**
         * @param consumedSplitQuantity 已吃拆零
         */
        public void setConsumedSplitQuantity(BigDecimal consumedSplitQuantity) {
            this.consumedSplitQuantity = consumedSplitQuantity;
        }

        /**
         * @return 剩余拆零
         */
        public BigDecimal getRemainSplitQuantity() {
            return remainSplitQuantity;
        }

        /**
         * @param remainSplitQuantity 剩余拆零
         */
        public void setRemainSplitQuantity(BigDecimal remainSplitQuantity) {
            this.remainSplitQuantity = remainSplitQuantity;
        }

        /**
         * @return 拆零单位名
         */
        public String getSplitUnitDisplay() {
            return splitUnitDisplay;
        }

        /**
         * @param splitUnitDisplay 拆零单位名
         */
        public void setSplitUnitDisplay(String splitUnitDisplay) {
            this.splitUnitDisplay = splitUnitDisplay;
        }

        /**
         * @return 下次领盒日
         */
        public LocalDate getNextClaimDate() {
            return nextClaimDate;
        }

        /**
         * @param nextClaimDate 下次领盒日
         */
        public void setNextClaimDate(LocalDate nextClaimDate) {
            this.nextClaimDate = nextClaimDate;
        }
    }

    /**
     * 自任意链上节点加载改量链（根→尾），仅同就诊同药、based_on=药嘱表。
     *
     * @param anyNodeId 链上任一药品申请主键
     * @return 根→尾有序列表；找不到则空
     */
    public List<MedicationRequest> loadDoseChangeChain(Long anyNodeId) {
        if (anyNodeId == null) {
            return List.of();
        }
        MedicationRequest start = medicationRequestQueryService.findById(anyNodeId);
        if (start == null) {
            return List.of();
        }
        // 向上走到改量链根（无药嘱 based_on 或断链）
        MedicationRequest root = start;
        Set<Long> seenUp = new HashSet<>();
        while (root.getBasedOnId() != null
            && TableNameConstants.MED_MEDICATION_REQUEST.equals(root.getBasedOnTable())) {
            if (!seenUp.add(root.getId())) {
                // 环：停止上溯，避免死循环
                break;
            }
            MedicationRequest parent = medicationRequestQueryService.findById(root.getBasedOnId());
            if (parent == null) {
                break;
            }
            // 跨就诊或换药：不续算，当前即为根
            if (!Objects.equals(parent.getEncounterId(), start.getEncounterId())
                || !Objects.equals(parent.getMedicationId(), start.getMedicationId())) {
                break;
            }
            root = parent;
        }
        // 向下沿 based_on 收集子嘱，拼成根→尾
        List<MedicationRequest> chain = new ArrayList<>();
        chain.add(root);
        Set<Long> seenDown = new HashSet<>();
        seenDown.add(root.getId());
        MedicationRequest cursor = root;
        while (true) {
            List<MedicationRequest> children = medicationRequestQueryService.listByBasedOnAnchor(
                TableNameConstants.MED_MEDICATION_REQUEST, cursor.getId());
            MedicationRequest next = this.pickSameDrugChild(children, start.getEncounterId(), start.getMedicationId());
            if (next == null) {
                break;
            }
            if (!seenDown.add(next.getId())) {
                break;
            }
            chain.add(next);
            cursor = next;
        }
        return chain;
    }

    /**
     * 计算展示行单位领用剩余（未命中资格则 applied=false）。
     *
     * <p>默认 {@link UnitClaimMode#PACKAGE}（一盒一点）；拆零下限请显式传 {@link UnitClaimMode#MIN_SPLIT}。</p>
     *
     * @param displayRequest                 当前列表行对应药嘱
     * @param proceduresByRequestId          链上各嘱 Procedure（净态判定用）
     * @param product                        药品目录
     * @param methodLabelByCode              用法展示名
     * @param asOf                           业务统计截止日（含当日）；若晚于今天则钳为今天——
     *                                       「已吃」不得把未来尚未出现的执行点算进去
     * @param applyStandingOrderPackageBatch 全局不可拆为 true（仅 PACKAGE 模式使用）
     * @return 快照；永不 null
     */
    public OralPackageInventorySnapshot compute(
        MedicationRequest displayRequest,
        Map<Long, List<ProcedureRecordQueryModel>> proceduresByRequestId,
        ProductDefinitionDisplayDto product,
        Map<String, String> methodLabelByCode,
        LocalDate asOf,
        boolean applyStandingOrderPackageBatch) {
        return this.compute(
            displayRequest,
            proceduresByRequestId,
            product,
            methodLabelByCode,
            asOf,
            UnitClaimMode.PACKAGE,
            applyStandingOrderPackageBatch);
    }

    /**
     * 按领用模式计算展示行剩余（未命中资格则 applied=false）。
     *
     * @param displayRequest                 当前列表行对应药嘱
     * @param proceduresByRequestId          链上各嘱 Procedure（净态判定用）
     * @param product                        药品目录
     * @param methodLabelByCode              用法展示名（PACKAGE 口服判定用）
     * @param asOf                           业务统计截止日（含当日）；晚于今天则钳为今天
     * @param claimMode                      PACKAGE=一盒一点；MIN_SPLIT=拆零下限
     * @param applyStandingOrderPackageBatch 全局不可拆；仅 PACKAGE 模式需要为 true
     * @return 快照；永不 null
     */
    public OralPackageInventorySnapshot compute(
        MedicationRequest displayRequest,
        Map<Long, List<ProcedureRecordQueryModel>> proceduresByRequestId,
        ProductDefinitionDisplayDto product,
        Map<String, String> methodLabelByCode,
        LocalDate asOf,
        UnitClaimMode claimMode,
        boolean applyStandingOrderPackageBatch) {
        OralPackageInventorySnapshot snap = new OralPackageInventorySnapshot();
        snap.setApplied(false);
        snap.setDisplayHost(false);
        if (displayRequest == null || asOf == null || claimMode == null) {
            return snap;
        }
        // 护士站列表截止常拉到未来以展开待执行；已吃/剩余只能算到今天，否则会出现「剩余0但领用点还没出」
        LocalDate today = LocalDate.now();
        LocalDate effectiveAsOf = asOf;
        if (effectiveAsOf.isAfter(today)) {
            effectiveAsOf = today;
        }
        StandingOrderPackageBatchPlanTimeResolver.MeasureInput measure =
            this.toMeasureInput(displayRequest);
        BigDecimal claimPerExecution = this.resolveClaimPerExecutionSplit(
            claimMode,
            applyStandingOrderPackageBatch,
            measure,
            product,
            methodLabelByCode);
        if (claimPerExecution == null) {
            // 未命中本模式资格
            return snap;
        }
        List<MedicationRequest> chain = this.loadDoseChangeChain(displayRequest.getId());
        if (chain.isEmpty()) {
            chain = List.of(displayRequest);
        }
        MedicationRequest tip = chain.get(chain.size() - 1);
        boolean displayHost = Objects.equals(displayRequest.getId(), tip.getId());
        if (!displayHost) {
            // 已被改量指走的旧嘱：不挂剩余展示，返回 applied=false 以免误抑制
            snap.setDisplayHost(false);
            return snap;
        }
        snap.setDisplayHost(true);
        snap.setApplied(true);
        snap.setSplitUnitDisplay(this.resolveSplitUnitDisplay(product));

        Map<Long, List<ProcedureRecordQueryModel>> procedures = proceduresByRequestId;
        if (procedures == null) {
            procedures = Collections.emptyMap();
        }
        List<LocalDate> claimDays = this.collectNetCompletedClaimDays(chain, procedures);
        BigDecimal claimed = claimPerExecution.multiply(BigDecimal.valueOf(claimDays.size()));
        snap.setClaimedSplitQuantity(claimed);

        BigDecimal consumed = BigDecimal.ZERO;
        LocalDate consumeStart = null;
        if (!claimDays.isEmpty()) {
            consumeStart = claimDays.get(0);
            for (LocalDate claimDay : claimDays) {
                if (claimDay.isBefore(consumeStart)) {
                    consumeStart = claimDay;
                }
            }
            consumed = this.sumConsumedSplit(
                chain, product, methodLabelByCode, consumeStart, effectiveAsOf);
        }
        snap.setConsumedSplitQuantity(consumed);
        BigDecimal remain = claimed.subtract(consumed);
        if (remain.compareTo(BigDecimal.ZERO) < 0) {
            // 理论算负：按 0，且应已出现领用点
            remain = BigDecimal.ZERO;
        }
        snap.setRemainSplitQuantity(remain);

        // 从未有效领用：不预估下次领用日（第一单位由开立 pending 负责）
        if (claimDays.isEmpty() || consumeStart == null) {
            snap.setNextClaimDate(null);
            return snap;
        }
        // 下次领用日按「领入总量自首领用日起逐日扣」绝对推算；前瞻右界用钳后 asOf，与已吃口径一致
        snap.setNextClaimDate(
            this.resolveNextClaimDate(
                chain, product, methodLabelByCode, consumeStart, claimed, effectiveAsOf));
        return snap;
    }

    /**
     * 解析本模式每次净完成执行应计入的领入拆零量；未命中资格返回 null。
     *
     * @param claimMode                      领用模式
     * @param applyStandingOrderPackageBatch 全局不可拆（PACKAGE 必填）
     * @param measure                        槽量入参
     * @param product                        目录
     * @param methodLabelByCode              用法名
     * @return 每次领入拆零；未命中 null
     */
    private BigDecimal resolveClaimPerExecutionSplit(
        UnitClaimMode claimMode,
        boolean applyStandingOrderPackageBatch,
        StandingOrderPackageBatchPlanTimeResolver.MeasureInput measure,
        ProductDefinitionDisplayDto product,
        Map<String, String> methodLabelByCode) {
        if (UnitClaimMode.PACKAGE.equals(claimMode)) {
            if (!applyStandingOrderPackageBatch) {
                // 院点可拆：不启用一盒一点剩余
                return null;
            }
            if (!StandingOrderPackageBatchEligibilitySupport.isEligibleWithoutSplittableSwitch(
                measure.getTherapyEnum(),
                measure.getMethodCode(),
                methodLabelByCode,
                product)) {
                return null;
            }
            if (product == null
                || product.getPackageUnitRatio() == null
                || product.getPackageUnitRatio().compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }
            return product.getPackageUnitRatio();
        }
        if (UnitClaimMode.MIN_SPLIT.equals(claimMode)) {
            // 拆零下限：长期 + 常规单次临床拆零严格小于 1；与院点不可拆开关正交
            if (product == null || StringUtils.isEmpty(product.getSplitUnitCode())) {
                return null;
            }
            if (!TherapyType.LONG_TERM.getCode().equals(measure.getTherapyEnum())) {
                return null;
            }
            BigDecimal regularSplit = planTimeResolver.resolveRegularClinicalSplitQuantity(measure, product);
            if (!StandingOrderMinSplitUnitBatchExpandSupport.isBelowOneSplitUnit(regularSplit)) {
                return null;
            }
            return BigDecimal.ONE;
        }
        return null;
    }

    /**
     * 剩余仍够覆盖时是否抑制候选盒首 pending（按天，不按次）。
     *
     * <p>以预计下次领盒日为准：候选日早于该日则抑制。剩余恰为 0（截止日刚好吃完）时仍须抑制「够吃到的末日」上的过早盒首，
     * 不可因剩余≤0 直接放开。</p>
     *
     * @param snap            剩余快照
     * @param pendingPlanDate 候选 pending 所在自然日
     * @return true=抑制不回传
     */
    public boolean shouldSuppressPending(OralPackageInventorySnapshot snap, LocalDate pendingPlanDate) {
        if (snap == null || !snap.isApplied() || pendingPlanDate == null) {
            return false;
        }
        if (snap.getClaimedSplitQuantity() == null
            || snap.getClaimedSplitQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            // 从未领盒：不抑制第一盒
            return false;
        }
        LocalDate nextClaim = snap.getNextClaimDate();
        if (nextClaim == null) {
            // 前瞻窗内仍够用：抑制当前可见的过早盒首
            return true;
        }
        // 下次领盒日晚于本候选日 → 本日仍够，抑制
        return nextClaim.isAfter(pendingPlanDate);
    }

    /**
     * 解析预计下次领盒日当天的第一个频次计划点（BR-IP-EXE-038 pending 锚）。
     *
     * <p>改量/已领盒后，包装切盒仍可能把「下一盒首」锚在新嘱开立日；抑制过早点后会出现空窗。
     * 调用方须在下次领盒日用本方法补回可执行锚点。</p>
     *
     * @param displayRequest 展示宿主药嘱（链尾）
     * @param nextClaimDate  预计下次领盒日
     * @return 当日最早频次点；无频次/开立后无点则 null
     */
    public LocalDateTime resolveNextClaimPendingPlanTime(
        MedicationRequest displayRequest,
        LocalDate nextClaimDate) {
        if (displayRequest == null || nextClaimDate == null) {
            return null;
        }
        if (StringUtils.isEmpty(displayRequest.getRateCode()) || displayRequest.getReqOpenedTime() == null) {
            return null;
        }
        LocalDate openDay = displayRequest.getReqOpenedTime().toLocalDate();
        // 领盒日不得早于本条开立日（改量链尾）
        if (nextClaimDate.isBefore(openDay)) {
            return null;
        }
        // 已停且领盒日落在停嘱日之后：不再出待执行
        if (displayRequest.getStopTime() != null
            && nextClaimDate.isAfter(displayRequest.getStopTime().toLocalDate())) {
            return null;
        }
        LocalDateTime dayStart = nextClaimDate.atStartOfDay();
        LocalDateTime dayEnd = nextClaimDate.atTime(23, 59, 59);
        Set<LocalDateTime> points = planTimeResolver.expandLongTermPlanTimes(
            displayRequest.getRateCode(), dayStart, dayEnd, displayRequest.getReqOpenedTime());
        if (points == null || points.isEmpty()) {
            return null;
        }
        LocalDateTime earliest = null;
        for (LocalDateTime point : points) {
            if (point == null) {
                continue;
            }
            if (!nextClaimDate.equals(point.toLocalDate())) {
                continue;
            }
            // 停嘱当日：计划点不得晚于停嘱时刻
            if (displayRequest.getStopTime() != null
                && point.toLocalDate().equals(displayRequest.getStopTime().toLocalDate())
                && point.isAfter(displayRequest.getStopTime())) {
                continue;
            }
            if (earliest == null || point.isBefore(earliest)) {
                earliest = point;
            }
        }
        return earliest;
    }

    /**
     * 挑选同就诊同药的改量子嘱（多条时取开立最早一条延续链）。
     *
     * @param children     子嘱
     * @param encounterId  就诊
     * @param medicationId 药品
     * @return 子嘱或 null
     */
    private MedicationRequest pickSameDrugChild(
        List<MedicationRequest> children,
        Long encounterId,
        Long medicationId) {
        if (children == null || children.isEmpty()) {
            return null;
        }
        MedicationRequest best = null;
        for (MedicationRequest child : children) {
            if (child == null) {
                continue;
            }
            if (!Objects.equals(child.getEncounterId(), encounterId)) {
                continue;
            }
            if (!Objects.equals(child.getMedicationId(), medicationId)) {
                continue;
            }
            if (best == null) {
                best = child;
                continue;
            }
            LocalDateTime bestOpened = best.getReqOpenedTime();
            LocalDateTime childOpened = child.getReqOpenedTime();
            if (bestOpened == null) {
                best = child;
                continue;
            }
            if (childOpened != null && childOpened.isBefore(bestOpened)) {
                best = child;
            }
        }
        return best;
    }

    /**
     * 收集链上净 COMPLETED 领盒日（取消/不执行不计；按 recorded/occurrence 自然日升序）。
     *
     * @param chain       改量链
     * @param procedures  requestId → Procedure
     * @return 领盒日列表
     */
    private List<LocalDate> collectNetCompletedClaimDays(
        List<MedicationRequest> chain,
        Map<Long, List<ProcedureRecordQueryModel>> procedures) {
        List<LocalDate> days = new ArrayList<>();
        for (MedicationRequest mr : chain) {
            if (mr == null || mr.getId() == null) {
                continue;
            }
            List<ProcedureRecordQueryModel> rows = procedures.get(mr.getId());
            if (rows == null || rows.isEmpty()) {
                continue;
            }
            Set<Long> cancelledTargets = new HashSet<>();
            for (ProcedureRecordQueryModel row : rows) {
                if (row == null) {
                    continue;
                }
                if (EventStatus.CANCEL.getCode().equals(row.getStatusEnum()) && row.getRefundId() != null) {
                    cancelledTargets.add(row.getRefundId());
                }
            }
            for (ProcedureRecordQueryModel row : rows) {
                if (row == null) {
                    continue;
                }
                if (!EventStatus.COMPLETED.getCode().equals(row.getStatusEnum())) {
                    continue;
                }
                if (row.getId() != null && cancelledTargets.contains(row.getId())) {
                    // 已被 CANCEL 对冲：净态未领
                    continue;
                }
                LocalDateTime when = row.getRecordedTime();
                if (when == null) {
                    when = row.getOccurrenceTime();
                }
                if (when == null) {
                    continue;
                }
                days.add(when.toLocalDate());
            }
        }
        Collections.sort(days);
        return days;
    }

    /**
     * 累加 asOf 截止（含）的理论已吃拆零量。
     *
     * @param chain             改量链根→尾
     * @param product           目录
     * @param methodLabelByCode 用法名
     * @param fromInclusive     起日（首领盒日）
     * @param asOfInclusive     止日
     * @return 已吃拆零
     */
    private BigDecimal sumConsumedSplit(
        List<MedicationRequest> chain,
        ProductDefinitionDisplayDto product,
        Map<String, String> methodLabelByCode,
        LocalDate fromInclusive,
        LocalDate asOfInclusive) {
        if (fromInclusive == null || asOfInclusive == null || fromInclusive.isAfter(asOfInclusive)) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = BigDecimal.ZERO;
        LocalDate day = fromInclusive;
        while (!day.isAfter(asOfInclusive)) {
            BigDecimal daily = this.resolveDailyDemandSplit(chain, product, methodLabelByCode, day);
            total = total.add(daily);
            day = day.plusDays(1);
        }
        return total;
    }

    /**
     * 某自然日理论需求拆零（观察期 0；改量当天取 max(旧,新)）。
     *
     * @param chain             链
     * @param product           目录
     * @param methodLabelByCode 用法
     * @param day               自然日
     * @return 日需求拆零
     */
    private BigDecimal resolveDailyDemandSplit(
        List<MedicationRequest> chain,
        ProductDefinitionDisplayDto product,
        Map<String, String> methodLabelByCode,
        LocalDate day) {
        BigDecimal maxDemand = BigDecimal.ZERO;
        boolean anyActive = false;
        for (int i = 0; i < chain.size(); i++) {
            MedicationRequest mr = chain.get(i);
            if (mr == null || mr.getReqOpenedTime() == null) {
                continue;
            }
            LocalDate openDay = mr.getReqOpenedTime().toLocalDate();
            if (day.isBefore(openDay)) {
                continue;
            }
            LocalDate stopDay = null;
            if (mr.getStopTime() != null) {
                stopDay = mr.getStopTime().toLocalDate();
            }
            // 观察期：已停且后继尚未开立（或本段停后到下一开立前）——停嘱日仍按旧量吃，次日起冻结
            if (stopDay != null && day.isAfter(stopDay)) {
                MedicationRequest next = null;
                if (i + 1 < chain.size()) {
                    next = chain.get(i + 1);
                }
                if (next == null || next.getReqOpenedTime() == null) {
                    // 无后继：整段观察冻结
                    continue;
                }
                LocalDate nextOpen = next.getReqOpenedTime().toLocalDate();
                if (day.isBefore(nextOpen)) {
                    // 观察期中间日不扣
                    continue;
                }
            }
            // 责任段：开立日～停嘱日（含）；若已被后继开立日覆盖，仍参与 max（改量当天）
            if (stopDay != null && day.isAfter(stopDay)) {
                continue;
            }
            anyActive = true;
            BigDecimal demand = this.computeOrderDailySplit(mr, product, day);
            if (demand.compareTo(maxDemand) > 0) {
                maxDemand = demand;
            }
        }
        if (!anyActive) {
            return BigDecimal.ZERO;
        }
        return maxDemand;
    }

    /**
     * 单条医嘱在某自然日的理论拆零消耗。
     *
     * @param mr      药嘱
     * @param product 目录
     * @param day     自然日
     * @return 日拆零；无频次点或换算失败为 0
     */
    private BigDecimal computeOrderDailySplit(
        MedicationRequest mr,
        ProductDefinitionDisplayDto product,
        LocalDate day) {
        if (mr == null || mr.getReqOpenedTime() == null || StringUtils.isEmpty(mr.getRateCode())) {
            return BigDecimal.ZERO;
        }
        LocalDateTime dayStart = day.atStartOfDay();
        LocalDateTime dayEnd = day.atTime(23, 59, 59);
        Set<LocalDateTime> points = planTimeResolver.expandLongTermPlanTimes(
            mr.getRateCode(), dayStart, dayEnd, mr.getReqOpenedTime());
        if (points == null || points.isEmpty()) {
            return BigDecimal.ZERO;
        }
        List<LocalDateTime> ordered = new ArrayList<>(points);
        Collections.sort(ordered);
        List<BigDecimal> splits = planTimeResolver.resolveSlotSplitQuantities(
            ordered, mr.getReqOpenedTime(), this.toMeasureInput(mr), product);
        if (splits == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal split : splits) {
            if (split != null) {
                sum = sum.add(split);
            }
        }
        return sum;
    }

    /**
     * 自首领盒日起按链日需求扣减领入总量，找首个「当日需求 &gt; 行走剩余」的自然日 = 预计下次领盒日。
     *
     * <p>语义：上一盒够吃到的最后一日的<strong>次日</strong>才领下一盒（例：8.26 起、日 2 片、盒 14 → 够吃到 9.1，下次领盒 9.2）。
     * 禁止用「asOf 后剩余」再从 asOf 当日重扣——已吃已含 asOf，重扣会把领盒日提前一天。</p>
     *
     * @param chain             改量链
     * @param product           目录
     * @param methodLabelByCode 用法名
     * @param consumeStart      首领盒自然日
     * @param claimed           领入拆零总量
     * @param asOf              业务截止日（前瞻窗右界锚点）
     * @return 下次领盒日；窗内仍够则 null
     */
    private LocalDate resolveNextClaimDate(
        List<MedicationRequest> chain,
        ProductDefinitionDisplayDto product,
        Map<String, String> methodLabelByCode,
        LocalDate consumeStart,
        BigDecimal claimed,
        LocalDate asOf) {
        if (consumeStart == null || claimed == null || asOf == null) {
            return null;
        }
        if (claimed.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal walkingRemain = claimed;
        // 右界：至少覆盖到 asOf，并再向前看 NEXT_CLAIM_LOOKAHEAD_DAYS，兼容「截止日已过后仍未领」
        LocalDate limit = asOf.plusDays(NEXT_CLAIM_LOOKAHEAD_DAYS);
        LocalDate day = consumeStart;
        while (!day.isAfter(limit)) {
            BigDecimal need = this.resolveDailyDemandSplit(chain, product, methodLabelByCode, day);
            if (need.compareTo(walkingRemain) > 0) {
                return day;
            }
            walkingRemain = walkingRemain.subtract(need);
            day = day.plusDays(1);
        }
        return null;
    }

    /**
     * 药嘱 → 槽量入参。
     *
     * @param mr 药嘱
     * @return MeasureInput
     */
    private StandingOrderPackageBatchPlanTimeResolver.MeasureInput toMeasureInput(MedicationRequest mr) {
        StandingOrderPackageBatchPlanTimeResolver.MeasureInput measure =
            new StandingOrderPackageBatchPlanTimeResolver.MeasureInput();
        if (mr == null) {
            return measure;
        }
        measure.setTherapyEnum(mr.getTherapyEnum());
        measure.setQuantity(mr.getQuantity());
        measure.setUnitCode(mr.getUnitCode());
        measure.setFirstDose(mr.getFirstDose());
        measure.setFirstDoseUnitCode(mr.getFirstDoseUnitCode());
        measure.setMethodCode(mr.getMethodCode());
        return measure;
    }

    /**
     * 拆零单位展示名：产品字典列优先，再回落单位槽解析（勿直接回传 code）。
     *
     * @param product 目录
     * @return 展示名；可空
     */
    private String resolveSplitUnitDisplay(ProductDefinitionDisplayDto product) {
        if (product == null) {
            return null;
        }
        String fromDict = StringUtils.trimToNull(product.getSplitUnitCodeDictText());
        if (fromDict != null) {
            return fromDict;
        }
        return ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
            null, product.getSplitUnitCode(), product);
    }

    /**
     * 批量：按展示行 id 预构建「链成员 id → 自身」索引，供列表减少重复上溯。
     *
     * @param displayIds 列表行药嘱 id
     * @return displayId → 链（根→尾）
     */
    public Map<Long, List<MedicationRequest>> preloadChainsByDisplayIds(List<Long> displayIds) {
        Map<Long, List<MedicationRequest>> result = new LinkedHashMap<>();
        if (displayIds == null || displayIds.isEmpty()) {
            return result;
        }
        for (Long id : displayIds) {
            if (id == null) {
                continue;
            }
            if (result.containsKey(id)) {
                continue;
            }
            result.put(id, this.loadDoseChangeChain(id));
        }
        return result;
    }

    /**
     * 收集链上全部 requestId（批量拉 Procedure）。
     *
     * @param chains 多条链
     * @return id 集合
     */
    public Set<Long> collectChainRequestIds(Map<Long, List<MedicationRequest>> chains) {
        Set<Long> ids = new HashSet<>();
        if (chains == null || chains.isEmpty()) {
            return ids;
        }
        for (List<MedicationRequest> chain : chains.values()) {
            if (chain == null) {
                continue;
            }
            for (MedicationRequest mr : chain) {
                if (mr != null && mr.getId() != null) {
                    ids.add(mr.getId());
                }
            }
        }
        return ids;
    }

    /**
     * 空快照工厂。
     *
     * @return 未命中快照
     */
    public OralPackageInventorySnapshot emptySnapshot() {
        OralPackageInventorySnapshot snap = new OralPackageInventorySnapshot();
        snap.setApplied(false);
        snap.setDisplayHost(false);
        return snap;
    }
}
