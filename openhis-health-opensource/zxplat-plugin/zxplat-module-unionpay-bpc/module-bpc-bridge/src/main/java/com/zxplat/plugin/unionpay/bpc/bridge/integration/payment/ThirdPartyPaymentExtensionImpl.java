/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.unionpay.bpc.bridge.integration.payment;


import com.zxplat.domain.infrastructure.integration.payment.ThirdPartyPaymentExtension;
import com.zxplat.domain.share.enums.PaymentChannel;
import com.zxplat.domain.infrastructure.integration.payment.ExtensionIn.ThirdPartyQueryExtensionIn;
import com.zxplat.domain.infrastructure.integration.payment.ExtensionIn.ThirdPartyRefundConfirmExtensionIn;
import com.zxplat.domain.infrastructure.integration.payment.ExtensionIn.ThirdPartyScanConfirmExtensionIn;
import com.zxplat.domain.infrastructure.integration.payment.ExtensionOut.ThirdPartyQueryExtensionOut;
import com.zxplat.domain.infrastructure.integration.payment.ExtensionOut.ThirdPartyRefundExtensionOut;
import com.zxplat.domain.infrastructure.integration.payment.ExtensionOut.ThirdPartyScanExtensionOut;
import com.zxplat.plugin.unionpay.bpc.bridge.settle.workflow.UnionPaySettleOrchestrator;
import com.zxplat.plugin.unionpay.bpc.domain.refund.command.ApplyClientRefundCommand;
import com.zxplat.plugin.unionpay.bpc.domain.scan.command.ApplyClientScanCommand;
import com.zxplat.plugin.unionpay.bpc.domain.query.command.QueryBpcOrderCommand;
import com.zxplat.plugin.unionpay.bpc.domain.refund.result.ApplyClientRefundResult;
import com.zxplat.plugin.unionpay.bpc.domain.scan.result.ApplyClientScanResult;
import com.zxplat.plugin.unionpay.bpc.domain.query.result.QueryBpcOrderResult;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** {@link ThirdPartyPaymentExtension} 实现（委托 {@link UnionPaySettleOrchestrator}）。 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(name = "zxplat.modules.unionpay-bpc.enabled", havingValue = "true", matchIfMissing = true)
public class ThirdPartyPaymentExtensionImpl implements ThirdPartyPaymentExtension {

    private final UnionPaySettleOrchestrator settleOrchestrator;

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean supportsChannel(String channelCode) {
        return PaymentChannel.SCAN_PAY.getCode().equals(StringUtils.trimWhitespace(channelCode));
    }

    @Override
    public boolean hasSuccessfulConsumerScanOrder(Long paymentId) {
        return settleOrchestrator.hasSuccessfulConsumerScanOrder(paymentId);
    }

    @Override
    public ThirdPartyScanExtensionOut applyClientScanResult(ThirdPartyScanConfirmExtensionIn command) {
        ApplyClientScanResult result = settleOrchestrator.applyClientScanResult(
                new ApplyClientScanCommand()
                        .setPaymentId(command.getPaymentId())
                        .setDetailIndex(command.getDetailIndex())
                        .setAmount(command.getAmount())
                        .setMerTradeNo(command.getMerTradeNo())
                        .setTradeNo(command.getTradeNo())
                        .setPayType(command.getPayType())
                        .setRespCode(command.getRespCode())
                        .setRespMsg(command.getRespMsg())
                        .setTxnAmtFen(command.getTxnAmtFen())
                        .setOperatorId(command.getOperatorId())
                        .setOrgId(command.getOrgId()));
        return new ThirdPartyScanExtensionOut()
                .setSuccess(result.isSuccess())
                .setThirdPartyOrderId(result.getOrderId())
                .setTradeNo(result.getTradeNo())
                .setPayType(result.getPayType())
                .setRespMsg(result.getRespMsg())
                .setChannelCode(result.getChannelCode());
    }

    @Override
    public ThirdPartyRefundExtensionOut applyClientRefundResult(ThirdPartyRefundConfirmExtensionIn command) {
        ApplyClientRefundResult result = settleOrchestrator.applyClientRefundResult(
                new ApplyClientRefundCommand()
                        .setPaymentId(command.getPaymentId())
                        .setMerTradeNo(command.getMerTradeNo())
                        .setTradeNo(command.getTradeNo())
                        .setRefundMerTradeNo(command.getRefundMerTradeNo())
                        .setRefundAmount(command.getRefundAmount())
                        .setRespCode(command.getRespCode())
                        .setRespMsg(command.getRespMsg())
                        .setOperatorId(command.getOperatorId()));
        return new ThirdPartyRefundExtensionOut()
                .setSuccess(result.isSuccess())
                .setThirdPartyOrderId(result.getOrderId())
                .setRespMsg(result.getRespMsg());
    }

    @Override
    public ThirdPartyQueryExtensionOut queryOrder(ThirdPartyQueryExtensionIn command) {
        QueryBpcOrderResult result = settleOrchestrator.queryOrder(
                new QueryBpcOrderCommand()
                        .setMerTradeNo(command.getMerTradeNo())
                        .setTradeNo(command.getTradeNo())
                        .setPaymentId(command.getPaymentId()));
        return new ThirdPartyQueryExtensionOut()
                .setSuccess(result.isSuccess())
                .setStatus(result.getStatus())
                .setTradeNo(result.getTradeNo())
                .setPayType(result.getPayType());
    }
}
