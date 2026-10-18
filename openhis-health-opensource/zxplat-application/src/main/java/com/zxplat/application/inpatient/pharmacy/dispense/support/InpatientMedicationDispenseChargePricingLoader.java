/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.pharmacy.dispense.support;

import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.zxplat.domain.medication.medicationdispense.queries.model.MedicationDispenseSummaryItemQueryModel;
import com.zxplat.domain.medication.medicationdispense.queries.model.MedicationDispensePickupPendingQueryModel;
import com.zxplat.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.zxplat.domain.share.enums.ChargeItemStatus;
import com.zxplat.domain.workflow.supplyrequest.queries.model.MedicationSummarySlipLineQueryModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院药品发放 ↔ 待结算 ChargeItem 只读定价协作（B.6.1.3）。
 *
 * <p>跨 {@code MedicationDispense} 与 {@code ChargeItem} 聚合的读编排收口于此；领域 QueryService 禁止互调。</p>
 */
@Component
@RequiredArgsConstructor
public class InpatientMedicationDispenseChargePricingLoader {

    private final ChargeItemQueryService chargeItemQueryService;
    private final MedicationDispenseQueryService medicationDispenseQueryService;

    /**
     * 待汇总明细分页行回填 {@code chargeUnitPrice}（仅 {@code PENDING_SETTLEMENT}）。
     *
     * @param rows 发放域分页结果；{@code null} 时无操作
     */
    public void applyPendingSettlementUnitPricesToPickupPendingRows(
        List<MedicationDispensePickupPendingQueryModel> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Map<Long, BigDecimal> unitPriceByDispenseId = this.mapPendingSettlementUnitPriceByDispenseId(rows);
        for (MedicationDispensePickupPendingQueryModel row : rows) {
            if (row == null || row.getDispenseId() == null) {
                continue;
            }
            row.setChargeUnitPrice(unitPriceByDispenseId.get(row.getDispenseId()));
        }
    }

    /**
     * 按就诊 + 发放锚点批量加载待结算账单单价。
     *
     * @param rows 须含 {@code encounterId} 与 {@code dispenseId}
     * @return dispenseId → unitPrice
     */
    public Map<Long, BigDecimal> mapPendingSettlementUnitPriceByDispenseId(
        List<MedicationDispensePickupPendingQueryModel> rows) {
        Map<Long, BigDecimal> result = new HashMap<>();
        Map<Long, List<Long>> dispenseIdsByEncounter = this.groupDispenseIdsByEncounterFromPickupRows(rows);
        Map<Long, ChargeItem> chargeByDispenseId =
            this.mapPendingSettlementChargeByDispenseIds(dispenseIdsByEncounter);
        for (Map.Entry<Long, ChargeItem> entry : chargeByDispenseId.entrySet()) {
            ChargeItem charge = entry.getValue();
            if (charge != null && charge.getUnitPrice() != null) {
                result.put(entry.getKey(), charge.getUnitPrice());
            }
        }
        return result;
    }

    /**
     * 汇总保存/发药 slip：按发放锚点批量加载待结算账单（仅 {@code PENDING_SETTLEMENT}）。
     *
     * @param dispenseIdsByEncounter encounterId → dispenseId 列表
     * @return dispenseId → ChargeItem
     */
    public Map<Long, ChargeItem> mapPendingSettlementChargeByDispenseIds(
        Map<Long, List<Long>> dispenseIdsByEncounter) {
        Map<Long, ChargeItem> chargeByDispenseId = new LinkedHashMap<>();
        if (dispenseIdsByEncounter == null || dispenseIdsByEncounter.isEmpty()) {
            return chargeByDispenseId;
        }
        for (Map.Entry<Long, List<Long>> entry : dispenseIdsByEncounter.entrySet()) {
            Long encounterId = entry.getKey();
            List<Long> dispenseIds = entry.getValue();
            if (encounterId == null || dispenseIds == null || dispenseIds.isEmpty()) {
                continue;
            }
            List<ChargeItem> charges = chargeItemQueryService.listPendingSettlementChargeItemsForMedicationDispensePricing(
                encounterId, dispenseIds);
            for (ChargeItem charge : charges) {
                if (charge == null || charge.getDispenseId() == null) {
                    continue;
                }
                chargeByDispenseId.put(charge.getDispenseId(), charge);
            }
        }
        return chargeByDispenseId;
    }

