/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.medicalinfo.invoker;

import com.zxplat.plugin.chs.bridge.medicalinfo.support.MedicalInfoInvokeSupport;
import com.zxplat.plugin.chs.domain.medicalinfo.access.Med5301QueryInvoker;
import com.zxplat.plugin.chs.domain.medicalinfo.command.QueryOpspFiling5301Command;
import com.zxplat.plugin.chs.domain.medicalinfo.result.MedicalInfoQueryResult;
import com.zxplat.plugin.chs.gateway.client.InsuranceGatewayClient;
import com.zxplat.plugin.chs.gateway.client.ResultBody;
import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.plugin.chs.gateway.scheme.medicalinfo.parser.MedInfoListOutputParser;
import com.zxplat.plugin.chs.gateway.scheme.medicalinfo.assembler.MedPlatformInputAssembler;
import com.zxplat.plugin.chs.gateway.support.ChsPlatformResponseSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 【5301】人员慢特病备案查询 Invoker：查询参保人已备案的门诊慢特病病种信息。
 *
 * <p>实现 domain {@link Med5301QueryInvoker}，供医保信息查询 UI 与挂号待遇 Extension 共用。
 * 契约解析与签到出站上下文由 {@link MedicalInfoInvokeSupport} 提供。</p>
 */
@Component
@RequiredArgsConstructor
public class Med5301Invoker implements Med5301QueryInvoker {

    private final InsuranceGatewayClient gatewayClient;
    private final MedicalInfoInvokeSupport medicalInfoInvokeSupport;

    /**
     * 按人员编号查询慢特病备案记录。
     *
     * @param command psnNo 必填
     * @return 慢特病备案行列表
     */
    @Override
    public MedicalInfoQueryResult invoke(QueryOpspFiling5301Command command) {
        medicalInfoInvokeSupport.validateOpspFiling5301(command);
        String busNo = medicalInfoInvokeSupport.resolveBusNo(command.getContractBusNo(), command.getContractId());
        ResultBody body = gatewayClient.invoke(
            busNo,
            ChsInfNo.PERSON_OPSP_FILING_QUERY,
            MedPlatformInputAssembler.assemble5301(command),
            medicalInfoInvokeSupport.buildInvokeContext(command));
        ChsPlatformResponseSupport.requireSuccess(body, ChsInfNo.PERSON_OPSP_FILING_QUERY);
        return MedInfoListOutputParser.parse5301(body);
    }
}
