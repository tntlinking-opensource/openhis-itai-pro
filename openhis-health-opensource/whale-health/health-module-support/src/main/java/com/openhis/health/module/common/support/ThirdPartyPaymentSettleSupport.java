/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.module.common.support;

import com.openhis.health.domain.financial.paymentreconciliation.command.PaymentDetailItemCommand;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.PaymentChannel;
import com.openhis.health.domain.share.payment.BpcPayTypeMapper;
import com.openhis.health.integration.payment.ThirdPartyPaymentExtension;
import com.openhis.health.integration.payment.command.ThirdPartyScanConfirmCommand;
import com.openhis.health.integration.payment.dto.ClientScanResultDto;
import com.openhis.health.integration.payment.result.ThirdPartyScanResult;
import com.openhis.health.module.common.validator.PaymentChannelGate;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * settle 前银联被扫挂接：检测 {@code SCAN_PAY} 行 → {@code applyClientScanResult} →
 * {@link BpcPayTypeMapper} 替换明细 → {@link PaymentChannelGate} 门控。
 */
@Component
@RequiredArgsConstructor
public class ThirdPartyPaymentSettleSupport {

    private static final String SUCCESS_RESP_CODE = "00";

    private final ThirdPartyPaymentExtension thirdPartyPaymentExtension;
    private final PaymentChannelGate paymentChannelGate;

    /**
     * 扫码 settle 前置处理（原地替换 {@code paymentDetails} 中 {@code SCAN_PAY} 行为语义码）。
     *
     * @param paymentId HIS 支付单
     * @param paymentDetails 可变明细（{@code payEnum} 含 {@code SCAN_PAY} 时触发）
     * @param clientScanResult Win 被扫回传；无扫码行时可 null
     * @param operatorId 操作员
     * @param orgId 院区
     */
    public void applyScanBeforeSettle(
        Long paymentId,
        List<PaymentDetailItemCommand> paymentDetails,
        ClientScanResultDto clientScanResult,
        Long operatorId,
        Long orgId) {

        if (paymentDetails == null || paymentDetails.isEmpty()) {
            return;
        }

        List<Integer> scanLineIndexes = findScanPayLineIndexes(paymentDetails);
        if (scanLineIndexes.isEmpty()) {
            return;
        }

        if (clientScanResult == null) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                "扫码支付须提交 clientScanResult");
        }

        requireSuccessRespCode(clientScanResult.getRespCode(), "银联被扫未成功，不可结算");

        if (!thirdPartyPaymentExtension.isEnabled()) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                "第三方收单未开通");
        }

        if (scanLineIndexes.size() > 1) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                "混合支付仅支持一条扫码支付明细");
        }

        int detailIndex = scanLineIndexes.get(0);
        PaymentDetailItemCommand scanLine = paymentDetails.get(detailIndex);
        validateScanLineAmount(scanLine.getAmount());
        validateTxnAmtFen(clientScanResult, scanLine.getAmount());

        ThirdPartyScanConfirmCommand command = new ThirdPartyScanConfirmCommand()
            .setPaymentId(paymentId)
            .setDetailIndex(detailIndex)
            .setAmount(scanLine.getAmount())
            .setMerTradeNo(clientScanResult.getMerTradeNo())
            .setTradeNo(clientScanResult.getTradeNo())
            .setPayType(clientScanResult.getPayType())
            .setRespCode(clientScanResult.getRespCode())
            .setRespMsg(clientScanResult.getRespMsg())
            .setTxnAmtFen(clientScanResult.getTxnAmtFen())
            .setOperatorId(operatorId)
            .setOrgId(orgId);

        ThirdPartyScanResult result = thirdPartyPaymentExtension.applyClientScanResult(command);
        if (result == null || !result.isSuccess()) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                StringUtils.defaultIfBlank(result != null ? result.getRespMsg() : null, "银联收单确认失败"));
        }

        String payEnum = BpcPayTypeMapper.toPayEnum(clientScanResult.getPayType());
        scanLine.setPayEnum(payEnum);
        paymentChannelGate.requirePersistablePayChannelCode(
            payEnum,
            ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
            "支付渠道不合法");
    }

    private static List<Integer> findScanPayLineIndexes(List<PaymentDetailItemCommand> paymentDetails) {
        List<Integer> indexes = new ArrayList<>();
        for (int index = 0; index < paymentDetails.size(); index++) {
            PaymentDetailItemCommand line = paymentDetails.get(index);
            if (line == null) {
                continue;
            }
            if (PaymentChannel.SCAN_PAY.getCode().equals(StringUtils.trimToEmpty(line.getPayEnum()))) {
                indexes.add(index);
            }
        }
        return indexes;
    }

    private static void validateScanLineAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                "扫码支付金额须大于0");
        }
    }

    private static void requireSuccessRespCode(String respCode, String message) {
        if (!SUCCESS_RESP_CODE.equals(StringUtils.trimToEmpty(respCode))) {
            throw new BusinessException(ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT, message);
        }
    }

    private static void validateTxnAmtFen(ClientScanResultDto clientScanResult, BigDecimal amountYuan) {
        Long txnAmtFen = clientScanResult.getTxnAmtFen();
        if (txnAmtFen == null) {
            return;
        }
        long expectedFen = amountYuan.multiply(BigDecimal.valueOf(100L)).setScale(0, RoundingMode.HALF_UP).longValue();
        if (txnAmtFen != expectedFen) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                "扫码支付金额与银联回传不一致");
        }
    }
}
