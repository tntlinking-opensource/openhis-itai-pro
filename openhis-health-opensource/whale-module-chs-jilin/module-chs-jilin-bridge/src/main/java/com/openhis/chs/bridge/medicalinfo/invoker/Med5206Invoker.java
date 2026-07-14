/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.medicalinfo.invoker;

import com.openhis.chs.bridge.medicalinfo.support.MedicalInfoInvokeSupport;
import com.openhis.chs.domain.medicalinfo.access.Med5206QueryInvoker;
import com.openhis.chs.domain.medicalinfo.command.QueryCum5206Command;
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
 * 【5206】人员累计信息查询 Invoker：查询参保人各基金年度/月度累计支出。
 *
 * <p>实现 domain {@link Med5206QueryInvoker}，供医保信息查询 UI 与挂号待遇 Extension 共用。
 * 契约解析与签到出站上下文由 {@link MedicalInfoInvokeSupport} 提供。</p>
 */
@Component
@RequiredArgsConstructor
public class Med5206Invoker implements Med5206QueryInvoker {

    private final InsuranceGatewayClient gatewayClient;
    private final MedicalInfoInvokeSupport medicalInfoInvokeSupport;

    /**
     * 按人员编号与可选累计年月查询平台累计信息。
     *
     * @param command psnNo 必填；cumYm 格式 yyyyMM（可空查全部）
     * @return 累计信息行列表
     */
    @Override
    public MedicalInfoQueryResult invoke(QueryCum5206Command command) {
        medicalInfoInvokeSupport.validateCum5206(command);
        String busNo = medicalInfoInvokeSupport.resolveBusNo(command.getContractBusNo(), command.getContractId());
        ResultBody body = gatewayClient.invoke(
            busNo,
            ChsInfNo.PERSON_CUM_QUERY,
            MedPlatformInputAssembler.assemble5206(command),
            medicalInfoInvokeSupport.buildInvokeContext());
        ChsPlatformResponseSupport.requireSuccess(body, ChsInfNo.PERSON_CUM_QUERY);
        return MedInfoListOutputParser.parse5206(body);
    }
}
