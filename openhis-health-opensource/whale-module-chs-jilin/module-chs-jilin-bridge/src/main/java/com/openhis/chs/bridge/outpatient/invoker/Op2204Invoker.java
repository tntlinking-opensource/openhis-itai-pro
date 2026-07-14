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
import com.openhis.chs.gateway.scheme.outpatient.model.Op2204Input;
import com.openhis.chs.gateway.scheme.outpatient.assembler.Op2204InputAssembler;
import com.openhis.chs.gateway.scheme.outpatient.parser.Op2204OutputParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 【2204】费用明细上传 Invoker。
 *
 * <p>出站：{@link InsuranceGatewayClient}；无副作用（不落库，由编排 persistFrom2204）。</p>
 */
@Component
@RequiredArgsConstructor
public class Op2204Invoker {

    private final InsuranceGatewayClient gatewayClient;

    /**
     * 上传门诊费用明细至医保平台。
     *
     * @param contractBusNo 医保契约 bus_no
     * @param context       门诊预结算上下文
     * @param mdtrtId       医保平台就诊 ID
     * @param chrgBchno     收费批次号
     * @param invokeContext 操作员与签到上下文
     * @return 平台原始响应体
     */
    public ResultBody invoke(
            String contractBusNo,
            OpPreSettleCommand context,
            String mdtrtId,
            String chrgBchno,
            InvokeContext invokeContext) {
        Op2204Input input = Op2204InputAssembler.assemble(context, mdtrtId, chrgBchno);
        ResultBody body = gatewayClient.invoke(
            contractBusNo, ChsInfNo.OUTPATIENT_FEE_UPLOAD, input, invokeContext);
        Op2204OutputParser.requireSuccess(body);
        return body;
    }
}
