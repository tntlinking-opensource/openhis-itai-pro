/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.financial;

import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.command.PaymentRecStaticItemCommand;
import com.openhis.health.domain.financial.paymentreconciliation.entity.PaymentReconciliation;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationDomainService;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.share.constants.DailySettlementSharedConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.enums.PaymentKind;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.infrastructure.integration.financial.PaymentFinTypeStaticSnapshotHook;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 结算/退费成功后写入 {@code fin_payment_rec_static}（按 fin_type 汇总本笔收费净额）。
 *
 * <p>金额权威为关联 {@code charge_item.total_price} <strong>代数和</strong>（含冲正负行；日结「总收入」口径，
 * 对齐 AS-OP-FIN-004 **BR-OP-DCL-010** / AS-IP-FIN-005 **BR-IP-DCL-002**）。
 * <strong>禁止</strong>对行额取绝对值再汇总，否则同单退费/冲正会被当成正收入，导致日结总收入虚高。
 * {@code tenderedAmount}/{@code displayAmount} 仍表示患者现金应付（医保全个账可为 0），
 * 不得用作分类快照基数。</p>
 *
 * <p>实现 {@link PaymentFinTypeStaticSnapshotHook}，供落账编排同事务同步调用；
 * 领域事件 {@link PaymentFinTypeStaticSnapshotListener} 仍保留作退费/反结等非 settle 编排路径。</p>
 */
@Component
@RequiredArgsConstructor
public class PaymentFinTypeStaticSnapshotWriter implements PaymentFinTypeStaticSnapshotHook {

    private final PaymentReconciliationQueryService paymentReconciliationQueryService;
    private final PaymentReconciliationDomainService paymentReconciliationDomainService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final DailySettlementFinTypeAmountLoader dailySettlementFinTypeAmountLoader;
    /** 写入快照时同步落 type_name，避免已结算大类名为空。 */
    private final TerminologyOptionLoader terminologyOptionLoader;

    /**
     * 按支付主键重建分类快照；预交金等无收费明细的流水跳过。
     *
     * @param paymentId 支付主键
     */
    @Override
    public void writeForPayment(Long paymentId) {
        // Gate：空主键无支付可写
        if (paymentId == null) {
            return;
        }
        PaymentReconciliation payment = this.paymentReconciliationQueryService.findOptionalPaymentEntityById(paymentId)
            .orElse(null);
        // Gate：支付已删或不存在则跳过，避免空写
        if (payment == null) {
            return;
        }
        // Gate：预交金无收费明细，不参与 fin_type 快照
        if (PaymentKind.HOSPITAL_DEPOSIT.equals(payment.getKindEnum())) {
            return;
        }
        List<Long> chargeItemIds = this.parseChargeItemIds(payment.getChargeItemIds());
        // 无收费明细时无法分类；清空旧快照避免脏数据残留抬高日结
        if (chargeItemIds.isEmpty()) {
            this.paymentReconciliationDomainService.replacePaymentRecStatics(paymentId, List.of());
            return;
        }
        // Pre：装载本笔挂载收费行，供 fin_type 解析与净额汇总
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
        // Pre：按 fin_type 直接 Σ 行净额（含冲正负行），非患者现金应付 tendered、非绝对值
        List<PaymentRecStaticItemCommand> staticItems =
            this.buildStaticItems(chargeItems, finTypeByChargeItemId);
        // Cmd：全量替换本笔快照，与收费挂载保持一致
        this.paymentReconciliationDomainService.replacePaymentRecStatics(paymentId, staticItems);
    }

    /**
     * 按 fin_type 汇总 charge_item.total_price 代数和（保留冲正负行）。
     *
     * @param chargeItems           本笔支付关联收费明细
     * @param finTypeByChargeItemId chargeItemId → fin_type_code
     * @return 分类快照命令列表
     */
    private List<PaymentRecStaticItemCommand> buildStaticItems(
        List<ChargeItem> chargeItems,
        Map<Long, String> finTypeByChargeItemId) {
        Map<String, BigDecimal> amountByFinType = new LinkedHashMap<>();
        Map<String, StringBuilder> chargeItemIdsByFinType = new LinkedHashMap<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || chargeItem.getId() == null) {
                continue;
            }
            String finTypeCode = finTypeByChargeItemId.get(chargeItem.getId());
            // Gate：缺 fin_type 则整笔拒绝，禁止静默归入未知类抬高/压低日结
            if (StringUtils.isEmpty(finTypeCode)) {
                throw new BusinessException(
                    ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT,
                    "收费明细无法解析财务类型，无法写入分类快照，chargeItemId=" + chargeItem.getId());
            }
            BigDecimal lineAmount = chargeItem.getTotalPrice();
            if (lineAmount == null) {
                lineAmount = BigDecimal.ZERO;
            }
            BigDecimal existing = amountByFinType.get(finTypeCode);
            if (existing == null) {
                existing = BigDecimal.ZERO;
            }
            // 代数和：冲正负行抵减，禁止 abs 后再加
            amountByFinType.put(finTypeCode, existing.add(lineAmount));
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
        if (amountByFinType.isEmpty()) {
            return List.of();
        }
        Map<String, String> labelByCode =
            this.terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.FIN_TYPE_CODE);
        List<PaymentRecStaticItemCommand> result = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : amountByFinType.entrySet()) {
            PaymentRecStaticItemCommand item = new PaymentRecStaticItemCommand();
            item.setMethodCode(DailySettlementSharedConstants.PAYMENT_REC_STATIC_METHOD_FIN_TYPE_CODE);
            item.setTypeCode(entry.getKey());
            if (labelByCode != null) {
                String typeName = labelByCode.get(entry.getKey());
                if (!StringUtils.isEmpty(typeName)) {
                    item.setTypeName(typeName);
                }
            }
            BigDecimal amount = entry.getValue();
            if (amount == null) {
                amount = BigDecimal.ZERO;
            }
            // 计算刻度 6 位入库，与 charge_item.total_price / BR-MON-001 一致
            item.setAmount(MonetaryAmountSupport.scaleCalc(amount));
            StringBuilder idsBuilder = chargeItemIdsByFinType.get(entry.getKey());
            if (idsBuilder != null) {
                item.setChargeItemIds(idsBuilder.toString());
            }
            result.add(item);
        }
        return result;
    }

    /**
     * 解析支付主表逗号分隔的收费明细主键列表；脏片段跳过。
     *
     * @param chargeItemIds 逗号分隔 chargeItemId 串
     * @return 有效主键列表（可空）
     */
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
                // 脏片段跳过，避免整笔快照失败；缺失行由后续装载校验兜底
            }
        }
        return result;
    }
}
