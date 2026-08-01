/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.financial;

import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.command.PaymentRecStaticItemCommand;
import com.openhis.health.domain.financial.paymentreconciliation.entity.PaymentReconciliation;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationDomainService;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.share.constants.DailySettlementSharedConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.PaymentKind;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 结算/退费成功后写入 {@code fin_payment_rec_static}（按 fin_type 分摊本笔收费总额）。
 *
 * <p>金额权威为关联 {@code charge_item.total_price} 绝对值之和（日结「总收入」口径）；
 * charge_item 同时提供 fin_type 分类权重。{@code tenderedAmount}/{@code displayAmount} 仍表示
 * 患者现金应付（医保全个账可为 0），不得用作分类快照分摊基数。</p>
 */
@Component
@RequiredArgsConstructor
public class PaymentFinTypeStaticSnapshotWriter {

    private static final int MONEY_SCALE = 6;

    private final PaymentReconciliationQueryService paymentReconciliationQueryService;
    private final PaymentReconciliationDomainService paymentReconciliationDomainService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final DailySettlementFinTypeAmountLoader dailySettlementFinTypeAmountLoader;

    /**
     * 按支付主键重建分类快照；预交金等无收费明细的流水跳过。
     *
     * @param paymentId 支付主键
     */
    public void writeForPayment(Long paymentId) {
        if (paymentId == null) {
            return;
        }
        PaymentReconciliation payment = this.paymentReconciliationQueryService.findOptionalPaymentEntityById(paymentId)
            .orElse(null);
        if (payment == null) {
            return;
        }
        if (PaymentKind.HOSPITAL_DEPOSIT.equals(payment.getKindEnum())) {
            return;
        }
        List<Long> chargeItemIds = this.parseChargeItemIds(payment.getChargeItemIds());
        if (chargeItemIds.isEmpty()) {
            // 无收费明细时无法分类；清空旧快照避免脏数据
            this.paymentReconciliationDomainService.replacePaymentRecStatics(paymentId, List.of());
            return;
        }
        ChargeItemQueryCommand queryCommand = new ChargeItemQueryCommand();
        queryCommand.setIds(chargeItemIds);
        List<ChargeItem> chargeItems = this.chargeItemQueryService.loadChargeItemsByIds(queryCommand);
        if (chargeItems == null || chargeItems.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_NOT_FOUND,
                "支付关联收费明细不存在，无法写入费用分类快照，paymentId=" + paymentId);
        }
        Map<Long, String> finTypeByChargeItemId =
            this.dailySettlementFinTypeAmountLoader.mapFinTypeCodeByChargeItemId(chargeItems);
        // Pre：分摊基数取收费明细总额（非患者现金应付 tendered）
        BigDecimal allocateAmount = this.resolveAllocateAmount(chargeItems);
        List<PaymentRecStaticItemCommand> staticItems =
            this.buildStaticItems(chargeItems, finTypeByChargeItemId, allocateAmount);
        this.paymentReconciliationDomainService.replacePaymentRecStatics(paymentId, staticItems);
    }

    /**
     * 本笔分摊基数：Σ|charge_item.total_price|（日结总收入权威；与现金应付 displayAmount 解耦）。
     *
     * @param chargeItems 本笔支付关联收费明细
     * @return 非负分摊总额
     */
    private BigDecimal resolveAllocateAmount(List<ChargeItem> chargeItems) {
        BigDecimal total = BigDecimal.ZERO;
        if (chargeItems == null || chargeItems.isEmpty()) {
            return total;
        }
        // Loop：按收费行总额绝对值累加，空总额按 0
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null) {
                continue;
            }
            BigDecimal lineTotal = chargeItem.getTotalPrice();
            if (lineTotal == null) {
                continue;
            }
            total = total.add(lineTotal.abs());
        }
        return total;
    }

    /**
     * 按 charge_item.total_price 权重将收费总额分摊到 fin_type；末行吃掉尾差。
     */
    private List<PaymentRecStaticItemCommand> buildStaticItems(
        List<ChargeItem> chargeItems,
        Map<Long, String> finTypeByChargeItemId,
        BigDecimal allocateAmount) {
        Map<String, BigDecimal> weightByFinType = new LinkedHashMap<>();
        Map<String, StringBuilder> chargeItemIdsByFinType = new LinkedHashMap<>();
        BigDecimal weightTotal = BigDecimal.ZERO;
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || chargeItem.getId() == null) {
                continue;
            }
            String finTypeCode = finTypeByChargeItemId.get(chargeItem.getId());
            if (StringUtils.isEmpty(finTypeCode)) {
                throw new BusinessException(
                    ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT,
                    "收费明细无法解析财务类型，无法写入分类快照，chargeItemId=" + chargeItem.getId());
            }
            BigDecimal lineWeight = chargeItem.getTotalPrice();
            if (lineWeight == null) {
                lineWeight = BigDecimal.ZERO;
            }
            lineWeight = lineWeight.abs();
            BigDecimal existing = weightByFinType.get(finTypeCode);
            if (existing == null) {
                existing = BigDecimal.ZERO;
            }
            weightByFinType.put(finTypeCode, existing.add(lineWeight));
            weightTotal = weightTotal.add(lineWeight);
            StringBuilder idsBuilder = chargeItemIdsByFinType.get(finTypeCode);
            if (idsBuilder == null) {
                idsBuilder = new StringBuilder();
                chargeItemIdsByFinType.put(finTypeCode, idsBuilder);
            }
            if (idsBuilder.length() > 0) {
                idsBuilder.append(',');
            }
            idsBuilder.append(chargeItem.getId());
        }
        if (weightByFinType.isEmpty()) {
            return List.of();
        }
        List<String> orderedTypes = new ArrayList<>(weightByFinType.keySet());
        List<PaymentRecStaticItemCommand> result = new ArrayList<>();
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < orderedTypes.size(); i++) {
            String typeCode = orderedTypes.get(i);
            BigDecimal amount;
            if (i == orderedTypes.size() - 1) {
                amount = allocateAmount.subtract(allocated);
            } else if (weightTotal.compareTo(BigDecimal.ZERO) <= 0) {
                amount = BigDecimal.ZERO;
            } else {
                amount = allocateAmount
                    .multiply(weightByFinType.get(typeCode))
                    .divide(weightTotal, MONEY_SCALE, RoundingMode.HALF_UP);
                allocated = allocated.add(amount);
            }
            PaymentRecStaticItemCommand item = new PaymentRecStaticItemCommand();
            item.setMethodCode(DailySettlementSharedConstants.PAYMENT_REC_STATIC_METHOD_FIN_TYPE_CODE);
            item.setTypeCode(typeCode);
            item.setAmount(amount);
            StringBuilder idsBuilder = chargeItemIdsByFinType.get(typeCode);
            if (idsBuilder != null) {
                item.setChargeItemIds(idsBuilder.toString());
            }
            result.add(item);
        }
        return result;
    }

    private List<Long> parseChargeItemIds(String chargeItemIds) {
        List<Long> result = new ArrayList<>();
        if (StringUtils.isEmpty(chargeItemIds)) {
            return result;
        }
        String[] parts = chargeItemIds.split(",");
        for (String part : parts) {
            if (StringUtils.isEmpty(part)) {
                continue;
            }
            try {
                result.add(Long.valueOf(part.trim()));
            } catch (NumberFormatException ignored) {
                // 脏数据跳过
            }
        }
        return result;
    }
}
