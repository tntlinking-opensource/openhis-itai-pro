package com.openhis.whale.unionpay.bpc.contracts.client;

import com.openhis.whale.unionpay.bpc.contracts.command.ApplyClientRefundCommand;
import com.openhis.whale.unionpay.bpc.contracts.command.ApplyClientScanCommand;
import com.openhis.whale.unionpay.bpc.contracts.command.PrepareConsumerScanCommand;
import com.openhis.whale.unionpay.bpc.contracts.command.QueryBpcOrderCommand;
import com.openhis.whale.unionpay.bpc.contracts.result.ApplyClientRefundResult;
import com.openhis.whale.unionpay.bpc.contracts.result.ApplyClientScanResult;
import com.openhis.whale.unionpay.bpc.contracts.result.PrepareConsumerScanResult;
import com.openhis.whale.unionpay.bpc.contracts.result.QueryBpcOrderResult;

/**
 * 银联 BPC 对外 Client（adapter 经本接口委托 application 落库）。
 */
public interface UnionPayBpcPaymentClient {

    PrepareConsumerScanResult prepareConsumerScan(PrepareConsumerScanCommand command);

    ApplyClientScanResult applyClientScanResult(ApplyClientScanCommand command);

    ApplyClientRefundResult applyClientRefundResult(ApplyClientRefundCommand command);

    QueryBpcOrderResult queryOrder(QueryBpcOrderCommand command);
}
