/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.payment;

import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.infrastructure.integration.payment.ThirdPartyPaymentExtension;
import com.zxplat.domain.infrastructure.integration.payment.ExtensionIn.ThirdPartyRefundConfirmExtensionIn;
import com.zxplat.domain.infrastructure.integration.payment.dto.ClientRefundResultDto;
import com.zxplat.domain.infrastructure.integration.payment.ExtensionOut.ThirdPartyRefundExtensionOut;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

/**
 * 退费前银联退货挂接：{@code clientRefundResult} → {@code applyClientRefundResult}；
 * Win R 失败则不进入本地退费。
 *
 * <p>强制门控：原支付仍有 BPC SUCCESS 被扫单时，禁止 {@code clientRefundResult} 为空就本地退
 * （防止「HIS 已退、银联未退」）。</p>
 */
@Component
@RequiredArgsConstructor
public class ThirdPartyPaymentRefundOrchestrator {

    /** 银联网关成功响应码。 */
    private static final String SUCCESS_RESP_CODE = "00";

    /** 第三方收单扩展：退货确认挂接。 */
    private final ThirdPartyPaymentExtension thirdPartyPaymentExtension;

    /**
     * 原路退扫码段前置处理。
     *
     * @param paymentId HIS 支付单
     * @param clientRefundResult Win 退货回传；非扫码原路退时可 null
     * @param operatorId 操作员
     */
    public void applyRefundBeforeLocalRefund(
        Long paymentId,
        ClientRefundResultDto clientRefundResult,
        Long operatorId) {

        boolean requiresClientRefund = thirdPartyPaymentExtension.isEnabled()
            && paymentId != null
            && thirdPartyPaymentExtension.hasSuccessfulConsumerScanOrder(paymentId);

        if (clientRefundResult == null) {
            if (requiresClientRefund) {
                throw new BusinessException(
                    ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                    "原支付为银联扫码收单，须先完成银联退货后再本地退费（请启动 Zxplat.Win）");
            }
            return;
        }

        requireSuccessRespCode(clientRefundResult.getRespCode());

        if (!thirdPartyPaymentExtension.isEnabled()) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                "第三方收单未开通");
        }

        ThirdPartyRefundConfirmExtensionIn command = new ThirdPartyRefundConfirmExtensionIn()
            .setPaymentId(paymentId)
            .setMerTradeNo(clientRefundResult.getMerTradeNo())
            .setTradeNo(clientRefundResult.getTradeNo())
            .setRefundMerTradeNo(clientRefundResult.getRefundMerTradeNo())
            .setRefundAmount(clientRefundResult.getRefundAmountYuan())
            .setRespCode(clientRefundResult.getRespCode())
            .setRespMsg(clientRefundResult.getRespMsg())
            .setOperatorId(operatorId);

        ThirdPartyRefundExtensionOut result = thirdPartyPaymentExtension.applyClientRefundResult(command);
        if (result == null || !result.isSuccess()) {
            String respMsg;
            if (result != null) {
                respMsg = result.getRespMsg();
            } else {
                respMsg = null;
            }
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                StringUtils.defaultIfBlank(respMsg, "银联退货确认失败"));
        }
    }

    /**
     * 银联退货 Gate：respCode 须为成功码，否则禁止本地退费。
     *
     * @param respCode 网关响应码
     * @throws BusinessException 非成功码
     */
    private static void requireSuccessRespCode(String respCode) {
        if (!SUCCESS_RESP_CODE.equals(StringUtils.trimToEmpty(respCode))) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                "银联退货未成功，不可本地退费");
        }
    }
}
