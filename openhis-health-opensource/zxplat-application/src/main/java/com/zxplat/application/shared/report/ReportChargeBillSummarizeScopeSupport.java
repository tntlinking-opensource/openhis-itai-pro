/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.report;

import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.financial.paymentreconciliation.queries.model.OutpatientFeeDetailChargePaymentLinkQueryModel;
import com.zxplat.domain.financial.paymentreconciliation.queries.model.OutpatientFeeDetailPaymentContextQueryModel;
import com.zxplat.domain.share.enums.ChargeItemStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 报表费用明细：由账单（ChargeItem）合成读侧链路上下文（禁止扫 Payment）。
 *
 * <p>门/住院费用明细（明细+汇总）统一走本路径；已结时间轴取 {@code entered_date}，待结回落
 * {@code occurrence_time}。<strong>收费人</strong>仅取结算/退费完成写入的 {@code enterer_id}
 *（{@code SETTLED}/{@code REFUNDED}）；待结不得用开单人/执行人冒充。</p>
 */
@Component
public class ReportChargeBillSummarizeScopeSupport {

    /**
     * 将已结/已退账单行合成为与费用明细同构的 charge↔payment 关联（无 paymentId）。
     *
     * @param chargeItems 账单行
     * @return 链路列表；空入参返回空列表
     */
    public List<OutpatientFeeDetailChargePaymentLinkQueryModel> buildSyntheticPaymentLinks(
        Collection<ChargeItem> chargeItems) {
        List<OutpatientFeeDetailChargePaymentLinkQueryModel> links = new ArrayList<>();
        if (chargeItems == null || chargeItems.isEmpty()) {
            return links;
        }
        for (ChargeItem chargeItem : chargeItems) {
            OutpatientFeeDetailPaymentContextQueryModel context = this.buildSyntheticPaymentContext(chargeItem);
            if (context == null) {
                continue;
            }
            OutpatientFeeDetailChargePaymentLinkQueryModel link =
                new OutpatientFeeDetailChargePaymentLinkQueryModel();
            link.setChargeItemId(chargeItem.getId());
            link.setPaymentContext(context);
            links.add(link);
        }
        return links;
    }

    /**
     * chargeItemId → 合成上下文（同 id 后者覆盖，汇总路径一账单一行）。
     *
     * @param chargeItems 账单行
     * @return 映射；空入参返回空 Map
     */
    public Map<Long, OutpatientFeeDetailPaymentContextQueryModel> buildSyntheticPaymentContextByChargeItemId(
        Collection<ChargeItem> chargeItems) {
        Map<Long, OutpatientFeeDetailPaymentContextQueryModel> contextById = new LinkedHashMap<>();
        if (chargeItems == null || chargeItems.isEmpty()) {
            return contextById;
        }
        for (ChargeItem chargeItem : chargeItems) {
            OutpatientFeeDetailPaymentContextQueryModel context = this.buildSyntheticPaymentContext(chargeItem);
            if (context == null || chargeItem.getId() == null) {
                continue;
            }
            contextById.put(chargeItem.getId(), context);
        }
        return contextById;
    }

    /**
     * 单条账单合成 payment 读上下文：无 paymentId；退费态写 {@code refundPayment=true}。
     *
     * @param chargeItem 账单行
     * @return 上下文；入参无效时 null
     */
    public OutpatientFeeDetailPaymentContextQueryModel buildSyntheticPaymentContext(ChargeItem chargeItem) {
        if (chargeItem == null || chargeItem.getId() == null) {
            return null;
        }
        OutpatientFeeDetailPaymentContextQueryModel context = new OutpatientFeeDetailPaymentContextQueryModel();
        context.setChargeItemId(chargeItem.getId());
        context.setEncounterId(chargeItem.getEncounterId());
        // 时间轴：已结取 entered_date；待结回落 occurrence_time（业务时间）
        LocalDateTime billDate = chargeItem.getEnteredDate();
        if (billDate == null) {
            billDate = chargeItem.getOccurrenceTime();
        }
        context.setBillDate(billDate);
        context.setPaymentId(null);
        // 收费人：仅结算/退费完成才有 enterer_id；待结强制空，禁止乱回填
        String statusCode = chargeItem.getStatusEnum();
        boolean settledOrRefunded =
            ChargeItemStatus.SETTLED.getCode().equals(statusCode)
                || ChargeItemStatus.REFUNDED.getCode().equals(statusCode);
        if (settledOrRefunded) {
            context.setCashierId(chargeItem.getEntererId());
        } else {
            context.setCashierId(null);
        }
        context.setRefundPayment(ChargeItemStatus.REFUNDED.getCode().equals(statusCode));
        context.setYbFundAndAcctTotal(null);
        context.setPaymentSnapshotAbsAmountTotal(null);
        return context;
    }
}
