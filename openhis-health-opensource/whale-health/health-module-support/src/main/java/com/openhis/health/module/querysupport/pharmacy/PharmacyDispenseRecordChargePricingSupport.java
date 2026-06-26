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
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseSlipQueryModel;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.share.enums.MedicationDispenseType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 发退药记录定价补充：退药行 charge 锚点在原正向发放/原药嘱 service_id，需回退读价。
 */
@Component
@RequiredArgsConstructor
public class PharmacyDispenseRecordChargePricingSupport {

    private final ChargeItemQueryService chargeItemQueryService;

    /**
     * 为尚未命中 dispense 锚点定价的退药行，按原药嘱 {@code service_id} 批量补单价/金额。
     *
     * @param slipRows                    发放事实读模型
     * @param medicationRequestByMedReqId 药嘱索引（含负向与原药嘱）
     * @param unitPriceOut                dispenseId → 单价（可写）
     * @param totalPriceOut               dispenseId → 金额（可写）
     */
    public void supplementRefundPricingFromOriginMedicationRequests(
        List<MedicationDispenseSlipQueryModel> slipRows,
        Map<Long, MedicationRequestListQueryModel> medicationRequestByMedReqId,
        Map<Long, BigDecimal> unitPriceOut,
        Map<Long, BigDecimal> totalPriceOut) {
        if (slipRows == null || slipRows.isEmpty()) {
            return;
        }
        Map<Long, Long> refundDispenseIdToOriginRequestId = new HashMap<>();
        LinkedHashSet<Long> originRequestIds = new LinkedHashSet<>();
        for (MedicationDispenseSlipQueryModel slip : slipRows) {
            if (slip == null || slip.getDispenseId() == null || !this.isRefundSlipRow(slip)) {
                continue;
            }
            if (totalPriceOut.containsKey(slip.getDispenseId())) {
                continue;
            }
            Long originRequestId = this.resolveOriginMedicationRequestId(slip, medicationRequestByMedReqId);
            if (originRequestId == null) {
                continue;
            }
            originRequestIds.add(originRequestId);
            refundDispenseIdToOriginRequestId.put(slip.getDispenseId(), originRequestId);
        }
        if (originRequestIds.isEmpty()) {
            return;
        }
        ChargeItemQueryCommand chargeQuery = new ChargeItemQueryCommand();
        chargeQuery.setServiceIds(originRequestIds);
        List<ChargeItem> chargeRows = this.chargeItemQueryService.listChargeItemsByServiceIds(chargeQuery);
        Map<Long, BigDecimal> totalByOriginRequestId = new HashMap<>();
        Map<Long, BigDecimal> qtyByOriginRequestId = new HashMap<>();
        for (ChargeItem chargeRow : chargeRows) {
            if (chargeRow == null || chargeRow.getServiceId() == null) {
                continue;
            }
            if (!TableNameConstants.MED_MEDICATION_REQUEST.equals(chargeRow.getServiceTable())) {
                continue;
            }
            if (!this.isMedicationDispensePricingChargeStatus(chargeRow.getStatusEnum())) {
                continue;
            }
            Long serviceKey = chargeRow.getServiceId();
            if (chargeRow.getTotalPrice() != null) {
                BigDecimal existed = totalByOriginRequestId.get(serviceKey);
                if (existed == null) {
                    totalByOriginRequestId.put(serviceKey, chargeRow.getTotalPrice());
                } else {
                    totalByOriginRequestId.put(serviceKey, existed.add(chargeRow.getTotalPrice()));
                }
            }
            if (chargeRow.getQuantityValue() != null) {
                BigDecimal existedQty = qtyByOriginRequestId.get(serviceKey);
                if (existedQty == null) {
                    qtyByOriginRequestId.put(serviceKey, chargeRow.getQuantityValue());
                } else {
                    qtyByOriginRequestId.put(serviceKey, existedQty.add(chargeRow.getQuantityValue()));
                }
            }
        }
        for (Map.Entry<Long, Long> entry : refundDispenseIdToOriginRequestId.entrySet()) {
            Long dispenseId = entry.getKey();
            Long originRequestId = entry.getValue();
            BigDecimal totalVal = totalByOriginRequestId.get(originRequestId);
            if (totalVal == null) {
                continue;
            }
            totalPriceOut.put(dispenseId, totalVal);
            BigDecimal qtyVal = qtyByOriginRequestId.get(originRequestId);
            if (qtyVal != null && qtyVal.compareTo(BigDecimal.ZERO) > 0) {
                unitPriceOut.put(dispenseId, totalVal.divide(qtyVal, 4, RoundingMode.HALF_UP));
            }
        }
    }

    private Long resolveOriginMedicationRequestId(
        MedicationDispenseSlipQueryModel slip,
        Map<Long, MedicationRequestListQueryModel> medicationRequestByMedReqId) {
        if (slip.getMedReqId() == null || medicationRequestByMedReqId == null) {
            return null;
        }
        MedicationRequestListQueryModel refundReq = medicationRequestByMedReqId.get(slip.getMedReqId());
        if (refundReq != null && refundReq.getRefundMedicineId() != null) {
            return refundReq.getRefundMedicineId();
        }
        return slip.getMedReqId();
    }

    private boolean isRefundSlipRow(MedicationDispenseSlipQueryModel slip) {
        if (slip.getDispenseEnum() == MedicationDispenseType.REFUND) {
            return true;
        }
        return slip.getStatusEnum() == DispenseStatus.REFUNDED;
    }

    private boolean isMedicationDispensePricingChargeStatus(String statusEnum) {
        if (statusEnum == null) {
            return false;
        }
        if (ChargeItemStatus.DRAFT.getCode().equals(statusEnum)) {
            return true;
        }
        if (ChargeItemStatus.PLANNED.getCode().equals(statusEnum)) {
            return true;
        }
        if (ChargeItemStatus.SETTLED.getCode().equals(statusEnum)) {
            return true;
        }
        if (ChargeItemStatus.REFUNDING.getCode().equals(statusEnum)) {
            return true;
        }
        if (ChargeItemStatus.REFUNDED.getCode().equals(statusEnum)) {
            return true;
        }
        if (ChargeItemStatus.PENDING_SETTLEMENT.getCode().equals(statusEnum)) {
            return true;
        }
        return false;
    }
}
