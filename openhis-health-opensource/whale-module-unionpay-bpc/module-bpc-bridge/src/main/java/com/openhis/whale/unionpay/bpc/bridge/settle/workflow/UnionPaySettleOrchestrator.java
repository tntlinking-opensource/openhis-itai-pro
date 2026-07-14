/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.unionpay.bpc.bridge.settle.workflow;

import com.openhis.whale.unionpay.bpc.domain.refund.command.ApplyClientRefundCommand;
import com.openhis.whale.unionpay.bpc.domain.scan.command.ApplyClientScanCommand;
import com.openhis.whale.unionpay.bpc.domain.scan.command.PrepareConsumerScanCommand;
import com.openhis.whale.unionpay.bpc.domain.query.command.QueryBpcOrderCommand;
import com.openhis.whale.unionpay.bpc.domain.refund.result.ApplyClientRefundResult;
import com.openhis.whale.unionpay.bpc.domain.scan.result.ApplyClientScanResult;
import com.openhis.whale.unionpay.bpc.domain.scan.result.PrepareConsumerScanResult;
import com.openhis.whale.unionpay.bpc.domain.query.result.QueryBpcOrderResult;
import com.openhis.whale.unionpay.bpc.domain.service.BpcSettleDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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

    public ApplyClientRefundResult applyClientRefundResult(ApplyClientRefundCommand command) {
        return settleDomainService.applyClientRefundResult(command);
    }

    public QueryBpcOrderResult queryOrder(QueryBpcOrderCommand command) {
        return settleDomainService.queryOrder(command);
    }
}
