/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.payment;

import com.openhis.health.domain.financial.paymentreconciliation.command.PaymentDetailItemCommand;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.PaymentChannel;
import com.openhis.health.domain.share.payment.BpcPayTypeMapper;
import com.openhis.health.infrastructure.integration.payment.ThirdPartyPaymentExtension;
import com.openhis.health.infrastructure.integration.payment.ExtensionIn.ThirdPartyScanConfirmExtensionIn;
import com.openhis.health.infrastructure.integration.payment.dto.ClientScanResultDto;
import com.openhis.health.infrastructure.integration.payment.ExtensionOut.ThirdPartyScanExtensionOut;
import com.openhis.health.application.shared.validator.PaymentChannelGate;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
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
public class ThirdPartyPaymentSettleOrchestrator {

    /** 银联网关成功响应码。 */
    private static final String SUCCESS_RESP_CODE = "00";

    /** 第三方收单扩展：被扫确认挂接。 */
    private final ThirdPartyPaymentExtension thirdPartyPaymentExtension;
    /** 支付渠道门控：校验可落库 pay_enum。 */
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

        if (!SUCCESS_RESP_CODE.equals(StringUtils.trimToEmpty(clientScanResult.getRespCode()))) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                "银联被扫未成功，不可结算");
        }

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

        ThirdPartyScanConfirmExtensionIn command = new ThirdPartyScanConfirmExtensionIn()
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

        ThirdPartyScanExtensionOut result = thirdPartyPaymentExtension.applyClientScanResult(command);
        if (result == null || !result.isSuccess()) {
            String respMsg;
            if (result != null) {
                respMsg = result.getRespMsg();
            } else {
                respMsg = null;
            }
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                StringUtils.defaultIfBlank(respMsg, "银联收单确认失败"));
        }

        String payEnum = BpcPayTypeMapper.toPayEnum(clientScanResult.getPayType());
        scanLine.setPayEnum(payEnum);
        paymentChannelGate.requirePersistablePayChannelCode(
            payEnum,
            ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
            "支付渠道不合法");
        this.applyPaidAmountKeepingInsuranceAcct(scanLine, clientScanResult);
    }

    /**
     * POS 实付低于院内下单时，扫码行改记实付。
     *
     * <p>差额已由 2207 写入 {@link PaymentChannel#INSURANCE_ACCT}（医保账户余额扣除），
     * 禁止再记优惠或第二笔个账。</p>
     *
     * @param scanLine         已替换为微信/支付宝/银行卡的原扫码行
     * @param clientScanResult Win 被扫回传
     */
    private void applyPaidAmountKeepingInsuranceAcct(
        PaymentDetailItemCommand scanLine,
        ClientScanResultDto clientScanResult) {
        Long paidFen = clientScanResult.getTxnAmtFen();
        if (paidFen == null || scanLine.getAmount() == null) {
            return;
        }
        BigDecimal paidYuan = BigDecimal.valueOf(paidFen)
            .divide(BigDecimal.valueOf(100L), 2, RoundingMode.HALF_UP);
        BigDecimal orderYuan = scanLine.getAmount().setScale(2, RoundingMode.HALF_UP);
        if (paidYuan.compareTo(orderYuan) >= 0) {
            return;
        }
        scanLine.setAmount(paidYuan);
        scanLine.setChargeAmount(paidYuan);
    }

    /**
     * 收集支付明细中 {@link PaymentChannel#SCAN_PAY} 行的下标，供 settle 前单条扫码替换。
     *
     * @param paymentDetails 待结算支付明细
     * @return 扫码行下标列表（保持原序）
     */
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

    /**
     * 扫码支付行金额 Gate：须为正数方可进入银联确认。
     *
     * @param amount 扫码明细金额（元）
     * @throws BusinessException 金额为空或不大于 0
     */
    private static void validateScanLineAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                "扫码支付金额须大于0");
        }
    }

    /**
     * 校验银联回传交易金额（分）相对 HIS 扫码行金额（元）合法；回传缺失时跳过。
     *
     * <p>POS 实付可小于院内下单（常见为医保账户余额已由 2207 扣除）；实付写入 BPC {@code paid_amt_fen}，
     * settle 仅拒绝大于下单。院内扫码行改记实付，差额不再记优惠。</p>
     *
     * @param clientScanResult Win 被扫回传
     * @param amountYuan       待结算扫码行金额（元）
     * @throws BusinessException 回传分额无效或大于院内下单分额
     */
    private static void validateTxnAmtFen(ClientScanResultDto clientScanResult, BigDecimal amountYuan) {
        Long txnAmtFen = clientScanResult.getTxnAmtFen();
        if (txnAmtFen == null) {
            return;
        }
        // Gate：实付分须为正；医保账户已扣时允许实付 < 下单，禁止实付 > 下单。
        if (txnAmtFen <= 0L) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                "银联回传金额无效");
        }
        long expectedFen = amountYuan.multiply(BigDecimal.valueOf(100L)).setScale(0, RoundingMode.HALF_UP).longValue();
        if (txnAmtFen > expectedFen) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                "扫码支付金额与银联回传不一致");
        }
    }
}
