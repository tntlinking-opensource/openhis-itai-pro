/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.medicalinfo.invoker;

import com.openhis.chs.bridge.medicalinfo.support.MedicalInfoInvokeSupport;
import com.openhis.chs.domain.medicalinfo.access.Med5204QueryInvoker;
import com.openhis.chs.domain.medicalinfo.command.QueryFee5204Command;
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
 * 【5204】费用明细查询 Invoker：按就诊 ID 查询费用明细。
 *
 * <p>实现 domain {@link Med5204QueryInvoker}，供医保信息查询 UI 使用。
 * 契约解析与签到出站上下文由 {@link MedicalInfoInvokeSupport} 提供。</p>
 */
@Component
@RequiredArgsConstructor
public class Med5204Invoker implements Med5204QueryInvoker {

    private final InsuranceGatewayClient gatewayClient;
    private final MedicalInfoInvokeSupport medicalInfoInvokeSupport;

    /**
     * 按就诊 ID 查询平台费用明细。
     *
     * @param command psnNo、mdtrtId 必填
     * @return 费用明细行列表
     */
    @Override
    public MedicalInfoQueryResult invoke(QueryFee5204Command command) {
        medicalInfoInvokeSupport.validateFee5204(command);
        String busNo = medicalInfoInvokeSupport.resolveBusNo(command.getContractBusNo(), command.getContractId());
        ResultBody body = gatewayClient.invoke(
            busNo,
            ChsInfNo.FEE_DETAIL_QUERY,
            MedPlatformInputAssembler.assemble5204(command),
            medicalInfoInvokeSupport.buildInvokeContext());
        ChsPlatformResponseSupport.requireSuccess(body, ChsInfNo.FEE_DETAIL_QUERY);
        return MedInfoListOutputParser.parse5204(body);
    }
}
