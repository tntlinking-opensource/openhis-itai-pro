/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.medcas.invoker;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.alibaba.fastjson2.JSONObject;
import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.plugin.chs.gateway.client.InsuranceGatewayClient;
import com.zxplat.plugin.chs.gateway.client.InvokeContext;
import com.zxplat.plugin.chs.gateway.client.ResultBody;
import com.zxplat.domain.infrastructure.integration.insurance.medcas.ExtensionIn.MedcasSettleListQueryExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.medcas.ExtensionOut.MedcasUploadExtensionOut;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 【4103】结算清单信息查询 Invoker。
 *
 * <p>报文形态与老 HIS / 规范一致：{@code input.data.{psn_no,setl_id}}。</p>
 */
@Component
@RequiredArgsConstructor
public class Med4103Invoker {

    private final InsuranceGatewayClient gatewayClient;

    /**
     * 查询平台结算清单详情。
     *
     * @param command psn_no / setl_id
     * @return 平台结果；成功时 output 含 setlinfo 等节点
     */
    public MedcasUploadExtensionOut invoke(MedcasSettleListQueryExtensionIn command) {
        // Pre：规范要求 input.data 包裹 psn_no/setl_id
        JSONObject data = new JSONObject();
        data.put("psn_no", command.getPsnNo());
        data.put("setl_id", command.getSetlId());
        JSONObject input = new JSONObject();
        input.put("data", data);
        String operatorId = null;
        // 操作员可空：部分网关场景允许无 opter
        if (command.getOperatorId() != null) {
            operatorId = String.valueOf(command.getOperatorId());
        }
        InvokeContext context = InvokeContext.builder()
            .operatorId(operatorId)
            .build();
        // Cmd：出站 4103
        ResultBody body = gatewayClient.invoke(
            command.getContractBusNo(), ChsInfNo.SETTLE_LIST_QUERY, input, context);
        MedcasUploadExtensionOut result = new MedcasUploadExtensionOut();
        // 网关无响应视为失败
        if (body == null) {
            result.setSuccess(false);
            result.setErrMsg("医保平台无返回");
            return result;
        }
        // Asm：映射平台摘要；infcode=0 为成功
        result.setInfcode(body.getInfcode());
        result.setErrMsg(body.getErrMsg());
        result.setWarnMsg(body.getWarnMsg());
        result.setOutput(body.getOutput());
        result.setSuccess("0".equals(trimToNull(body.getInfcode())));
        return result;
    }
}
