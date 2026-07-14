/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.dispenserecord.support;

import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseSlipQueryModel;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.share.enums.MedicationDispenseType;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 发退药记录定价补充：退药行优先按原正向发放 {@code dispense_id} 锚点读价，与正向发药行同源。
 */
@Component
@RequiredArgsConstructor
public class PharmacyDispenseRecordChargePricingAssembler {

    private final ChargeItemQueryService chargeItemQueryService;
    private final MedicationDispenseQueryService medicationDispenseQueryService;

    /**
     * 为尚未命中 dispense 锚点定价的退药行补单价/金额。
     *
     * <p>优先级：① 同页正向发放行已算价 → ② 原正向发放单 charge（dispense_id） →
     * ③ 原药嘱 service 下单条 charge 的 {@code unit_price}（禁止累加整单金额）。</p>
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
        Map<String, MedicationDispenseSlipQueryModel> forwardSlipByPresentationKey = new HashMap<>();
        List<RefundPricingTarget> pendingRefunds = new ArrayList<>();
        LinkedHashSet<Long> originRequestIds = new LinkedHashSet<>();
        for (MedicationDispenseSlipQueryModel slip : slipRows) {
            if (slip == null) {
                continue;
            }
            if (!this.isRefundSlipRow(slip)) {
                forwardSlipByPresentationKey.put(this.buildPresentationMatchKey(slip), slip);
                continue;
            }
            if (slip.getDispenseId() == null || totalPriceOut.containsKey(slip.getDispenseId())) {
                continue;
            }
            Long originRequestId = this.resolveOriginMedicationRequestId(slip, medicationRequestByMedReqId);
            if (originRequestId == null) {
                continue;
            }
            originRequestIds.add(originRequestId);
            pendingRefunds.add(new RefundPricingTarget(slip, originRequestId));
        }
        if (pendingRefunds.isEmpty()) {
            return;
        }
        List<RefundPricingTarget> unresolvedRefunds = new ArrayList<>();
        for (RefundPricingTarget target : pendingRefunds) {
            MedicationDispenseSlipQueryModel forwardSlip =
                forwardSlipByPresentationKey.get(this.buildPresentationMatchKey(target.slip()));
            if (forwardSlip != null
                && this.applyRefundPricingFromForwardDispenseId(
                    target.slip(),
                    forwardSlip.getDispenseId(),
                    forwardSlip,
                    unitPriceOut,
                    totalPriceOut)) {
                continue;
            }
            unresolvedRefunds.add(target);
        }
        if (unresolvedRefunds.isEmpty()) {
            return;
        }
        Map<Long, List<MedicationDispense>> forwardDispensesByOriginRequestId =
            this.loadCompletedForwardDispensesByOriginRequestId(originRequestIds);
        Map<Long, List<ChargeItem>> chargeRowsByForwardDispenseId =
            this.loadChargeRowsByForwardDispenseId(forwardDispensesByOriginRequestId);
        List<RefundPricingTarget> serviceFallbackRefunds = new ArrayList<>();
        for (RefundPricingTarget target : unresolvedRefunds) {
            MedicationDispense matchedForward =
                this.matchForwardDispense(target.slip(), forwardDispensesByOriginRequestId.get(target.originRequestId()));
            if (matchedForward != null
                && this.applyRefundPricingFromForwardDispenseId(
                    target.slip(),
                    matchedForward.getId(),
                    null,
                    unitPriceOut,
                    totalPriceOut,
                    chargeRowsByForwardDispenseId.get(matchedForward.getId()))) {
                continue;
            }
            serviceFallbackRefunds.add(target);
        }
        if (serviceFallbackRefunds.isEmpty()) {
            return;
        }
        ChargeItemQueryCommand chargeQuery = new ChargeItemQueryCommand();
        chargeQuery.setServiceIds(originRequestIds);
        chargeQuery.setExcludeSupersededSettledLines(true);
        List<ChargeItem> serviceChargeRows = this.chargeItemQueryService.listChargeItemsByServiceIds(chargeQuery);
        Map<Long, List<ChargeItem>> serviceChargeRowsByOriginRequestId = new HashMap<>();
        for (ChargeItem chargeRow : serviceChargeRows) {
            if (chargeRow == null || chargeRow.getServiceId() == null) {
                continue;
            }
            if (!TableNameConstants.MED_MEDICATION_REQUEST.equals(chargeRow.getServiceTable())) {
                continue;
            }
            if (!this.isMedicationDispensePricingChargeStatus(chargeRow.getStatusEnum())) {
                continue;
            }
            serviceChargeRowsByOriginRequestId
                .computeIfAbsent(chargeRow.getServiceId(), ignored -> new ArrayList<>())
                .add(chargeRow);
        }
        for (RefundPricingTarget target : serviceFallbackRefunds) {
            BigDecimal unitPrice = this.resolveUnitPriceFromServiceCharges(
                serviceChargeRowsByOriginRequestId.get(target.originRequestId()),
                this.resolveSlipQuantity(target.slip()));
            if (unitPrice == null) {
                continue;
            }
            this.writeRefundPricing(target.slip(), unitPrice, unitPriceOut, totalPriceOut);
        }
    }

    /**
     * 从同页正向 slip 或已加载的原正向 dispense charge 回填退药定价。
     *
     * @param refundSlip           退药 slip
     * @param forwardDispenseId    原正向发放 id
     * @param forwardSlip          同页正向 slip；跨页查库时为 null
     * @param unitPriceOut         单价输出
     * @param totalPriceOut        金额输出
     * @return 成功写入为 true
     */
    private boolean applyRefundPricingFromForwardDispenseId(
        MedicationDispenseSlipQueryModel refundSlip,
        Long forwardDispenseId,
        MedicationDispenseSlipQueryModel forwardSlip,
        Map<Long, BigDecimal> unitPriceOut,
        Map<Long, BigDecimal> totalPriceOut) {
        return this.applyRefundPricingFromForwardDispenseId(
            refundSlip,
            forwardDispenseId,
            forwardSlip,
            unitPriceOut,
            totalPriceOut,
            null);
    }

