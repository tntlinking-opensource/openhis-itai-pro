/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.medcas.invoker;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.gateway.client.InsuranceGatewayClient;
import com.openhis.chs.gateway.client.InvokeContext;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.health.infrastructure.integration.insurance.medcas.ExtensionIn.MedcasSettleListStatusExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.medcas.ExtensionOut.MedcasUploadExtensionOut;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 【4102】结算清单状态修改 Invoker。
 */
@Component
@RequiredArgsConstructor
public class Med4102Invoker {

    private final InsuranceGatewayClient gatewayClient;

    /**
     * 修改结算清单审核状态。
     *
     * @param command psn_no/setl_id/stas_type
     * @return 平台结果
     */
    public MedcasUploadExtensionOut invoke(MedcasSettleListStatusExtensionIn command) {
        JSONObject input = new JSONObject();
        input.put("psn_no", command.getPsnNo());
        input.put("setl_id", command.getSetlId());
        input.put("stas_type", command.getStasType());
        String operatorId = null;
        if (command.getOperatorId() != null) {
            operatorId = String.valueOf(command.getOperatorId());
        }
        InvokeContext context = InvokeContext.builder()
            .operatorId(operatorId)
            .build();
        ResultBody body = gatewayClient.invoke(
            command.getContractBusNo(), ChsInfNo.SETTLE_LIST_STATUS, input, context);
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
