/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.medcas.invoker;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.plugin.chs.gateway.client.InsuranceGatewayClient;
import com.zxplat.plugin.chs.gateway.client.InvokeContext;
import com.zxplat.plugin.chs.gateway.client.ResultBody;
import com.zxplat.domain.infrastructure.integration.insurance.medcas.ExtensionIn.MedcasSettleListUploadExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.medcas.ExtensionOut.MedcasUploadExtensionOut;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 【4101A】结算清单上传 Invoker。
 */
@Component
@RequiredArgsConstructor
public class Med4101AInvoker {

    private final InsuranceGatewayClient gatewayClient;

    /**
     * 上传结算清单至医保平台。
     *
     * @param command contractBusNo、4101A JSON 及 operatorId
     * @return 平台结果；成功时尝试解析 setl_list_id 写入 output
     */
    public MedcasUploadExtensionOut invoke(MedcasSettleListUploadExtensionIn command) {
        Object input = JSON.parse(command.getInputJson());
        String operatorId = null;
        if (command.getOperatorId() != null) {
            operatorId = String.valueOf(command.getOperatorId());
        }
        InvokeContext context = InvokeContext.builder()
            .operatorId(operatorId)
            .build();
        ResultBody body = gatewayClient.invoke(
            command.getContractBusNo(), ChsInfNo.SETTLE_LIST_UPLOAD, input, context);
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

    /**
     * 从平台 output 解析 setl_list_id。
     *
     * @param output 平台 output 原文
     * @return 清单流水号，解析失败时 null
     */
    public static String parseSetlListId(String output) {
        if (trimToNull(output) == null) {
            return null;
        }
        try {
            JSONObject root = JSON.parseObject(output);
            if (root == null) {
                return null;
            }
            // 常见形态：{data:{setl_list_id}} 或 {setl_list_id}
            String direct = trimToNull(root.getString("setl_list_id"));
            if (direct != null) {
                return direct;
            }
            Object data = root.get("data");
            if (data instanceof JSONObject dataObj) {
                return trimToNull(dataObj.getString("setl_list_id"));
            }
            if (data instanceof String dataStr) {
                JSONObject nested = JSON.parseObject(dataStr);
                if (nested != null) {
                    return trimToNull(nested.getString("setl_list_id"));
                }
            }
            return null;
        } catch (Exception ex) {
            return null;
        }
    }
}