    /**
     * 从正向 dispense 定价映射或 charge 行回填退药定价。
     *
     * @param refundSlip           退药 slip
     * @param forwardDispenseId    原正向发放 id
     * @param forwardSlip          同页正向 slip；跨页查库时为 null
     * @param unitPriceOut         单价输出
     * @param totalPriceOut        金额输出
     * @param forwardChargeRows    原正向 dispense charge；可为 null
     * @return 成功写入为 true
     */
    private boolean applyRefundPricingFromForwardDispenseId(
        MedicationDispenseSlipQueryModel refundSlip,
        Long forwardDispenseId,
        MedicationDispenseSlipQueryModel forwardSlip,
        Map<Long, BigDecimal> unitPriceOut,
        Map<Long, BigDecimal> totalPriceOut,
        List<ChargeItem> forwardChargeRows) {
        if (refundSlip == null || refundSlip.getDispenseId() == null || forwardDispenseId == null) {
            return false;
        }
        BigDecimal unitPrice = null;
        if (unitPriceOut != null) {
            unitPrice = unitPriceOut.get(forwardDispenseId);
        }
        if (unitPrice == null && forwardSlip != null) {
            BigDecimal forwardTotal = totalPriceOut == null ? null : totalPriceOut.get(forwardDispenseId);
            BigDecimal forwardQuantity = this.resolveSlipQuantity(forwardSlip);
            if (forwardTotal != null && forwardQuantity != null && forwardQuantity.compareTo(BigDecimal.ZERO) > 0) {
                unitPrice = MonetaryAmountSupport.unitPriceFromLineTotal(forwardTotal, forwardQuantity);
            }
        }
        if (unitPrice == null) {
            unitPrice = this.resolveUnitPriceFromDispenseCharges(forwardChargeRows, forwardDispenseId);
        }
        if (unitPrice == null) {
            return false;
        }
        this.writeRefundPricing(refundSlip, unitPrice, unitPriceOut, totalPriceOut);
        return true;
    }

