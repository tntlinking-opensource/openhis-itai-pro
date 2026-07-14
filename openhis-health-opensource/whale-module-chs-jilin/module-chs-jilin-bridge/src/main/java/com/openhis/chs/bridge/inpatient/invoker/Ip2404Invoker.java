/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inpatient.invoker;

import com.openhis.chs.bridge.inpatient.assembler.InpatientSettleCommandAssembler;
import com.openhis.chs.domain.inpatient.command.IpEncounterPlatformCommand;
import com.openhis.chs.domain.outpatient.service.YbEncounterPlatformMapDomainService;
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
 * 【2404】入院撤销 Invoker。
 *
 * <p>出站：{@link InsuranceGatewayClient}；副作用：写 yb_encounter_platform_map（软删映射）。</p>
 */
@Component
@RequiredArgsConstructor
public class Ip2404Invoker {

    private final InsuranceGatewayClient gatewayClient;
    private final YbEncounterPlatformMapDomainService encounterPlatformMapDomainService;

    /**
     * 撤销入院登记并软删本地平台映射。
     *
     * @param command 含 encounterId、contractBusNo、mdtrtId、psnNo 及 operatorId
     */
    public void invoke(IpEncounterPlatformCommand command) {
        InpatientSettleCommandAssembler.validateEncounterPlatformCommand(
            command.getEncounterId(), command.getContractBusNo(),
            command.getMdtrtId(), command.getPsnNo());
        InvokeContext invokeContext = InpatientSettleCommandAssembler.buildInvokeContext(
            command.getOperatorId(), command.getInsuplcAdmdvs());
        Ip2404Input input2404 = IpInpatientSettleInputAssembler.assemble2404Or2405(
            command.getMdtrtId(), command.getPsnNo());
        ResultBody body2404 = gatewayClient.invoke(
            command.getContractBusNo(), ChsInfNo.INPATIENT_ADMISSION_REVOKE, input2404, invokeContext);
        ChsPlatformResponseSupport.requireSuccess(body2404, ChsInfNo.INPATIENT_ADMISSION_REVOKE);
        Long tenantId = InpatientSettleCommandAssembler.resolveTenantId(command.getTenantId());
        encounterPlatformMapDomainService.softDeleteEncounterMap(
            command.getEncounterId(),
            command.getContractId(),
            command.getMdtrtId(),
            command.getMedType(),
            command.getOperatorId(),
            tenantId);
    }
}
