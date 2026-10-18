/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.medicalinfo.invoker;

import com.zxplat.plugin.chs.bridge.medicalinfo.support.MedicalInfoInvokeSupport;
import com.zxplat.plugin.chs.domain.medicalinfo.access.Med5302AQueryInvoker;
import com.zxplat.plugin.chs.domain.medicalinfo.command.QueryOpspApproval5302ACommand;
import com.zxplat.plugin.chs.domain.medicalinfo.result.MedicalInfoQueryResult;
import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.plugin.chs.gateway.client.InsuranceGatewayClient;
import com.zxplat.plugin.chs.gateway.client.ResultBody;
import com.zxplat.plugin.chs.gateway.scheme.medicalinfo.assembler.MedPlatformInputAssembler;
import com.zxplat.plugin.chs.gateway.scheme.medicalinfo.parser.MedInfoListOutputParser;
import com.zxplat.plugin.chs.gateway.support.ChsPlatformResponseSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 【5302A】人员门诊慢特病审批查询 Invoker。
 *
 * <p>可通过 {@code med_type=140104/140201} 分查门诊慢病/门诊特病备案。</p>
 */
@Component
@RequiredArgsConstructor
public class Med5302AInvoker implements Med5302AQueryInvoker {

    private final InsuranceGatewayClient gatewayClient;
    private final MedicalInfoInvokeSupport medicalInfoInvokeSupport;

    @Override
    public MedicalInfoQueryResult invoke(QueryOpspApproval5302ACommand command) {
        medicalInfoInvokeSupport.validateOpspApproval5302A(command);
        String busNo = medicalInfoInvokeSupport.resolveBusNo(command.getContractBusNo(), command.getContractId());
        ResultBody body = gatewayClient.invoke(
            busNo,
            ChsInfNo.PERSON_OPSP_APPROVAL_QUERY,
            MedPlatformInputAssembler.assemble5302A(command),
            medicalInfoInvokeSupport.buildInvokeContext(command));
        ChsPlatformResponseSupport.requireSuccess(body, ChsInfNo.PERSON_OPSP_APPROVAL_QUERY);
        return MedInfoListOutputParser.parse5302A(body);
    }
}
