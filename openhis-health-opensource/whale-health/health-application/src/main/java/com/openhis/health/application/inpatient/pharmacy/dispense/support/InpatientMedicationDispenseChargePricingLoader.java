/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.dispense.support;

import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseSummaryItemQueryModel;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispensePickupPendingQueryModel;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.MedicationSummarySlipLineQueryModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
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
            line.setUnitPrice(priceGroup.unitPrice);
            line.setTotalPrice(priceGroup.totalPriceSum);
        }
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
     * 按汇总单号构建「药品 id + 单价」价组快照（待结算收费锚定单价与合计）。
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
        Map<Long, List<Long>> dispenseIdsByEncounter = new HashMap<>();
        Map<Long, MedicationDispenseSummaryItemQueryModel> dispenseById = new HashMap<>();
        for (MedicationDispenseSummaryItemQueryModel dispense : dispensess) {
            if (dispense == null || dispense.getId() == null) {
                continue;
            }
            dispenseById.put(dispense.getId(), dispense);
            if (dispense.getEncounterId() != null) {
                dispenseIdsByEncounter
                    .computeIfAbsent(dispense.getEncounterId(), key -> new ArrayList<>())
                    .add(dispense.getId());
            }
        }
        Map<Long, ChargeItem> chargeByDispenseId =
            this.mapPendingSettlementChargeByDispenseIds(dispenseIdsByEncounter);
        for (MedicationDispenseSummaryItemQueryModel dispense : dispenseById.values()) {
            ChargeItem chargeItem = chargeByDispenseId.get(dispense.getId());
            if (chargeItem == null || chargeItem.getUnitPrice() == null) {
                continue;
            }
            String groupKey = dispense.getMedicationId() + "|" + chargeItem.getUnitPrice().toPlainString();
            PriceGroupSnapshot snapshot = groups.computeIfAbsent(groupKey, key -> new PriceGroupSnapshot());
            snapshot.medicationId = dispense.getMedicationId();
            snapshot.unitPrice = chargeItem.getUnitPrice();
            if (chargeItem.getTotalPrice() != null) {
                if (snapshot.totalPriceSum == null) {
                    snapshot.totalPriceSum = chargeItem.getTotalPrice();
                } else {
                    snapshot.totalPriceSum = snapshot.totalPriceSum.add(chargeItem.getTotalPrice());
                }
            }
        }
        return groups;
    }

    /**
     * 为汇总 slip 供应行匹配未分配的价组（同 medicationId，一对一消耗 assignedGroups）。
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
        candidates.sort((left, right) -> {
            if (left.unitPrice == null && right.unitPrice == null) {
                return 0;
            }
            if (left.unitPrice == null) {
                return 1;
            }
            if (right.unitPrice == null) {
                return -1;
            }
            return right.unitPrice.compareTo(left.unitPrice);
        });
        PriceGroupSnapshot picked = candidates.get(0);
        assignedGroups.add(picked);
        return picked;
    }

    /** 汇总详情价格分组快照（itemId + unitPrice → ChargeItem 累加）。 */
    private static final class PriceGroupSnapshot {
        private Long medicationId;
        private BigDecimal unitPrice;
        private BigDecimal totalPriceSum;
    }
}
