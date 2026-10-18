/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.unionpay.bpc.bridge.settle.workflow;

import com.zxplat.plugin.unionpay.bpc.domain.order.entity.BpcOrder;
import com.zxplat.plugin.unionpay.bpc.domain.order.entity.BpcOrderStatus;
import com.zxplat.plugin.unionpay.bpc.domain.refund.command.ApplyClientRefundCommand;
import com.zxplat.plugin.unionpay.bpc.domain.scan.command.AbandonPendingScanCommand;
import com.zxplat.plugin.unionpay.bpc.domain.scan.command.ApplyClientScanCommand;
import com.zxplat.plugin.unionpay.bpc.domain.scan.command.PrepareConsumerScanCommand;
import com.zxplat.plugin.unionpay.bpc.domain.query.command.QueryBpcOrderCommand;
import com.zxplat.plugin.unionpay.bpc.domain.refund.result.ApplyClientRefundResult;
import com.zxplat.plugin.unionpay.bpc.domain.scan.result.AbandonPendingScanResult;
import com.zxplat.plugin.unionpay.bpc.domain.scan.result.ApplyClientScanResult;
import com.zxplat.plugin.unionpay.bpc.domain.scan.result.PrepareConsumerScanResult;
import com.zxplat.plugin.unionpay.bpc.domain.query.result.QueryBpcOrderResult;
import com.zxplat.plugin.unionpay.bpc.domain.service.BpcSettleDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 银联 BPC Extension 链编排（替代已废止的 {@code UnionPayBpcPaymentClient}）。
 */
@Component
@RequiredArgsConstructor
public class UnionPaySettleOrchestrator {

    private final BpcSettleDomainService settleDomainService;

    public PrepareConsumerScanResult prepareConsumerScan(PrepareConsumerScanCommand command) {
        return settleDomainService.prepareConsumerScan(command);
    }

    public ApplyClientScanResult applyClientScanResult(ApplyClientScanCommand command) {
        return settleDomainService.applyClientScanResult(command);
    }

    public AbandonPendingScanResult abandonPendingScan(AbandonPendingScanCommand command) {
        return settleDomainService.abandonPendingScan(command);
    }

    public ApplyClientRefundResult applyClientRefundResult(ApplyClientRefundCommand command) {
        return settleDomainService.applyClientRefundResult(command);
    }

    public QueryBpcOrderResult queryOrder(QueryBpcOrderCommand command) {
        return settleDomainService.queryOrder(command);
    }

    /**
     * 原支付是否仍有须 Win 原路退的被扫成功单（与前端 {@code findBpcSuccessOrder} 口径对齐）。
     *
     * @param paymentId HIS 支付单
     * @return 存在 SUCCESS 且含 tradeNo/merTradeNo 时为 true
     */
    public boolean hasSuccessfulConsumerScanOrder(Long paymentId) {
        if (paymentId == null) {
            return false;
        }
        List<BpcOrder> orders = settleDomainService.getOrdersByPayment(paymentId);
        for (BpcOrder order : orders) {
            if (order == null) {
                continue;
            }
            if (!BpcOrderStatus.SUCCESS.getCode().equals(order.getStatus())) {
                continue;
            }
            if (!StringUtils.hasText(order.getTradeNo()) || !StringUtils.hasText(order.getMerTradeNo())) {
                continue;
            }
            return true;
        }
        return false;
    }
}