    /**
     * 发药 slip：按 dispenseId 批量回填单价/总价 Map。
     *
     * @param encounterId  就诊标识
     * @param dispenseIds  发放行主键
     * @param unitPriceOut   输出：dispenseId → unitPrice
     * @param totalPriceOut  输出：dispenseId → totalPrice
     */
    public void mergePendingSettlementPricingMaps(
        Long encounterId,
        List<Long> dispenseIds,
        Map<Long, BigDecimal> unitPriceOut,
        Map<Long, BigDecimal> totalPriceOut) {
        if (encounterId == null || dispenseIds == null || dispenseIds.isEmpty()) {
            return;
        }
        List<ChargeItem> charges = chargeItemQueryService.listPendingSettlementChargeItemsForMedicationDispensePricing(
            encounterId, dispenseIds);
        for (ChargeItem charge : charges) {
            if (charge == null || charge.getDispenseId() == null) {
                continue;
            }
            if (unitPriceOut != null && charge.getUnitPrice() != null) {
                unitPriceOut.put(charge.getDispenseId(), charge.getUnitPrice());
            }
            if (totalPriceOut != null && charge.getTotalPrice() != null) {
                totalPriceOut.put(charge.getDispenseId(), charge.getTotalPrice());
            }
        }
    }

    /**
     * 汇总单合并明细：只读累加 ChargeItem 单价/总价（BR-IP-SUM-009）。
     *
     * <p>{@code totalPrice} 仍为 ChargeItem 总价之和；{@code unitPrice} 按供应行拆零数量回推
     * （总价÷拆零数量），与数量同阶，避免包装价 3.90 与拆零价 0.39 同药混显。</p>
     *
     * @param summaryNo 汇总单号
     * @param lines     供应域合并行；原地写入 {@code unitPrice}/{@code totalPrice}
     */
    public void enrichSummarySlipLineChargePricing(String summaryNo, List<MedicationSummarySlipLineQueryModel> lines) {
        if (summaryNo == null || summaryNo.isBlank() || lines == null || lines.isEmpty()) {
            return;
        }
        Map<String, PriceGroupSnapshot> priceGroupByKey =
            this.buildSummaryPriceGroupsByBusNo(summaryNo.trim());
        Set<PriceGroupSnapshot> assignedGroups = new HashSet<>();
        for (MedicationSummarySlipLineQueryModel line : lines) {
            if (line == null) {
                continue;
            }
            PriceGroupSnapshot priceGroup = this.matchPriceGroupForSupplyLine(line, priceGroupByKey, assignedGroups);
            if (priceGroup == null) {
                continue;
            }
            line.setTotalPrice(priceGroup.totalPriceSum);
            // 展示单价与拆零数量同阶：总价÷数量（禁止再拿包装档 ChargeItem.unitPrice 直出）
            line.setUnitPrice(this.resolveSplitUnitDisplayPrice(
                priceGroup.totalPriceSum, line.getItemQuantity(), priceGroup.unitPrice));
        }
    }

    /**
     * 汇总行展示单价：优先总价÷拆零数量；无法计算时回落账单单价。
     *
     * @param totalPriceSum 价组总价
     * @param itemQuantity  供应行拆零数量
     * @param fallbackUnit  账单原始单价
     * @return 与拆零数量同阶的单价
     */
    private BigDecimal resolveSplitUnitDisplayPrice(
        BigDecimal totalPriceSum,
        BigDecimal itemQuantity,
        BigDecimal fallbackUnit) {
        if (totalPriceSum != null
            && itemQuantity != null
            && itemQuantity.compareTo(BigDecimal.ZERO) > 0) {
            return totalPriceSum.divide(itemQuantity, 6, RoundingMode.HALF_UP);
        }
        return fallbackUnit;
    }

    /**
     * 待发药拣货行按 encounterId 分组 dispenseId 列表。
     *
     * @param rows 拣货待处理读模型
     * @return encounter → dispenseId 列表
     */
    private Map<Long, List<Long>> groupDispenseIdsByEncounterFromPickupRows(
        List<MedicationDispensePickupPendingQueryModel> rows) {
        Map<Long, List<Long>> dispenseIdsByEncounter = new HashMap<>();
        if (rows == null) {
            return dispenseIdsByEncounter;
        }
        for (MedicationDispensePickupPendingQueryModel row : rows) {
            if (row == null || row.getEncounterId() == null || row.getDispenseId() == null) {
                continue;
            }
            dispenseIdsByEncounter
                .computeIfAbsent(row.getEncounterId(), key -> new ArrayList<>())
                .add(row.getDispenseId());
        }
        return dispenseIdsByEncounter;
    }

