/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.outpatient.invoker;

import com.openhis.chs.domain.outpatient.command.OpPreSettleCommand;
import com.openhis.chs.gateway.client.InsuranceGatewayClient;
import com.openhis.chs.gateway.client.InvokeContext;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.gateway.scheme.outpatient.model.Op2203Input;
import com.openhis.chs.gateway.scheme.outpatient.assembler.Op2203InputAssembler;
import com.openhis.chs.gateway.scheme.outpatient.parser.Op2203OutputParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 【2203】就诊信息上传 Invoker。
 *
 * <p>出站：{@link InsuranceGatewayClient}；无副作用（不落库）。</p>
 */
@Component
@RequiredArgsConstructor
public class Op2203Invoker {

    private final InsuranceGatewayClient gatewayClient;

    /**
     * 上传门诊就诊信息至医保平台。
     *
     * @param contractBusNo 医保契约 bus_no
     * @param context       门诊预结算上下文
     * @param mdtrtId       医保平台就诊 ID
     * @param invokeContext 操作员与签到上下文
     * @return 平台原始响应体
     */
    public ResultBody invoke(
            String contractBusNo,
            OpPreSettleCommand context,
            String mdtrtId,
            InvokeContext invokeContext) {
        Op2203Input input = Op2203InputAssembler.assemble(context, mdtrtId);
        ResultBody body = gatewayClient.invoke(
            contractBusNo, ChsInfNo.OUTPATIENT_VISIT_UPLOAD, input, invokeContext);
        Op2203OutputParser.requireSuccess(body);
        return body;
    }
}
