/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.foundation.platformdict.invoker;

import com.openhis.chs.bridge.foundation.platformdict.support.DictPlatformSupport;
import com.openhis.chs.domain.platformdict.command.UpsertPlatformDictRowCommand;
import com.openhis.chs.gateway.client.InsuranceGatewayClient;
import com.openhis.chs.gateway.client.InvokeContext;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.gateway.scheme.platformdict.assembler.Dict1901InputAssembler;
import com.openhis.chs.gateway.scheme.platformdict.parser.Dict1901OutputParser;
import com.openhis.chs.gateway.support.ChsPlatformResponseSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 【1901】单页字典拉取出站。
 * <p>调用网关 1901 接口获取一页字典行并解析为 upsert 命令。</p>
 */
@Component
@RequiredArgsConstructor
public class Dict1901PageInvoker implements com.openhis.chs.domain.platformdict.access.Dict1901PageInvoker {

    private final InsuranceGatewayClient gatewayClient;
    private final DictPlatformSupport dictPlatformSupport;

    @Override
    public List<UpsertPlatformDictRowCommand> fetchPage(
            String contractBusNo,
            String dictType,
            String queryAdmdvs,
            String parentValue,
            int pageNum,
            int pageSize,
            Long operatorId,
            String valiFlagKey) {
        InvokeContext invokeContext = dictPlatformSupport.buildInvokeContext(operatorId);
        InvokeContext pageContext = invokeContext.toBuilder()
            .insuplcAdmdvs(queryAdmdvs)
            .build();
        ResultBody body = gatewayClient.invoke(
            contractBusNo,
            ChsInfNo.DICT_QUERY,
            Dict1901InputAssembler.assemblePage(
                dictType, queryAdmdvs, parentValue, pageNum, pageSize, valiFlagKey),
            pageContext);
        ChsPlatformResponseSupport.requireSuccess(body, ChsInfNo.DICT_QUERY);
        return Dict1901OutputParser.parseRows(dictType, body);
    }
}
