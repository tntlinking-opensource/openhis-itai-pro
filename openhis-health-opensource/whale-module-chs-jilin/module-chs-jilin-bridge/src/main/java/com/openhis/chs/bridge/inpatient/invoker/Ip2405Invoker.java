/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inpatient.invoker;

import com.openhis.chs.bridge.inpatient.assembler.InpatientSettleCommandAssembler;
import com.openhis.chs.domain.inpatient.command.IpEncounterPlatformCommand;
import com.openhis.chs.gateway.client.InsuranceGatewayClient;
import com.openhis.chs.gateway.client.InvokeContext;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.gateway.scheme.inpatient.model.Ip2404Input;
import com.openhis.chs.gateway.scheme.inpatient.assembler.IpInpatientSettleInputAssembler;
import com.openhis.chs.gateway.support.ChsPlatformResponseSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 【2405】出院撤销 Invoker。
 *
 * <p>出站：{@link InsuranceGatewayClient}；无副作用（不落库）。</p>
 */
@Component
@RequiredArgsConstructor
public class Ip2405Invoker {

    private final InsuranceGatewayClient gatewayClient;

    /**
     * 撤销出院办理并提交至医保平台。
     *
     * @param command 含 encounterId、contractBusNo、mdtrtId、psnNo 及 operatorId
     */
    public void invoke(IpEncounterPlatformCommand command) {
        InpatientSettleCommandAssembler.validateEncounterPlatformCommand(
            command.getEncounterId(), command.getContractBusNo(),
            command.getMdtrtId(), command.getPsnNo());
        InvokeContext invokeContext = InpatientSettleCommandAssembler.buildInvokeContext(
            command.getOperatorId(), command.getInsuplcAdmdvs());
        Ip2404Input input2405 = IpInpatientSettleInputAssembler.assemble2404Or2405(
            command.getMdtrtId(), command.getPsnNo());
        ResultBody body2405 = gatewayClient.invoke(
            command.getContractBusNo(), ChsInfNo.INPATIENT_DISCHARGE_REVOKE, input2405, invokeContext);
        ChsPlatformResponseSupport.requireSuccess(body2405, ChsInfNo.INPATIENT_DISCHARGE_REVOKE);
    }
}
