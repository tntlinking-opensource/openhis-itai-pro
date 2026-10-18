/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.payment;

import com.zxplat.domain.share.constants.PaymentChannelConstants;
import com.zxplat.domain.infrastructure.integration.payment.ThirdPartyPaymentExtension;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 支付草稿作废半成功门禁：存在未退货的银联被扫 SUCCESS 单时禁止裸作废。
 *
 * <p>对齐挂号「未收费取消」口径；门诊/体检预结算清草稿、住院重预结算清旧草稿共用本 Gate。
 * 正式结算成功、原路退费（先 Win R 再本地）不调用本 Gate，故不影响正常收退费主路径。</p>
 */
@Component
@RequiredArgsConstructor
public class PaymentDraftHalfSuccessAbortGate {

    /** 第三方收单扩展；未装配 Module 时 {@link ThirdPartyPaymentExtension#isEnabled()} 为 false。 */
    private final ThirdPartyPaymentExtension thirdPartyPaymentExtension;

    /**
     * 作废前断言：该支付草稿不得仍挂未退货的银联 SUCCESS 消费单。
     *
     * <p>命中时抛业务异常，迫使收银员补结算或先银联退货，避免 CANCELLED+渠道多收。</p>
     *
     * @param paymentId 待作废的支付草稿主键；空则跳过
     */
    public void assertAbortableWithoutUnreimbursedScan(Long paymentId) {
        // Gate：未启用银联 Module 时无 BPC 单可挂，直接放行
        if (paymentId == null || !thirdPartyPaymentExtension.isEnabled()) {
            return;
        }
        // Gate：BPC SUCCESS 且含 mer/trade → 禁止当普通草稿作废
        if (thirdPartyPaymentExtension.hasSuccessfulConsumerScanOrder(paymentId)) {
            throw new BusinessException(
                PaymentChannelConstants.EX_HALF_SUCCESS_ABORT_BLOCKED,
                PaymentChannelConstants.MESSAGE_HALF_SUCCESS_ABORT_BLOCKED);
        }
    }

    /**
     * 是否存在须原路退的银联 SUCCESS 消费单（供结算后清草稿「跳过」策略使用）。
     *
     * @param paymentId 支付主键；空则 false
     * @return true 表示不可静默作废，应保留草稿待补结算/退货
     */
    public boolean hasUnreimbursedSuccessfulScan(Long paymentId) {
        if (paymentId == null || !thirdPartyPaymentExtension.isEnabled()) {
            return false;
        }
        return thirdPartyPaymentExtension.hasSuccessfulConsumerScanOrder(paymentId);
    }
}
