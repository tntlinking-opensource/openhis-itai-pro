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
import com.zxplat.domain.infrastructure.integration.insurance.medcas.ExtensionIn.MedcasSettleListStatusExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.medcas.ExtensionOut.MedcasUploadExtensionOut;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 【4102】结算清单状态修改 Invoker。
 *
 * <p>报文对齐老 HIS {@code WinMessage.ybFundSettleBillCommit}：
 * {@code input.data.stast_info.{psn_no,setl_id,stas_type}}。
 * 扁平三字段会被平台拒收「请传入需更改状态的数据」。</p>
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
        // Pre：stast_info 节点（键名照抄老插件，非 stats_info）
        JSONObject stastInfo = new JSONObject();
        stastInfo.put("psn_no", command.getPsnNo());
        stastInfo.put("setl_id", command.getSetlId());
        stastInfo.put("stas_type", command.getStasType());
        JSONObject data = new JSONObject();
        data.put("stast_info", stastInfo);
        JSONObject input = new JSONObject();
        input.put("data", data);
        String operatorId = null;
        if (command.getOperatorId() != null) {
            operatorId = String.valueOf(command.getOperatorId());
        }
        InvokeContext context = InvokeContext.builder()
            .operatorId(operatorId)
            .build();
        // Cmd：出站 4102
        ResultBody body = gatewayClient.invoke(
            command.getContractBusNo(), ChsInfNo.SETTLE_LIST_STATUS, input, context);
        MedcasUploadExtensionOut result = new MedcasUploadExtensionOut();
        if (body == null) {
            result.setSuccess(false);
            result.setErrMsg("医保平台无返回");
            return result;
        }
        // Asm：infcode=0 为成功
        result.setInfcode(body.getInfcode());
        result.setErrMsg(body.getErrMsg());
        result.setWarnMsg(body.getWarnMsg());
        result.setOutput(body.getOutput());
        result.setSuccess("0".equals(trimToNull(body.getInfcode())));
        return result;
    }
}
