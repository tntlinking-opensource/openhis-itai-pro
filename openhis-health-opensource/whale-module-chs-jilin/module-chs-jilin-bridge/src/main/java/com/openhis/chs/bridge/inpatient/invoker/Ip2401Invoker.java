/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inpatient.invoker;


import com.openhis.chs.domain.inpatient.command.IpSettleContext;
import com.openhis.chs.domain.outpatient.service.YbEncounterPlatformMapDomainService;
import com.openhis.chs.domain.outpatient.service.YbEncounterPlatformMapQueryService;
import com.openhis.chs.gateway.client.InsuranceGatewayClient;
import com.openhis.chs.gateway.client.InvokeContext;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.gateway.scheme.inpatient.model.Ip2401Input;
import com.openhis.chs.gateway.scheme.inpatient.model.Ip2401Output;
import com.openhis.chs.gateway.scheme.inpatient.parser.Ip2401OutputParser;
import com.openhis.chs.gateway.scheme.inpatient.assembler.IpInpatientSettleInputAssembler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 【2401】住院入院登记 Invoker。
 *
 * <p>出站：{@link InsuranceGatewayClient}；副作用：读/写 yb_encounter_platform_map。</p>
 */
@Component
@RequiredArgsConstructor
public class Ip2401Invoker {

    private final InsuranceGatewayClient gatewayClient;
    private final YbEncounterPlatformMapQueryService encounterPlatformMapQueryService;
    private final YbEncounterPlatformMapDomainService encounterPlatformMapDomainService;

    /**
     * 查询本地就诊平台映射，不存在时调用 2401 入院登记并落库。
     *
     * @param contractBusNo 医保契约 bus_no
     * @param context       住院结算上下文（就诊、费用性质、人员编号等）
     * @param invokeContext 操作员与签到上下文
     * @param tenantId      租户 ID
     * @return 医保平台就诊 ID（mdtrt_id）
     */
    public String resolveOrRegister(
            String contractBusNo,
            IpSettleContext context,
            InvokeContext invokeContext,
            Long tenantId) {
        String medType = IpInpatientSettleInputAssembler.resolveMedTypeForContext(context);
        Optional<String> existingMdtrtId = encounterPlatformMapQueryService
            .findOptionalByEncounterContractMedType(
                context.getEncounterId(), context.getContractId(), medType, tenantId)
            .map(row -> row.getMdtrtId());
        if (existingMdtrtId.isPresent()) {
            return existingMdtrtId.get().trim();
        }
        Ip2401Input input2401 = IpInpatientSettleInputAssembler.assemble2401(context);
        ResultBody body2401 = gatewayClient.invoke(
            contractBusNo, ChsInfNo.INPATIENT_ADMISSION, input2401, invokeContext);
        Ip2401Output output2401 = Ip2401OutputParser.requireResult(body2401);
        String mdtrtId = Ip2401OutputParser.resolveMdtrtId(output2401);
        encounterPlatformMapDomainService.saveAfterPlatformRegistration(
            context.getEncounterId(),
            context.getContractId(),
            medType,
            mdtrtId,
            context.getPsnNo(),
            context.getEncounterBusNo(),
            tenantId);
        return mdtrtId;
    }
}
