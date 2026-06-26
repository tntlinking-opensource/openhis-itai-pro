package com.openhis.whale.unionpay.bpc.application.client;

import com.openhis.whale.unionpay.bpc.application.scan.UnionPayBpcScanApplicationService;
import com.openhis.whale.unionpay.bpc.contracts.client.UnionPayBpcPaymentClient;
import com.openhis.whale.unionpay.bpc.contracts.command.ApplyClientRefundCommand;
import com.openhis.whale.unionpay.bpc.contracts.command.ApplyClientScanCommand;
import com.openhis.whale.unionpay.bpc.contracts.command.PrepareConsumerScanCommand;
import com.openhis.whale.unionpay.bpc.contracts.command.QueryBpcOrderCommand;
import com.openhis.whale.unionpay.bpc.contracts.result.ApplyClientRefundResult;
import com.openhis.whale.unionpay.bpc.contracts.result.ApplyClientScanResult;
import com.openhis.whale.unionpay.bpc.contracts.result.PrepareConsumerScanResult;
import com.openhis.whale.unionpay.bpc.contracts.result.QueryBpcOrderResult;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * {@link UnionPayBpcPaymentClient} 的 application 层实现。
 */
@Component
@Primary
@RequiredArgsConstructor
public class UnionPayBpcPaymentClientImpl implements UnionPayBpcPaymentClient {

    private final UnionPayBpcScanApplicationService scanApplicationService;

    @Override
    public PrepareConsumerScanResult prepareConsumerScan(PrepareConsumerScanCommand command) {
        return scanApplicationService.prepareConsumerScan(command);
    }

    @Override
    public ApplyClientScanResult applyClientScanResult(ApplyClientScanCommand command) {
        return scanApplicationService.applyClientScanResult(command);
    }

    @Override
    public ApplyClientRefundResult applyClientRefundResult(ApplyClientRefundCommand command) {
        return scanApplicationService.applyClientRefundResult(command);
    }

    @Override
    public QueryBpcOrderResult queryOrder(QueryBpcOrderCommand command) {
        return scanApplicationService.queryOrder(command);
    }
}
