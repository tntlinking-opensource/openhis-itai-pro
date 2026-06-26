/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.payment;

import com.openhis.health.domain.share.enums.PaymentChannel;
import com.openhis.health.integration.payment.command.ThirdPartyQueryCommand;
import com.openhis.health.integration.payment.command.ThirdPartyRefundConfirmCommand;
import com.openhis.health.integration.payment.command.ThirdPartyScanConfirmCommand;
import com.openhis.health.integration.payment.result.ThirdPartyQueryResult;
import com.openhis.health.integration.payment.result.ThirdPartyRefundResult;
import com.openhis.health.integration.payment.result.ThirdPartyScanResult;
import com.openhis.whale.unionpay.bpc.contracts.client.UnionPayBpcPaymentClient;
import com.openhis.whale.unionpay.bpc.contracts.command.ApplyClientRefundCommand;
import com.openhis.whale.unionpay.bpc.contracts.command.ApplyClientScanCommand;
import com.openhis.whale.unionpay.bpc.contracts.command.QueryBpcOrderCommand;
import com.openhis.whale.unionpay.bpc.contracts.result.ApplyClientRefundResult;
import com.openhis.whale.unionpay.bpc.contracts.result.ApplyClientScanResult;
import com.openhis.whale.unionpay.bpc.contracts.result.QueryBpcOrderResult;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** {@link ThirdPartyPaymentExtension} 实现（委托 {@link UnionPayBpcPaymentClient}）。 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnBean(UnionPayBpcPaymentClient.class)
@ConditionalOnProperty(name = "openhis.modules.unionpay-bpc.enabled", havingValue = "true")
public class ThirdPartyPaymentExtensionImpl implements ThirdPartyPaymentExtension {

    private final UnionPayBpcPaymentClient unionPayBpcPaymentClient;

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean supportsChannel(String channelCode) {
        return PaymentChannel.SCAN_PAY.getCode().equals(StringUtils.trimWhitespace(channelCode));
    }

    @Override
    public ThirdPartyScanResult applyClientScanResult(ThirdPartyScanConfirmCommand command) {
        ApplyClientScanResult result = unionPayBpcPaymentClient.applyClientScanResult(
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
        return new ThirdPartyScanResult()
                .setSuccess(result.isSuccess())
                .setThirdPartyOrderId(result.getOrderId())
                .setTradeNo(result.getTradeNo())
                .setPayType(result.getPayType())
                .setRespMsg(result.getRespMsg())
                .setChannelCode(result.getChannelCode());
    }

    @Override
    public ThirdPartyRefundResult applyClientRefundResult(ThirdPartyRefundConfirmCommand command) {
        ApplyClientRefundResult result = unionPayBpcPaymentClient.applyClientRefundResult(
                new ApplyClientRefundCommand()
                        .setPaymentId(command.getPaymentId())
                        .setMerTradeNo(command.getMerTradeNo())
                        .setTradeNo(command.getTradeNo())
                        .setRefundMerTradeNo(command.getRefundMerTradeNo())
                        .setRefundAmount(command.getRefundAmount())
                        .setRespCode(command.getRespCode())
                        .setRespMsg(command.getRespMsg())
                        .setOperatorId(command.getOperatorId()));
        return new ThirdPartyRefundResult()
                .setSuccess(result.isSuccess())
                .setThirdPartyOrderId(result.getOrderId())
                .setRespMsg(result.getRespMsg());
    }

    @Override
    public ThirdPartyQueryResult queryOrder(ThirdPartyQueryCommand command) {
        QueryBpcOrderResult result = unionPayBpcPaymentClient.queryOrder(
                new QueryBpcOrderCommand()
                        .setMerTradeNo(command.getMerTradeNo())
                        .setTradeNo(command.getTradeNo())
                        .setPaymentId(command.getPaymentId()));
        return new ThirdPartyQueryResult()
                .setSuccess(result.isSuccess())
                .setStatus(result.getStatus())
                .setTradeNo(result.getTradeNo())
                .setPayType(result.getPayType());
    }
}
