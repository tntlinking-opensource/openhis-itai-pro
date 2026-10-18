/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.reconciliation.invoker;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;

import com.zxplat.plugin.chs.bridge.reconciliation.support.ReconPlatformSupport;
import com.zxplat.plugin.chs.bridge.reconciliation.support.ReconPlatformSupport.ReconContractContext;
import com.zxplat.plugin.chs.domain.reconciliation.command.QueryCrossProvinceRecon3209ACommand;
import com.zxplat.plugin.chs.domain.reconciliation.result.CrossProvinceReconRowResult;
import com.zxplat.plugin.chs.domain.share.constants.ChsErrorCodes;
import com.zxplat.plugin.chs.gateway.client.InsuranceGatewayClient;
import com.zxplat.plugin.chs.gateway.client.ResultBody;
import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.plugin.chs.gateway.scheme.reconciliation.assembler.ReconPlatformInputAssembler;
import com.zxplat.plugin.chs.gateway.scheme.reconciliation.parser.ReconPlatformOutputParser;
import com.zxplat.plugin.chs.gateway.support.ChsPlatformResponseSupport;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 【3209A】跨省异地对账失败查询 Invoker：gateway 出站（只读，不落库）。
 * <p>实现 domain {@link com.zxplat.plugin.chs.domain.reconciliation.access.Recon3209AInvoker}。</p>
 */
@Component
@RequiredArgsConstructor
public class Recon3209AInvoker implements com.zxplat.plugin.chs.domain.reconciliation.access.Recon3209AInvoker {

    private final InsuranceGatewayClient gatewayClient;
    private final ReconPlatformSupport reconPlatformSupport;

    @Override
    public List<CrossProvinceReconRowResult> invoke(QueryCrossProvinceRecon3209ACommand command) {
        QueryCrossProvinceRecon3209ACommand safe = command == null ? new QueryCrossProvinceRecon3209ACommand() : command;
        if (isEmpty(safe.getClrOptins())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "清算机构不能为空");
        }
        ReconContractContext ctx = reconPlatformSupport.resolveContract(safe.getContractBusNo(), safe.getContractId());
        ResultBody body = gatewayClient.invoke(
            ctx.busNo(),
            ChsInfNo.CLEAR_CROSS_PROVINCE_RECON_JILIN,
            ReconPlatformInputAssembler.assemble3209A(safe),
            reconPlatformSupport.buildInvokeContext());
        ChsPlatformResponseSupport.requireSuccess(body, ChsInfNo.CLEAR_CROSS_PROVINCE_RECON_JILIN);
        return ReconPlatformOutputParser.parse3209A(body);
    }
}
