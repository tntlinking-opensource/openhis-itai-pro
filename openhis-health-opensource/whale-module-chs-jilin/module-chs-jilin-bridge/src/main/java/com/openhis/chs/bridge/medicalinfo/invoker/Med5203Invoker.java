/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.medicalinfo.invoker;

import com.openhis.chs.bridge.medicalinfo.support.MedicalInfoInvokeSupport;
import com.openhis.chs.domain.medicalinfo.access.Med5203QueryInvoker;
import com.openhis.chs.domain.medicalinfo.command.QuerySetl5203Command;
import com.openhis.chs.domain.medicalinfo.result.Setl5203Result;
import com.openhis.chs.gateway.client.InsuranceGatewayClient;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.gateway.scheme.medicalinfo.parser.Med5203OutputParser;
import com.openhis.chs.gateway.scheme.medicalinfo.assembler.MedPlatformInputAssembler;
import com.openhis.chs.gateway.support.ChsPlatformResponseSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 【5203】结算信息查询 Invoker：按就诊 ID 与结算 ID 查询结算详情。
 *
 * <p>实现 domain {@link Med5203QueryInvoker}，供医保信息查询 UI 使用。
 * 契约解析与签到出站上下文由 {@link MedicalInfoInvokeSupport} 提供。</p>
 */
@Component
@RequiredArgsConstructor
public class Med5203Invoker implements Med5203QueryInvoker {

    private final InsuranceGatewayClient gatewayClient;
    private final MedicalInfoInvokeSupport medicalInfoInvokeSupport;

    /**
     * 按就诊 ID 与结算 ID 查询平台结算信息。
     *
     * @param command psnNo、mdtrtId、setlId 必填
     * @return 结算详情
     */
    @Override
    public Setl5203Result invoke(QuerySetl5203Command command) {
        medicalInfoInvokeSupport.validateSetl5203(command);
        String busNo = medicalInfoInvokeSupport.resolveBusNo(command.getContractBusNo(), command.getContractId());
        ResultBody body = gatewayClient.invoke(
            busNo,
            ChsInfNo.SETL_QUERY,
            MedPlatformInputAssembler.assemble5203(command),
            medicalInfoInvokeSupport.buildInvokeContext(command));
        ChsPlatformResponseSupport.requireSuccess(body, ChsInfNo.SETL_QUERY);
        return Med5203OutputParser.parse5203(body, ChsInfNo.SETL_QUERY);
    }
}
