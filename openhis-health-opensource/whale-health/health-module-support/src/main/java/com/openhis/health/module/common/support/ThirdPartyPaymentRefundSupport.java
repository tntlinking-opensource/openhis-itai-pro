/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.module.common.support;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.integration.payment.ThirdPartyPaymentExtension;
import com.openhis.health.integration.payment.command.ThirdPartyRefundConfirmCommand;
import com.openhis.health.integration.payment.dto.ClientRefundResultDto;
import com.openhis.health.integration.payment.result.ThirdPartyRefundResult;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * 退费前银联退货挂接：{@code clientRefundResult} → {@code applyClientRefundResult}；
 * Win R 失败则不进入本地退费。
 */
@Component
@RequiredArgsConstructor
public class ThirdPartyPaymentRefundSupport {

    private static final String SUCCESS_RESP_CODE = "00";

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

        if (clientRefundResult == null) {
            return;
        }

        requireSuccessRespCode(clientRefundResult.getRespCode());

        if (!thirdPartyPaymentExtension.isEnabled()) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                "第三方收单未开通");
        }

        ThirdPartyRefundConfirmCommand command = new ThirdPartyRefundConfirmCommand()
            .setPaymentId(paymentId)
            .setMerTradeNo(clientRefundResult.getMerTradeNo())
            .setTradeNo(clientRefundResult.getTradeNo())
            .setRefundMerTradeNo(clientRefundResult.getRefundMerTradeNo())
            .setRefundAmount(clientRefundResult.getRefundAmountYuan())
            .setRespCode(clientRefundResult.getRespCode())
            .setRespMsg(clientRefundResult.getRespMsg())
            .setOperatorId(operatorId);

        ThirdPartyRefundResult result = thirdPartyPaymentExtension.applyClientRefundResult(command);
        if (result == null || !result.isSuccess()) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                StringUtils.defaultIfBlank(result != null ? result.getRespMsg() : null, "银联退货确认失败"));
        }
    }

    private static void requireSuccessRespCode(String respCode) {
        if (!SUCCESS_RESP_CODE.equals(StringUtils.trimToEmpty(respCode))) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                "银联退货未成功，不可本地退费");
        }
    }
}
