/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.support;

import com.openhis.health.application.inpatient.pharmacy.dispense.support.InpatientMedicationDispenseChargePricingLoader;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.medication.medicationdispense.queries.model.InpatientPatientMedicationUsageDispenseRow;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 住院患者药品使用账单价批量装载（AS-IP-PHA-005 · IF-IP-PMU-03）。
 *
 * <p>先读发放锚点 ChargeItem，再合并住院待结算价；缺价可空不阻断（BR-IP-PMU-012）。
 * 历史落库金额，禁止用当前目录价覆盖（BR-PRICE-SRC-001）。</p>
 */
@Component
@RequiredArgsConstructor
public class InpatientPatientMedicationUsageChargePricingSupport {

    /** 发放锚点账单读。 */
    private final ChargeItemQueryService chargeItemQueryService;

    /** 住院待结算价合并。 */
    private final InpatientMedicationDispenseChargePricingLoader inpatientMedicationDispenseChargePricingLoader;

    /**
     * 按发放行批量装载账单单价与行合计。
     *
     * @param encounterId 就诊主键
     * @param rows        使用发放行；空则返回空 Map
     * @return dispenseId → {@link ChargePricingView}；无账单条目不出现于 Map
     */
    public Map<Long, ChargePricingView> loadPricingByDispenseIds(
        Long encounterId,
        List<InpatientPatientMedicationUsageDispenseRow> rows) {
        Map<Long, ChargePricingView> result = new HashMap<>();
        // 缺就诊或发放行：无需查价
        if (encounterId == null || rows == null || rows.isEmpty()) {
            return result;
        }
        List<Long> dispenseIds = new ArrayList<>();
        // 收集发放主键，供 IN 下推
        for (InpatientPatientMedicationUsageDispenseRow row : rows) {
            if (row == null || row.getDispenseId() == null) {
                continue;
            }
            dispenseIds.add(row.getDispenseId());
        }
        // 无有效发放主键：无需查询账单
        if (dispenseIds.isEmpty()) {
            return result;
        }
        Map<Long, BigDecimal> unitPriceByDispenseId = new HashMap<>();
        Map<Long, BigDecimal> totalPriceByDispenseId = new HashMap<>();
        // 发放锚点账单事实（历史落库 ChargeItem）
        List<ChargeItem> chargeRows =
            this.chargeItemQueryService.listChargeItemsForMedicationDispensePricing(encounterId, dispenseIds);
        this.mergeChargePricingFromChargeItems(chargeRows, unitPriceByDispenseId, totalPriceByDispenseId);
        // 补充住院待结算价（与发退药记录同族；不覆盖已有落库键以外的缺价）
        this.inpatientMedicationDispenseChargePricingLoader.mergePendingSettlementPricingMaps(
            encounterId, dispenseIds, unitPriceByDispenseId, totalPriceByDispenseId);
        // 组装 dispenseId → ChargePricingView
        for (Long dispenseId : dispenseIds) {
            BigDecimal unitPrice = unitPriceByDispenseId.get(dispenseId);
            BigDecimal totalPrice = totalPriceByDispenseId.get(dispenseId);
            // 双空则跳过：缺价不造零、不阻断（BR-IP-PMU-012）
            if (unitPrice == null && totalPrice == null) {
                continue;
            }
            ChargePricingView view = new ChargePricingView();
            view.setUnitPrice(unitPrice);
            view.setTotalPrice(totalPrice);
            result.put(dispenseId, view);
        }
        return result;
    }

    /**
     * 将 charge 行聚合为发放维度单价与金额（同 dispense 多行累加后反算单价）。
     *
     * @param chargeRows            charge 明细
     * @param unitPriceOut          单价输出
     * @param totalPriceOut         金额输出
     */
    private void mergeChargePricingFromChargeItems(
        List<ChargeItem> chargeRows,
        Map<Long, BigDecimal> unitPriceOut,
        Map<Long, BigDecimal> totalPriceOut) {
        if (chargeRows == null || chargeRows.isEmpty()) {
            return;
        }
        Map<Long, BigDecimal> totalByDispense = new HashMap<>();
        Map<Long, BigDecimal> qtyByDispense = new HashMap<>();
        // 同 dispense 可能多行 charge：先累加合计与数量
        for (ChargeItem chargeRow : chargeRows) {
            if (chargeRow == null || chargeRow.getDispenseId() == null) {
                continue;
            }
            Long dispenseKey = chargeRow.getDispenseId();
            // 累加行合计
            if (chargeRow.getTotalPrice() != null) {
                BigDecimal existingTotal = totalByDispense.get(dispenseKey);
                if (existingTotal == null) {
                    totalByDispense.put(dispenseKey, chargeRow.getTotalPrice());
                } else {
                    totalByDispense.put(dispenseKey, existingTotal.add(chargeRow.getTotalPrice()));
                }
            }
            // 累加数量，供反算单价
            if (chargeRow.getQuantityValue() != null) {
                BigDecimal existingQty = qtyByDispense.get(dispenseKey);
                if (existingQty == null) {
                    qtyByDispense.put(dispenseKey, chargeRow.getQuantityValue());
                } else {
                    qtyByDispense.put(dispenseKey, existingQty.add(chargeRow.getQuantityValue()));
                }
            }
        }
        // 写出金额；数量大于 0 时反算单价
        for (Map.Entry<Long, BigDecimal> entry : totalByDispense.entrySet()) {
            Long dispenseKey = entry.getKey();
            BigDecimal totalVal = entry.getValue();
            totalPriceOut.put(dispenseKey, totalVal);
            BigDecimal qtyVal = qtyByDispense.get(dispenseKey);
            if (qtyVal != null && qtyVal.compareTo(BigDecimal.ZERO) > 0) {
                unitPriceOut.put(dispenseKey, MonetaryAmountSupport.unitPriceFromLineTotal(totalVal, qtyVal));
            }
        }
    }
}
