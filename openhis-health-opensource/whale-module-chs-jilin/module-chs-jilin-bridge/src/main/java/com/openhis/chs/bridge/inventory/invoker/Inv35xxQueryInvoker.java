/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inventory.invoker;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;

import com.openhis.chs.bridge.inventory.support.InvPlatformSupport;
import com.openhis.chs.bridge.inventory.support.InvPlatformSupport.InvContractContext;
import com.openhis.chs.domain.inventory.queries.QueryPlatformInvQuery;
import com.openhis.chs.domain.inventory.result.PlatformInvQueryResult;
import com.openhis.chs.domain.share.constants.ChsErrorCodes;
import com.openhis.chs.gateway.client.InsuranceGatewayClient;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.gateway.scheme.inventory.assembler.InvPlatformInputAssembler;
import com.openhis.chs.gateway.scheme.inventory.parser.InvPlatformOutputParser;
import com.openhis.chs.gateway.scheme.inventory.model.InvPlatformQueryInput;
import com.openhis.chs.gateway.support.ChsPlatformResponseSupport;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 【3508–3513】平台进销存查询 gateway 出站（只读，不落库）。 */
@Component
@RequiredArgsConstructor
public class Inv35xxQueryInvoker implements com.openhis.chs.domain.inventory.access.Inv35xxQueryInvoker {

    private final InsuranceGatewayClient gatewayClient;
    private final InvPlatformSupport invPlatformSupport;

    public PlatformInvQueryResult invoke(String infno, QueryPlatformInvQuery query) {
        invPlatformSupport.assertPlatformQueryRequest(infno, query);
        InvContractContext ctx = invPlatformSupport.resolveContract(query.getContractBusNo(), query.getContractId());
        if (isEmpty(ctx.fixmedinsCode())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "契约未配置 fixmedins_code");
        }
        InvPlatformQueryInput input = InvPlatformInputAssembler.assemblePlatformQuery(query, ctx.fixmedinsCode());
        // 异步 3511（退药组装）带 query.operatorId；运维台同步查询走登录态
        ResultBody body = gatewayClient.invoke(
            ctx.busNo(),
            infno,
            input,
            invPlatformSupport.buildInvokeContext(query.getOperatorId(), query.getOperatorName()));
        ChsPlatformResponseSupport.requireSuccess(body, infno);
        return new PlatformInvQueryResult()
            .setInfno(infno)
            .setRows(InvPlatformOutputParser.parseQueryRows(body));
    }

    public PlatformInvQueryResult query3508(QueryPlatformInvQuery query) {
        return invoke(ChsInfNo.INVENTORY_STOCK_QUERY, query);
    }

    public PlatformInvQueryResult query3509(QueryPlatformInvQuery query) {
        return invoke(ChsInfNo.INVENTORY_CHANGE_QUERY, query);
    }

    public PlatformInvQueryResult query3510(QueryPlatformInvQuery query) {
        return invoke(ChsInfNo.INVENTORY_PURCHASE_QUERY, query);
    }

    public PlatformInvQueryResult query3511(QueryPlatformInvQuery query) {
        return invoke(ChsInfNo.INVENTORY_SALE_QUERY, query);
    }

    public PlatformInvQueryResult query3512(QueryPlatformInvQuery query) {
        return invoke(ChsInfNo.INVENTORY_INBOUND_TRACE_QUERY, query);
    }

    public PlatformInvQueryResult query3513(QueryPlatformInvQuery query) {
        return invoke(ChsInfNo.INVENTORY_SALE_TRACE_QUERY, query);
    }
}