    /**
     * 写入退药 slip 的单价与按退药数量折算的行金额。
     *
     * @param refundSlip     退药 slip
     * @param unitPrice      单价
     * @param unitPriceOut   单价输出
     * @param totalPriceOut  金额输出
     */
    private void writeRefundPricing(
        MedicationDispenseSlipQueryModel refundSlip,
        BigDecimal unitPrice,
        Map<Long, BigDecimal> unitPriceOut,
        Map<Long, BigDecimal> totalPriceOut) {
        if (refundSlip == null || refundSlip.getDispenseId() == null || unitPrice == null) {
            return;
        }
        unitPriceOut.put(refundSlip.getDispenseId(), unitPrice);
        BigDecimal refundQuantity = this.resolveSlipQuantity(refundSlip);
        if (refundQuantity != null && refundQuantity.compareTo(BigDecimal.ZERO) > 0) {
            totalPriceOut.put(
                refundSlip.getDispenseId(),
                MonetaryAmountSupport.lineTotal(unitPrice, refundQuantity));
        }
    }

    /**
     * 按原药嘱批量加载已完成正向发放单（排除 REFUND 语义行）。
     *
     * @param originRequestIds 原药嘱 id 集合
     * @return originRequestId → 正向发放单列表
     */
    private Map<Long, List<MedicationDispense>> loadCompletedForwardDispensesByOriginRequestId(
        LinkedHashSet<Long> originRequestIds) {
        Map<Long, List<MedicationDispense>> result = new HashMap<>();
        if (originRequestIds == null || originRequestIds.isEmpty()) {
            return result;
        }
        List<MedicationDispense> forwardRows =
            this.medicationDispenseQueryService.listByMedReqIds(originRequestIds);
        for (MedicationDispense forward : forwardRows) {
            if (forward == null || forward.getMedReqId() == null || forward.getId() == null) {
                continue;
            }
            if (forward.getStatusEnum() != DispenseStatus.COMPLETED) {
                continue;
            }
            if (forward.getDispenseEnum() == MedicationDispenseType.REFUND) {
                continue;
            }
            result.computeIfAbsent(forward.getMedReqId(), ignored -> new ArrayList<>()).add(forward);
        }
        return result;
    }

    /**
     * 批量加载原正向 dispense 锚点 charge（门诊全状态 + 住院待结算，与列表页正向读价同源）。
     *
     * @param forwardDispensesByOriginRequestId 原药嘱 → 正向发放单
     * @return forwardDispenseId → charge 行
     */
    private Map<Long, List<ChargeItem>> loadChargeRowsByForwardDispenseId(
        Map<Long, List<MedicationDispense>> forwardDispensesByOriginRequestId) {
        Map<Long, List<ChargeItem>> result = new HashMap<>();
        if (forwardDispensesByOriginRequestId == null || forwardDispensesByOriginRequestId.isEmpty()) {
            return result;
        }
        Map<Long, LinkedHashSet<Long>> dispenseIdsByEncounterId = new HashMap<>();
        for (List<MedicationDispense> forwardRows : forwardDispensesByOriginRequestId.values()) {
            if (forwardRows == null) {
                continue;
            }
            for (MedicationDispense forward : forwardRows) {
                if (forward == null || forward.getId() == null || forward.getEncounterId() == null) {
                    continue;
                }
                dispenseIdsByEncounterId
                    .computeIfAbsent(forward.getEncounterId(), ignored -> new LinkedHashSet<>())
                    .add(forward.getId());
            }
        }
        for (Map.Entry<Long, LinkedHashSet<Long>> entry : dispenseIdsByEncounterId.entrySet()) {
            Long encounterId = entry.getKey();
            List<Long> dispenseIds = new ArrayList<>(entry.getValue());
            List<ChargeItem> chargeRows = new ArrayList<>();
            chargeRows.addAll(
                this.chargeItemQueryService.listChargeItemsForMedicationDispensePricing(encounterId, dispenseIds));
            chargeRows.addAll(
                this.chargeItemQueryService.listPendingSettlementChargeItemsForMedicationDispensePricing(
                    encounterId, dispenseIds));
            for (ChargeItem chargeRow : chargeRows) {
                if (chargeRow == null || chargeRow.getDispenseId() == null) {
                    continue;
                }
                result.computeIfAbsent(chargeRow.getDispenseId(), ignored -> new ArrayList<>()).add(chargeRow);
            }
        }
        return result;
    }

