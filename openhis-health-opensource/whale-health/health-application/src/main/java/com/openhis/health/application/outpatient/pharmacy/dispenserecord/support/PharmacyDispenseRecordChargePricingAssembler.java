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
 * 发退药记录定价补充：退药写路径<strong>不</strong>生成自身 {@code ChargeItem}（设计如此），
 * 读侧须按原正向发放账单补售价，与发药行同源轧差（#4M-RFND）。
 *
 * <p>禁止只 JOIN 退药自身 {@code dispense_id}；同数量时退药售价总额绝对值须与正向账单
 * {@code total_price} 一致，禁止用单价×数量另算产生尾差。</p>
 */
@Component
@RequiredArgsConstructor
public class PharmacyDispenseRecordChargePricingAssembler {

    private final ChargeItemQueryService chargeItemQueryService;
    private final MedicationDispenseQueryService medicationDispenseQueryService;

    /**
     * 为尚无可用售价的退药行补单价/金额（写入为正额绝对值，调用方按退药取负）。
     *
     * <p>退药不查自身账单。优先级：① 同窗正向已算价 → ② 原正向发放
     * {@code dispense_id} 账单 → ③ 原药嘱 service 锚点单价。同数量抄正向总额；部分退按数量比例折算。</p>
     *
     * @param slipRows                    发放事实读模型
     * @param medicationRequestByMedReqId 药嘱索引（须含退药行，以解析 {@code refundMedicineId}）
     * @param unitPriceOut                dispenseId → 单价（可写）
     * @param totalPriceOut               dispenseId → 金额（可写；退药为正额）
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
            // Pre：正向行仅建展示匹配键，供同窗一发一退配对
            if (!this.isRefundSlipRow(slip)) {
                forwardSlipByPresentationKey.put(this.buildPresentationMatchKey(slip), slip);
                continue;
            }
            // Gate：无 dispenseId 或已有非 0 售价则跳过（0 元占位仍须补原正向）
            if (slip.getDispenseId() == null
                || this.hasUsableSalePricing(slip.getDispenseId(), unitPriceOut, totalPriceOut)) {
                continue;
            }
            Long originRequestId = this.resolveOriginMedicationRequestId(slip, medicationRequestByMedReqId);
            // Gate：无法解析原药嘱则无法补价（禁止冒充退药自身账单）
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
            BigDecimal refundQuantity = this.resolveSlipQuantity(target.slip());
            // Gate：无退药数量则无法按单价折行额
            if (refundQuantity == null || refundQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal unitPrice = this.resolveUnitPriceFromServiceCharges(
                serviceChargeRowsByOriginRequestId.get(target.originRequestId()),
                refundQuantity);
            // Gate：service 锚点仍无单价则本退药行售价保持空（禁止批号冒充）
            if (unitPrice == null) {
                continue;
            }
            // Asm：末级兜底仅有单价时用单价×数量；优先路径已保证与正向总额同额
            BigDecimal totalAbs = MonetaryAmountSupport.lineTotal(unitPrice, refundQuantity);
            this.writeRefundPricing(target.slip(), unitPrice, totalAbs, unitPriceOut, totalPriceOut);
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
     * 从正向 dispense 定价映射或 charge 行回填退药定价（同量抄总额，部分退按量折算）。
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
        BigDecimal refundQuantity = this.resolveSlipQuantity(refundSlip);
        // Gate：退药数量无效则无法与正向对齐金额
        if (refundQuantity == null || refundQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        ForwardSaleSnapshot forwardSale = this.resolveForwardSaleSnapshot(
            forwardDispenseId, forwardSlip, unitPriceOut, totalPriceOut, forwardChargeRows);
        // Gate：原正向无可用售价则本路径失败，交由 service 锚点兜底
        if (forwardSale == null || forwardSale.totalAbs() == null) {
            return false;
        }
        BigDecimal refundTotalAbs = this.resolveRefundTotalAbsFromForward(
            forwardSale.totalAbs(), forwardSale.quantityAbs(), refundQuantity);
        // Gate：折算失败则不写假 0
        if (refundTotalAbs == null) {
            return false;
        }
        BigDecimal refundUnit = forwardSale.unitPrice();
        // Pre：缺单价时用退药行额反推，保证单价与总额同阶
        if (refundUnit == null) {
            refundUnit = MonetaryAmountSupport.unitPriceFromLineTotal(refundTotalAbs, refundQuantity);
        }
        this.writeRefundPricing(refundSlip, refundUnit, refundTotalAbs, unitPriceOut, totalPriceOut);
        return true;
    }

    /**
     * 解析原正向售价快照：优先输出映射，其次 charge 行代数和。
     *
     * @param forwardDispenseId 原正向发放 id
     * @param forwardSlip       同窗正向 slip（可空）
     * @param unitPriceOut      已合并单价映射
     * @param totalPriceOut     已合并总额映射
     * @param forwardChargeRows 原正向 charge（可空）
     * @return 正向售价快照；无法解析则 null
     */
    private ForwardSaleSnapshot resolveForwardSaleSnapshot(
        Long forwardDispenseId,
        MedicationDispenseSlipQueryModel forwardSlip,
        Map<Long, BigDecimal> unitPriceOut,
        Map<Long, BigDecimal> totalPriceOut,
        List<ChargeItem> forwardChargeRows) {
        BigDecimal unitPrice = null;
        BigDecimal totalAbs = null;
        BigDecimal quantityAbs = null;
        if (unitPriceOut != null) {
            unitPrice = unitPriceOut.get(forwardDispenseId);
        }
        if (totalPriceOut != null) {
            BigDecimal mappedTotal = totalPriceOut.get(forwardDispenseId);
            if (mappedTotal != null) {
                totalAbs = mappedTotal.abs();
            }
        }
        if (forwardSlip != null) {
            quantityAbs = this.resolveSlipQuantity(forwardSlip);
            // Pre：映射有总额无单价时，用正向数量反推单价
            if (unitPrice == null && totalAbs != null && quantityAbs != null
                && quantityAbs.compareTo(BigDecimal.ZERO) > 0) {
                unitPrice = MonetaryAmountSupport.unitPriceFromLineTotal(totalAbs, quantityAbs);
            }
        }
        // Pre：跨窗补价时从原正向 charge 汇总总额/数量/单价
        if (totalAbs == null || quantityAbs == null || unitPrice == null) {
            ForwardSaleSnapshot fromCharges =
                this.resolveForwardSaleSnapshotFromCharges(forwardChargeRows, forwardDispenseId);
            if (fromCharges != null) {
                if (totalAbs == null) {
                    totalAbs = fromCharges.totalAbs();
                }
                if (quantityAbs == null) {
                    quantityAbs = fromCharges.quantityAbs();
                }
                if (unitPrice == null) {
                    unitPrice = fromCharges.unitPrice();
                }
            }
        }
        // Gate：至少要有总额才能与发药钱对齐
        if (totalAbs == null || totalAbs.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return new ForwardSaleSnapshot(unitPrice, totalAbs, quantityAbs);
    }

    /**
     * 从原正向发放锚点 charge 汇总售价总额、数量与单价。
     *
     * @param chargeRows charge 行
     * @param dispenseId 正向发放 id
     * @return 快照；无可用行则 null
     */
    private ForwardSaleSnapshot resolveForwardSaleSnapshotFromCharges(
        List<ChargeItem> chargeRows,
        Long dispenseId) {
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
            // Gate：仅发药划价关心的收费态参与补价
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
        if (totalVal == null) {
            return null;
        }
        BigDecimal totalAbs = totalVal.abs();
        BigDecimal quantityAbs = null;
        if (qtyVal != null) {
            quantityAbs = qtyVal.abs();
        }
        BigDecimal unitPrice = directUnitPrice;
        if (unitPrice == null && quantityAbs != null && quantityAbs.compareTo(BigDecimal.ZERO) > 0) {
            unitPrice = MonetaryAmountSupport.unitPriceFromLineTotal(totalAbs, quantityAbs);
        }
        return new ForwardSaleSnapshot(unitPrice, totalAbs, quantityAbs);
    }

    /**
     * 由正向总额推导退药正额：同数量则原样抄总额；部分退按数量比例折算。
     *
     * @param forwardTotalAbs 正向售价总额绝对值
     * @param forwardQtyAbs   正向数量绝对值；可空（仅同量无法判定时回落失败）
     * @param refundQtyAbs    退药数量绝对值
     * @return 退药售价正额；无法对齐则 null
     */
    private BigDecimal resolveRefundTotalAbsFromForward(
        BigDecimal forwardTotalAbs,
        BigDecimal forwardQtyAbs,
        BigDecimal refundQtyAbs) {
        if (forwardTotalAbs == null || refundQtyAbs == null) {
            return null;
        }
        // 同数量：与发药钱绝对值一致，禁止单价×数量另算
        if (forwardQtyAbs != null && forwardQtyAbs.compareTo(refundQtyAbs) == 0) {
            return MonetaryAmountSupport.scaleCalc(forwardTotalAbs);
        }
        // 部分退：须有正向数量作分母
        if (forwardQtyAbs == null || forwardQtyAbs.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return MonetaryAmountSupport.proportionAtCalcScale(
            forwardTotalAbs, refundQtyAbs, forwardQtyAbs);
    }

    /**
     * 写入退药 slip 的单价与正额行金额（调用方再取负）。
     *
     * @param refundSlip     退药 slip
     * @param unitPrice      单价
     * @param totalAbs       售价总额正额（已与正向对齐）
     * @param unitPriceOut   单价输出
     * @param totalPriceOut  金额输出
     */
    private void writeRefundPricing(
        MedicationDispenseSlipQueryModel refundSlip,
        BigDecimal unitPrice,
        BigDecimal totalAbs,
        Map<Long, BigDecimal> unitPriceOut,
        Map<Long, BigDecimal> totalPriceOut) {
        if (refundSlip == null || refundSlip.getDispenseId() == null || totalAbs == null) {
            return;
        }
        if (unitPrice != null) {
            unitPriceOut.put(refundSlip.getDispenseId(), MonetaryAmountSupport.scaleCalc(unitPrice));
        }
        totalPriceOut.put(refundSlip.getDispenseId(), MonetaryAmountSupport.scaleCalc(totalAbs));
    }

    /**
     * 退药行是否已有可用售价（非空且非 0；0 元占位须继续补原正向）。
     *
     * @param dispenseId    发放主键
     * @param unitPriceOut  单价映射
     * @param totalPriceOut 总额映射
     * @return 已有可用售价为 true
     */
    private boolean hasUsableSalePricing(
        Long dispenseId,
        Map<Long, BigDecimal> unitPriceOut,
        Map<Long, BigDecimal> totalPriceOut) {
        if (dispenseId == null) {
            return false;
        }
        if (unitPriceOut != null) {
            BigDecimal unit = unitPriceOut.get(dispenseId);
            if (unit != null && unit.compareTo(BigDecimal.ZERO) != 0) {
                return true;
            }
        }
        if (totalPriceOut != null) {
            BigDecimal total = totalPriceOut.get(dispenseId);
            if (total != null && total.compareTo(BigDecimal.ZERO) != 0) {
                return true;
            }
        }
        return false;
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
            // Gate：仅 COMPLETED 正向参与补价锚点
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
        LinkedHashSet<Long> forwardDispenseIds = new LinkedHashSet<>();
        for (List<MedicationDispense> forwardRows : forwardDispensesByOriginRequestId.values()) {
            if (forwardRows == null) {
                continue;
            }
            for (MedicationDispense forward : forwardRows) {
                if (forward == null || forward.getId() == null) {
                    continue;
                }
                forwardDispenseIds.add(forward.getId());
            }
        }
        if (forwardDispenseIds.isEmpty()) {
            return result;
        }
        // Pre：跨就诊一次按 dispenseIds 批量读价（含待结算），禁止按就诊循环 N+1
        List<ChargeItem> chargeRows =
            this.chargeItemQueryService.listChargeItemsForMedicationDispensePricingByDispenseIds(
                forwardDispenseIds, true);
        for (ChargeItem chargeRow : chargeRows) {
            if (chargeRow == null || chargeRow.getDispenseId() == null) {
                continue;
            }
            result.computeIfAbsent(chargeRow.getDispenseId(), ignored -> new ArrayList<>()).add(chargeRow);
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

    /**
     * 原正向发放售价快照（金额为正额绝对值）。
     *
     * @param unitPrice    售价单价；可空
     * @param totalAbs     售价总额绝对值
     * @param quantityAbs  正向数量绝对值；可空
     */
    private record ForwardSaleSnapshot(BigDecimal unitPrice, BigDecimal totalAbs, BigDecimal quantityAbs) {
    }
}
