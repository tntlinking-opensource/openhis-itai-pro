/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.unionpay.bpc.bridge.query.invoker;

import com.openhis.whale.unionpay.bpc.domain.query.access.BpcHttpsQueryAccess;
import com.openhis.whale.unionpay.bpc.domain.query.command.QueryBpcOrderCommand;
import com.openhis.whale.unionpay.bpc.domain.query.result.QueryBpcOrderResult;
import com.openhis.whale.unionpay.bpc.gateway.client.BpcGatewayClient;
import com.openhis.whale.unionpay.bpc.gateway.scheme.query.assembler.BpcQueryInputAssembler;
import com.openhis.whale.unionpay.bpc.gateway.scheme.query.parser.BpcQueryOutputParser;
import com.openhis.whale.unionpay.bpc.gateway.session.BpcContractConfigLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 银联 BPC 查单出站 Invoker（P1+ HTTPS 渠道占位）。
 */
@Component
@RequiredArgsConstructor
public class BpcQueryInvoker implements BpcHttpsQueryAccess {

    private static final String TRAN_CODE_QUERY = "query";

    private final BpcGatewayClient gatewayClient;

    @Override
    public QueryBpcOrderResult queryRemote(QueryBpcOrderCommand command) {
        String fixmedinsCode = BpcContractConfigLoader.resolveFixmedinsCode(null);
        if (fixmedinsCode == null) {
            return new QueryBpcOrderResult().setSuccess(false);
        }
        var request = BpcQueryInputAssembler.toRequest(command.getMerTradeNo(), command.getTradeNo());
        try {
            String rawJson = gatewayClient.invoke(fixmedinsCode, TRAN_CODE_QUERY, request.toString());
            var invoke = BpcQueryOutputParser.parse(rawJson);
            return new QueryBpcOrderResult().setSuccess(invoke.isSuccess());
        } catch (UnsupportedOperationException ex) {
            return new QueryBpcOrderResult().setSuccess(false);
        }
    }
}
