/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.medcas.invoker;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.alibaba.fastjson2.JSON;
import com.openhis.chs.gateway.client.InsuranceGatewayClient;
import com.openhis.chs.gateway.client.InvokeContext;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.health.infrastructure.integration.insurance.medcas.ExtensionIn.MedcasHomepageUploadExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.medcas.ExtensionOut.MedcasUploadExtensionOut;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 【4401】病案首页上传 Invoker：将住院病案首页 JSON 提交至医保平台。
 *
 * <p>入参 {@code inputJson} 由上游 Extension 组装完整 4401 报文；
 * 本类仅负责解析 JSON、构建出站上下文并透传平台响应。</p>
 */
@Component
@RequiredArgsConstructor
public class Med4401Invoker {

    private final InsuranceGatewayClient gatewayClient;

    /**
     * 上传病案首页至医保平台。
     *
     * @param command contractBusNo、4401 报文 JSON 及 operatorId
     * @return 平台 infcode、errMsg 及 output 透传结果
     */
    public MedcasUploadExtensionOut invoke(MedcasHomepageUploadExtensionIn command) {
        Object input = JSON.parse(command.getInputJson());
        String operatorId = null;
        if (command.getOperatorId() != null) {
            operatorId = String.valueOf(command.getOperatorId());
        }
        InvokeContext context = InvokeContext.builder()
            .operatorId(operatorId)
            .build();
        ResultBody body = gatewayClient.invoke(
            command.getContractBusNo(), ChsInfNo.MEDCAS_HOMEPAGE, input, context);
        MedcasUploadExtensionOut result = new MedcasUploadExtensionOut();
        if (body == null) {
            result.setSuccess(false);
            result.setErrMsg("医保平台无返回");
            return result;
        }
        result.setInfcode(body.getInfcode());
        result.setErrMsg(body.getErrMsg());
        result.setWarnMsg(body.getWarnMsg());
        result.setOutput(body.getOutput());
        result.setSuccess("0".equals(trimToNull(body.getInfcode())));
        return result;
    }
}
