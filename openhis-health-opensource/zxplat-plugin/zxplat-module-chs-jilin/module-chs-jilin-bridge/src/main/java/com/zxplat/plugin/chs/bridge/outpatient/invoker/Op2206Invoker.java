/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.outpatient.invoker;

import com.zxplat.plugin.chs.domain.outpatient.command.OpPreSettleCommand;
import com.zxplat.plugin.chs.gateway.client.InsuranceGatewayClient;
import com.zxplat.plugin.chs.gateway.client.InvokeContext;
import com.zxplat.plugin.chs.gateway.client.ResultBody;
import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.plugin.chs.gateway.scheme.common.model.ChsSetlinfoOutput;
import com.zxplat.plugin.chs.gateway.scheme.outpatient.model.Op2206Input;
import com.zxplat.plugin.chs.gateway.scheme.outpatient.assembler.Op2206InputAssembler;
import com.zxplat.plugin.chs.gateway.scheme.outpatient.parser.Op2206OutputParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 【2206】门诊费用预结算 Invoker。
 *
 * <p>出站：{@link InsuranceGatewayClient}；无副作用（不落库，由编排 saveActiveFrom2206）。</p>
 */
@Component
@RequiredArgsConstructor
public class Op2206Invoker {

    private final InsuranceGatewayClient gatewayClient;

    /**
     * 发起门诊费用预结算并解析结算信息。
     *
     * @param contractBusNo 医保契约 bus_no
     * @param context       门诊预结算上下文
     * @param mdtrtId       医保平台就诊 ID
     * @param chrgBchno     收费批次号
     * @param medfeeSumamt  医疗费总额
     * @param invokeContext 操作员与签到上下文
     * @return 平台预结算 setlinfo 输出
     */
    public ChsSetlinfoOutput invoke(
            String contractBusNo,
            OpPreSettleCommand context,
            String mdtrtId,
            String chrgBchno,
            BigDecimal medfeeSumamt,
            InvokeContext invokeContext) {
        Op2206Input input = Op2206InputAssembler.assemble(context, mdtrtId, chrgBchno, medfeeSumamt);
        ResultBody body = gatewayClient.invoke(
            contractBusNo, ChsInfNo.OUTPATIENT_PRE_SETTLE, input, invokeContext);
        return Op2206OutputParser.requireSetlinfo(body);
    }
}
