/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.module.common.support;

import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.share.enums.PaymentChannel;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 退费前按原支付明细渠道判定是否可能须银联 BPC 原路退。
 *
 * <p>真源为 {@code fin_payment_rec_detail.pay_enum}；仅当存在微信/支付宝/银行卡等
 * 可能被扫落库的渠道时，才建议前端继续查 {@code upc_bpc_order}。</p>
 */
@Component
@RequiredArgsConstructor
public class PaymentChannelRefundSupport {

    private final PaymentReconciliationQueryService paymentReconciliationQueryService;

    /**
     * 读取原支付明细上的去重渠道码。
     *
     * @param paymentId 原支付主单
     * @return 渠道 code 列表；无明细时返回空表
     */
    public List<String> listDistinctPayChannelCodes(Long paymentId) {
        if (paymentId == null) {
            return Collections.emptyList();
        }
        return paymentReconciliationQueryService
            .mapDistinctPayEnumCodesByPaymentIds(List.of(paymentId))
            .getOrDefault(paymentId, Collections.emptyList());
    }

    /**
     * 原支付是否可能存在银联被扫段（须再查 BPC SUCCESS 才能确认 Win 退货）。
     *
     * @param payChannelCodes 原支付明细渠道码
     * @return 仅现金/医保/收银台等渠道时为 false
     */
    public boolean mayRequireUnionPayBpcRefund(List<String> payChannelCodes) {
        if (payChannelCodes == null || payChannelCodes.isEmpty()) {
            return false;
        }
        for (String code : payChannelCodes) {
            if (StringUtils.isBlank(code)) {
                continue;
            }
            String normalized = code.trim();
            if (PaymentChannel.WECHAT.getCode().equals(normalized)
                || PaymentChannel.ALIPAY.getCode().equals(normalized)
                || PaymentChannel.BANK_CARD.getCode().equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 批量读取各支付单的去重渠道码。
     *
     * @param paymentIds 支付主键集合
     * @return paymentId → 渠道码列表
     */
    public Map<Long, List<String>> mapDistinctPayChannelCodesByPaymentIds(Collection<Long> paymentIds) {
        if (paymentIds == null || paymentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return paymentReconciliationQueryService.mapDistinctPayEnumCodesByPaymentIds(paymentIds);
    }
}
