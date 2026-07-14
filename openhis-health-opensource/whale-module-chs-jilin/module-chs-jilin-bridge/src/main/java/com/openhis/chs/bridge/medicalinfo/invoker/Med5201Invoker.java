/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.medicalinfo.invoker;

import com.openhis.chs.bridge.medicalinfo.support.MedicalInfoInvokeSupport;
import com.openhis.chs.domain.medicalinfo.access.Med5201QueryInvoker;
import com.openhis.chs.domain.medicalinfo.command.QueryMdtrt5201Command;
import com.openhis.chs.domain.medicalinfo.result.MedicalInfoQueryResult;
import com.openhis.chs.gateway.client.InsuranceGatewayClient;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.gateway.scheme.medicalinfo.parser.MedInfoListOutputParser;
import com.openhis.chs.gateway.scheme.medicalinfo.assembler.MedPlatformInputAssembler;
import com.openhis.chs.gateway.support.ChsPlatformResponseSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 【5201】就诊信息查询 Invoker：按人员编号与时间范围查询就诊记录。
 *
 * <p>实现 domain {@link Med5201QueryInvoker}，供医保信息查询 UI 使用。
 * 契约解析与签到出站上下文由 {@link MedicalInfoInvokeSupport} 提供。</p>
 */
@Component
@RequiredArgsConstructor
public class Med5201Invoker implements Med5201QueryInvoker {

    private final InsuranceGatewayClient gatewayClient;
    private final MedicalInfoInvokeSupport medicalInfoInvokeSupport;

    /**
     * 按人员编号与时间范围查询平台就诊信息。
     *
     * @param command psnNo、begntime、endtime 必填
     * @return 就诊信息行列表
     */
    @Override
    public MedicalInfoQueryResult invoke(QueryMdtrt5201Command command) {
        medicalInfoInvokeSupport.validateMdtrt5201(command);
        String busNo = medicalInfoInvokeSupport.resolveBusNo(command.getContractBusNo(), command.getContractId());
        ResultBody body = gatewayClient.invoke(
            busNo,
            ChsInfNo.MDTRT_QUERY,
            MedPlatformInputAssembler.assemble5201(command),
            medicalInfoInvokeSupport.buildInvokeContext());
        ChsPlatformResponseSupport.requireSuccess(body, ChsInfNo.MDTRT_QUERY);
        return MedInfoListOutputParser.parse5201(body);
    }
}