    /**
     * 按汇总单号构建「药品 id + 单价」价组快照（收费锚定单价与合计，BR-IP-SUM-009）。
     *
     * <p>读汇总单/打印须覆盖发放后仍有效的账单态：草稿、待收费、待结算、已结算。
     * 仅扫 {@code PENDING_SETTLEMENT} 时，部分发放已日结/出院结算后会出现
     * 「Supply 数量仍为 N、总价只剩 1 笔单价」的漏累加。</p>
     *
     * @param busNo 药品汇总单号
     * @return 价组 key → 快照（medicationId、unitPrice、totalPriceSum）
     */
    private Map<String, PriceGroupSnapshot> buildSummaryPriceGroupsByBusNo(String busNo) {
        Map<String, PriceGroupSnapshot> groups = new LinkedHashMap<>();
        List<MedicationDispenseSummaryItemQueryModel> dispensess =
            medicationDispenseQueryService.listMedicationDispensesBySummaryNo(busNo);
        if (dispensess == null || dispensess.isEmpty()) {
            return groups;
        }
        Map<Long, MedicationDispenseSummaryItemQueryModel> dispenseById = new HashMap<>();
        List<Long> allDispenseIds = new ArrayList<>();
        for (MedicationDispenseSummaryItemQueryModel dispense : dispensess) {
            if (dispense == null || dispense.getId() == null) {
                continue;
            }
            dispenseById.put(dispense.getId(), dispense);
            allDispenseIds.add(dispense.getId());
        }
        // 汇总单历史读：含待结算+已结算等；退费中/已退费不计入领药金额
        Map<Long, ChargePriceBucket> chargeByDispenseId =
            this.mapSummarySlipChargeBucketsByDispenseIds(allDispenseIds);
        for (MedicationDispenseSummaryItemQueryModel dispense : dispenseById.values()) {
            ChargePriceBucket bucket = chargeByDispenseId.get(dispense.getId());
            if (bucket == null || bucket.unitPrice == null) {
                continue;
            }
            String groupKey = this.buildPriceGroupKey(dispense.getMedicationId(), bucket.unitPrice);
            PriceGroupSnapshot snapshot = groups.computeIfAbsent(groupKey, key -> new PriceGroupSnapshot());
            snapshot.medicationId = dispense.getMedicationId();
            snapshot.unitPrice = bucket.unitPrice;
            if (bucket.totalPriceSum != null) {
                if (snapshot.totalPriceSum == null) {
                    snapshot.totalPriceSum = bucket.totalPriceSum;
                } else {
                    snapshot.totalPriceSum = snapshot.totalPriceSum.add(bucket.totalPriceSum);
                }
            }
        }
        return groups;
    }

    /**
     * 价组键：medicationId + 规范化单价（与汇总写合并键 stripTrailingZeros 口径一致）。
     *
     * @param medicationId 药品定义主键
     * @param unitPrice    账单单价
     * @return 价组 key
     */
    private String buildPriceGroupKey(Long medicationId, BigDecimal unitPrice) {
        String pricePart = "";
        if (unitPrice != null) {
            pricePart = unitPrice.stripTrailingZeros().toPlainString();
        }
        return String.valueOf(medicationId) + "|" + pricePart;
    }

    /**
     * 汇总单读价：按发放锚点加载可计入领药金额的 ChargeItem，并按 dispenseId 合并总价。
     *
     * @param dispenseIds 汇总单下发放主键
     * @return dispenseId → 单价/总价桶
     */
    private Map<Long, ChargePriceBucket> mapSummarySlipChargeBucketsByDispenseIds(List<Long> dispenseIds) {
        Map<Long, ChargePriceBucket> bucketByDispenseId = new LinkedHashMap<>();
        if (dispenseIds == null || dispenseIds.isEmpty()) {
            return bucketByDispenseId;
        }
        List<ChargeItem> charges =
            chargeItemQueryService.listChargeItemsForMedicationDispensePricingByDispenseIds(
                dispenseIds, true);
        if (charges == null || charges.isEmpty()) {
            return bucketByDispenseId;
        }
        for (ChargeItem charge : charges) {
            if (charge == null || charge.getDispenseId() == null) {
                continue;
            }
            if (!this.isCountableSummarySlipChargeStatus(charge.getStatusEnum())) {
                continue;
            }
            ChargePriceBucket bucket = bucketByDispenseId.computeIfAbsent(
                charge.getDispenseId(), key -> new ChargePriceBucket());
            if (bucket.unitPrice == null && charge.getUnitPrice() != null) {
                bucket.unitPrice = charge.getUnitPrice();
            }
            if (charge.getTotalPrice() != null) {
                if (bucket.totalPriceSum == null) {
                    bucket.totalPriceSum = charge.getTotalPrice();
                } else {
                    bucket.totalPriceSum = bucket.totalPriceSum.add(charge.getTotalPrice());
                }
            }
        }
        return bucketByDispenseId;
    }