    /**
     * 按批号/追溯码优先匹配原正向发放单；无法精确匹配时回落首条候选。
     *
     * @param refundSlip   退药 slip
     * @param forwardRows  原药嘱下正向发放单
     * @return 匹配到的正向发放单；无候选则 null
     */
    private MedicationDispense matchForwardDispense(
        MedicationDispenseSlipQueryModel refundSlip,
        List<MedicationDispense> forwardRows) {
        if (refundSlip == null || forwardRows == null || forwardRows.isEmpty()) {
            return null;
        }
        MedicationDispense fallback = null;
        for (MedicationDispense forward : forwardRows) {
            if (forward == null) {
                continue;
            }
            if (fallback == null) {
                fallback = forward;
            }
            if (this.matchesPresentation(refundSlip, forward)) {
                return forward;
            }
        }
        return fallback;
    }

    /**
     * 退药 slip 与原正向发放单批号/追溯码是否一致（退药创建时自原单拷贝）。
     *
     * @param refundSlip 退药 slip
     * @param forward    原正向发放单
     * @return 展示字段一致为 true
     */
    private boolean matchesPresentation(MedicationDispenseSlipQueryModel refundSlip, MedicationDispense forward) {
        if (refundSlip == null || forward == null) {
            return false;
        }
        if (!this.equalsNullableText(refundSlip.getLotNumber(), forward.getLotNumber())) {
            return false;
        }
        return this.equalsNullableText(refundSlip.getTraceNo(), forward.getTraceNo());
    }

    /**
     * 同页正向/退药行展示匹配键（就诊 + 药品 + 批号 + 追溯码）。
     *
     * @param slip 发放 slip
     * @return 匹配键
     */
    private String buildPresentationMatchKey(MedicationDispenseSlipQueryModel slip) {
        return String.valueOf(slip.getEncounterId())
            + "|"
            + String.valueOf(slip.getMedicationId())
            + "|"
            + this.normalizeMatchText(slip.getLotNumber())
            + "|"
            + this.normalizeMatchText(slip.getTraceNo());
    }

    /**
     * 从 dispense 锚点 charge 解析单价：优先 {@code unit_price}，否则行合计 ÷ 数量。
     *
     * @param chargeRows  charge 行
     * @param dispenseId  发放 id
     * @return 单价；无法解析则 null
     */
    private BigDecimal resolveUnitPriceFromDispenseCharges(List<ChargeItem> chargeRows, Long dispenseId) {
        if (chargeRows == null || chargeRows.isEmpty() || dispenseId == null) {
            return null;
        }
        BigDecimal directUnitPrice = null;
        BigDecimal totalVal = null;
        BigDecimal qtyVal = null;
        for (ChargeItem chargeRow : chargeRows) {
            if (chargeRow == null || !dispenseId.equals(chargeRow.getDispenseId())) {
                continue;
            }
            if (!this.isMedicationDispensePricingChargeStatus(chargeRow.getStatusEnum())) {
                continue;
            }
            if (chargeRow.getUnitPrice() != null && directUnitPrice == null) {
                directUnitPrice = chargeRow.getUnitPrice();
            }
            if (chargeRow.getTotalPrice() != null) {
                if (totalVal == null) {
                    totalVal = chargeRow.getTotalPrice();
                } else {
                    totalVal = totalVal.add(chargeRow.getTotalPrice());
                }
            }
            if (chargeRow.getQuantityValue() != null) {
                if (qtyVal == null) {
                    qtyVal = chargeRow.getQuantityValue();
                } else {
                    qtyVal = qtyVal.add(chargeRow.getQuantityValue());
                }
            }
        }
        if (directUnitPrice != null) {
            return directUnitPrice;
        }
        if (totalVal != null && qtyVal != null && qtyVal.compareTo(BigDecimal.ZERO) > 0) {
            return MonetaryAmountSupport.unitPriceFromLineTotal(totalVal, qtyVal);
        }
        return null;
    }

