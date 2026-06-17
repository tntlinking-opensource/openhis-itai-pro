/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.module.querysupport.pharmacy;

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