    /**
     * 汇总领药单是否计入该收费状态（排除退费中/已退费，避免冲减领药展示金额）。
     *
     * @param statusEnum ChargeItem.status_enum
     * @return true=计入单价/总价累加
     */
    private boolean isCountableSummarySlipChargeStatus(String statusEnum) {
        if (statusEnum == null || statusEnum.isBlank()) {
            return false;
        }
        String code = statusEnum.trim();
        if (ChargeItemStatus.REFUNDING.getCode().equals(code)
            || ChargeItemStatus.REFUNDED.getCode().equals(code)) {
            return false;
        }
        return ChargeItemStatus.DRAFT.getCode().equals(code)
            || ChargeItemStatus.PLANNED.getCode().equals(code)
            || ChargeItemStatus.PENDING_SETTLEMENT.getCode().equals(code)
            || ChargeItemStatus.SETTLED.getCode().equals(code);
    }

    /**
     * 为汇总 slip 供应行匹配未分配的价组（一对一消耗 assignedGroups）。
     *
     * <p>优先按 Supply 落库单价（itemId+unitPrice）精确命中；存量无单价时按
     * 「总价/单价 ≈ itemQuantity」数量亲和，禁止再按单价高低抢配（会导致同药 7 支/10 支金额对调）。</p>
     *
     * @param line             汇总 slip 行
     * @param priceGroupByKey  价组索引
     * @param assignedGroups   已分配价组集合（mutate）
     * @return 命中价组；无候选或未分配则 null
     */
    private PriceGroupSnapshot matchPriceGroupForSupplyLine(
        MedicationSummarySlipLineQueryModel line,
        Map<String, PriceGroupSnapshot> priceGroupByKey,
        Set<PriceGroupSnapshot> assignedGroups) {
        if (line == null || line.getItemId() == null || priceGroupByKey.isEmpty()) {
            return null;
        }
        // 1) 精确：Supply.price 与价组单价一致
        if (line.getUnitPrice() != null) {
            String exactKey = this.buildPriceGroupKey(line.getItemId(), line.getUnitPrice());
            PriceGroupSnapshot exact = priceGroupByKey.get(exactKey);
            if (exact != null && !assignedGroups.contains(exact)) {
                assignedGroups.add(exact);
                return exact;
            }
        }
        List<PriceGroupSnapshot> candidates = new ArrayList<>();
        for (PriceGroupSnapshot group : priceGroupByKey.values()) {
            if (group.medicationId != null && group.medicationId.equals(line.getItemId())
                && !assignedGroups.contains(group)) {
                candidates.add(group);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() == 1) {
            assignedGroups.add(candidates.get(0));
            return candidates.get(0);
        }
        // 2) 存量无落库单价：按隐含数量（总价÷单价）最接近 Supply 拆零数量
        PriceGroupSnapshot picked = this.pickClosestQuantityPriceGroup(line.getItemQuantity(), candidates);
        if (picked == null) {
            picked = candidates.get(0);
        }
        assignedGroups.add(picked);
        return picked;
    }

    /**
     * 在同药多价组中选隐含数量最接近目标拆零量的一组。
     *
     * @param itemQuantity 供应行拆零数量
     * @param candidates   未分配候选价组（同 medicationId）
     * @return 最近价组；无法计算时 null
     */
    private PriceGroupSnapshot pickClosestQuantityPriceGroup(
        BigDecimal itemQuantity,
        List<PriceGroupSnapshot> candidates) {
        if (itemQuantity == null || candidates == null || candidates.isEmpty()) {
            return null;
        }
        PriceGroupSnapshot best = null;
        BigDecimal bestDistance = null;
        for (PriceGroupSnapshot group : candidates) {
            BigDecimal impliedQty = this.impliedChargeQuantity(group);
            if (impliedQty == null) {
                continue;
            }
            BigDecimal distance = impliedQty.subtract(itemQuantity).abs();
            if (best == null || distance.compareTo(bestDistance) < 0) {
                best = group;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * 价组隐含数量 ≈ totalPriceSum ÷ unitPrice（用于存量错配纠偏）。
     *
     * @param group 价组快照
     * @return 隐含数量；无法计算时 null
     */
    private BigDecimal impliedChargeQuantity(PriceGroupSnapshot group) {
        if (group == null || group.unitPrice == null || group.totalPriceSum == null) {
            return null;
        }
        if (group.unitPrice.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return group.totalPriceSum.divide(group.unitPrice, 6, RoundingMode.HALF_UP);
    }

    /** 汇总详情价格分组快照（itemId + unitPrice → ChargeItem 累加）。 */
    private static final class PriceGroupSnapshot {
        private Long medicationId;
        private BigDecimal unitPrice;
        private BigDecimal totalPriceSum;
    }

    /** 单发放锚点下可计入汇总单的收费单价/总价（同 dispense 多 ChargeItem 时总价相加）。 */
    private static final class ChargePriceBucket {
        private BigDecimal unitPrice;
        private BigDecimal totalPriceSum;
    }
}