    /**
     * 原药嘱 service 锚点兜底：取 dispense 锚点或数量匹配行的 {@code unit_price}，禁止累加整单金额。
     *
     * @param chargeRows      原药嘱 charge 行
     * @param refundQuantity  退药数量
     * @return 单价；无法解析则 null
     */
    private BigDecimal resolveUnitPriceFromServiceCharges(
        List<ChargeItem> chargeRows,
        BigDecimal refundQuantity) {
        if (chargeRows == null || chargeRows.isEmpty()) {
            return null;
        }
        BigDecimal quantityMatchedUnitPrice = null;
        BigDecimal dispenseAnchoredUnitPrice = null;
        BigDecimal anyUnitPrice = null;
        for (ChargeItem chargeRow : chargeRows) {
            if (chargeRow == null || chargeRow.getUnitPrice() == null) {
                continue;
            }
            if (anyUnitPrice == null) {
                anyUnitPrice = chargeRow.getUnitPrice();
            }
            if (chargeRow.getDispenseId() != null) {
                if (dispenseAnchoredUnitPrice == null) {
                    dispenseAnchoredUnitPrice = chargeRow.getUnitPrice();
                }
                if (refundQuantity != null
                    && chargeRow.getQuantityValue() != null
                    && chargeRow.getQuantityValue().compareTo(refundQuantity) == 0
                    && quantityMatchedUnitPrice == null) {
                    quantityMatchedUnitPrice = chargeRow.getUnitPrice();
                }
            }
        }
        if (quantityMatchedUnitPrice != null) {
            return quantityMatchedUnitPrice;
        }
        if (dispenseAnchoredUnitPrice != null) {
            return dispenseAnchoredUnitPrice;
        }
        if (anyUnitPrice != null) {
            return anyUnitPrice;
        }
        if (chargeRows.size() == 1) {
            ChargeItem only = chargeRows.get(0);
            if (only != null
                && only.getTotalPrice() != null
                && only.getQuantityValue() != null
                && only.getQuantityValue().compareTo(BigDecimal.ZERO) > 0) {
                return MonetaryAmountSupport.unitPriceFromLineTotal(only.getTotalPrice(), only.getQuantityValue());
            }
        }
        return null;
    }

    /**
     * 解析发放 slip 数量（优先 dispenseQuantity，回落 quantity），取绝对值。
     *
     * @param slip 发药 slip 读模型
     * @return 数量绝对值；无数量时 null
     */
    private BigDecimal resolveSlipQuantity(MedicationDispenseSlipQueryModel slip) {
        BigDecimal quantity = slip.getDispenseQuantity();
        if (quantity == null) {
            quantity = slip.getQuantity();
        }
        if (quantity == null) {
            return null;
        }
        return quantity.abs();
    }

    /**
     * 解析发药 slip 对应的原始医嘱 id（退药行回落 refundMedicineId）。
     *
     * @param slip                        发药 slip 读模型
     * @param medicationRequestByMedReqId medReqId → 医嘱列表模型
     * @return 计价锚点医嘱 id；无法解析则 null
     */
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

    /**
     * 发药 slip 是否为退药语义行（dispenseEnum=REFUND 或 status=REFUNDED）。
     *
     * @param slip 发药 slip
     * @return 退药行为 true
     */
    private boolean isRefundSlipRow(MedicationDispenseSlipQueryModel slip) {
        if (slip.getDispenseEnum() == MedicationDispenseType.REFUND) {
            return true;
        }
        return slip.getStatusEnum() == DispenseStatus.REFUNDED;
    }

    /**
     * 发药划价关心的费用状态（草稿/待结算/已结算/退药中/已退）。
     *
     * @param statusEnum {@link ChargeItemStatus} code
     * @return 参与划价回显为 true
     */
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

    /**
     * 可空字符串相等比较（trim 后比较；双 null 视为相等）。
     *
     * @param left  左值
     * @param right 右值
     * @return 相等为 true
     */
    private boolean equalsNullableText(String left, String right) {
        String normalizedLeft = this.normalizeMatchText(left);
        String normalizedRight = this.normalizeMatchText(right);
        return normalizedLeft.equals(normalizedRight);
    }

    /**
     * 匹配键用字符串归一化：null/空白 → 空串。
     *
     * @param value 原值
     * @return 归一化文本
     */
    private String normalizeMatchText(String value) {
        if (StringUtils.isEmpty(value)) {
            return "";
        }
        return value.trim();
    }

    /** 待补价退药行上下文。 */
    private record RefundPricingTarget(MedicationDispenseSlipQueryModel slip, Long originRequestId) {
    }
}
