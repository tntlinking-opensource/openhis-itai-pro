/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.medicalinfo.invoker;

import com.zxplat.plugin.chs.bridge.medicalinfo.support.MedicalInfoInvokeSupport;
import com.zxplat.plugin.chs.domain.medicalinfo.access.Med5202QueryInvoker;
import com.zxplat.plugin.chs.domain.medicalinfo.command.QueryDise5202Command;
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
 * 【5202】诊断信息查询 Invoker：按就诊 ID 查询诊断记录。
 *
 * <p>实现 domain {@link Med5202QueryInvoker}，供医保信息查询 UI 使用。
 * 契约解析与签到出站上下文由 {@link MedicalInfoInvokeSupport} 提供。</p>
 */
@Component
@RequiredArgsConstructor
public class Med5202Invoker implements Med5202QueryInvoker {

    private final InsuranceGatewayClient gatewayClient;
    private final MedicalInfoInvokeSupport medicalInfoInvokeSupport;

    /**
     * 按就诊 ID 查询平台诊断信息。
     *
     * @param command psnNo、mdtrtId 必填
     * @return 诊断信息行列表
     */
    @Override
    public MedicalInfoQueryResult invoke(QueryDise5202Command command) {
        medicalInfoInvokeSupport.validateDise5202(command);
        String busNo = medicalInfoInvokeSupport.resolveBusNo(command.getContractBusNo(), command.getContractId());
        ResultBody body = gatewayClient.invoke(
            busNo,
            ChsInfNo.DISE_QUERY,
            MedPlatformInputAssembler.assemble5202(command),
            medicalInfoInvokeSupport.buildInvokeContext(command));
        ChsPlatformResponseSupport.requireSuccess(body, ChsInfNo.DISE_QUERY);
        return MedInfoListOutputParser.parse5202(body);
    }
}
