/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.unionpay.bpc.application.order.service;

import com.openhis.whale.unionpay.bpc.application.order.response.BpcOrderSummaryResponse;
import com.openhis.whale.unionpay.bpc.domain.service.BpcSettleDomainService;
import com.openhis.whale.unionpay.bpc.domain.refund.command.ApplyClientRefundCommand;
import com.openhis.whale.unionpay.bpc.domain.scan.command.ApplyClientScanCommand;
import com.openhis.whale.unionpay.bpc.domain.query.command.PageBpcOrdersCommand;
import com.openhis.whale.unionpay.bpc.domain.scan.command.PrepareConsumerScanCommand;
import com.openhis.whale.unionpay.bpc.domain.query.command.QueryBpcOrderCommand;
import com.openhis.whale.unionpay.bpc.domain.query.command.RetrySettleFromBpcOrderCommand;
import com.openhis.whale.unionpay.bpc.domain.refund.result.ApplyClientRefundResult;
import com.openhis.whale.unionpay.bpc.domain.scan.result.ApplyClientScanResult;
import com.openhis.whale.unionpay.bpc.domain.query.result.PageBpcOrdersResult;
import com.openhis.whale.unionpay.bpc.domain.scan.result.PrepareConsumerScanResult;
import com.openhis.whale.unionpay.bpc.domain.query.result.QueryBpcOrderResult;
import com.openhis.whale.unionpay.bpc.domain.query.result.RetrySettleFromBpcOrderResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 银联 BPC 对外 AppService（POST /api/app/unionPayBpc/{methodName}）。
 */
@Service
@Validated
@RequiredArgsConstructor
public class UnionPayBpcAppService {

    private final BpcSettleDomainService settleDomainService;

    private final UnionPayBpcOrderQueryService orderQueryService;

    public PrepareConsumerScanResult prepareConsumerScan(PrepareConsumerScanCommand command) {
        return settleDomainService.prepareConsumerScan(command);
    }

    public ApplyClientScanResult applyClientScanResult(ApplyClientScanCommand command) {
        return settleDomainService.applyClientScanResult(command);
    }

    public ApplyClientRefundResult applyClientRefundResult(ApplyClientRefundCommand command) {
        return settleDomainService.applyClientRefundResult(command);
    }

    public QueryBpcOrderResult queryOrder(QueryBpcOrderCommand command) {
        return settleDomainService.queryOrder(command);
    }

    /** 财务银联订单分页查询（P1 查单页）。 */
    public PageBpcOrdersResult pageOrders(PageBpcOrdersCommand command) {
        return orderQueryService.pageOrders(command);
    }

    public List<BpcOrderSummaryResponse> getOrderByPayment(Long paymentId) {
        return settleDomainService.getOrdersByPayment(paymentId).stream()
                .map(BpcOrderSummaryResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 半成功补结算资格校验：BPC 已 SUCCESS 时允许 health 侧重传 clientScanResult settle。
     *
     * @param command paymentId + merTradeNo
     * @return 是否可补结算
     */
    public RetrySettleFromBpcOrderResult retrySettleFromBpcOrder(RetrySettleFromBpcOrderCommand command) {
        if (command == null || command.getPaymentId() == null
            || command.getMerTradeNo() == null || command.getMerTradeNo().isBlank()) {
            return new RetrySettleFromBpcOrderResult()
                .setEligible(false)
                .setMessage("paymentId 与 merTradeNo 不能为空");
        }
        boolean eligible = settleDomainService.isRetrySettleEligible(
            command.getPaymentId(),
            command.getMerTradeNo());
        return new RetrySettleFromBpcOrderResult()
            .setEligible(eligible)
            .setMessage(eligible
                ? "可补结算，请重传完整 clientScanResult"
                : "未找到成功的 BPC 订单，不可补结算");
    }
}
