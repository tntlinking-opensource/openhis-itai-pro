/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.medicalinfo.invoker;

import com.openhis.chs.bridge.medicalinfo.support.MedicalInfoInvokeSupport;
import com.openhis.chs.domain.medicalinfo.access.Med5205QueryInvoker;
import com.openhis.chs.domain.medicalinfo.command.QueryOpspDrug5205Command;
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
 * 【5205】人员慢特病用药记录查询 Invoker：按时间范围查询慢特病用药记录。
 *
 * <p>实现 domain {@link Med5205QueryInvoker}，供医保信息查询 UI 使用。
 * 契约解析与签到出站上下文由 {@link MedicalInfoInvokeSupport} 提供。</p>
 */
@Component
@RequiredArgsConstructor
public class Med5205Invoker implements Med5205QueryInvoker {

    private final InsuranceGatewayClient gatewayClient;
    private final MedicalInfoInvokeSupport medicalInfoInvokeSupport;

    /**
     * 按人员编号与时间范围查询慢特病用药记录。
     *
     * @param command psnNo、begntime 必填；endtime 可选
     * @return 用药记录行列表
     */
    @Override
    public MedicalInfoQueryResult invoke(QueryOpspDrug5205Command command) {
        medicalInfoInvokeSupport.validateOpspDrug5205(command);
        String busNo = medicalInfoInvokeSupport.resolveBusNo(command.getContractBusNo(), command.getContractId());
        ResultBody body = gatewayClient.invoke(
            busNo,
            ChsInfNo.OPSP_DRUG_QUERY,
            MedPlatformInputAssembler.assemble5205(command),
            medicalInfoInvokeSupport.buildInvokeContext(command));
        ChsPlatformResponseSupport.requireSuccess(body, ChsInfNo.OPSP_DRUG_QUERY);
        return MedInfoListOutputParser.parse5205(body);
    }
}
