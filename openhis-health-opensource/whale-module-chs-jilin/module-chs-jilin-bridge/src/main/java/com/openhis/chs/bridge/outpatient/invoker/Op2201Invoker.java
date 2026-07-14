/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.outpatient.invoker;

import com.openhis.chs.domain.outpatient.command.OpPreSettleCommand;
import com.openhis.chs.domain.outpatient.service.YbEncounterPlatformMapDomainService;
import com.openhis.chs.domain.outpatient.service.YbEncounterPlatformMapQueryService;
import com.openhis.chs.gateway.client.InsuranceGatewayClient;
import com.openhis.chs.gateway.client.InvokeContext;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.gateway.scheme.outpatient.model.Op2201Input;
import com.openhis.chs.gateway.scheme.outpatient.assembler.Op2201InputAssembler;
import com.openhis.chs.gateway.scheme.outpatient.model.Op2201Output;
import com.openhis.chs.gateway.scheme.outpatient.parser.Op2201OutputParser;
import com.openhis.chs.gateway.scheme.outpatient.assembler.OpOutpatientSettleInputAssembler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 【2201】门诊挂号登记 Invoker。
 *
 * <p>出站：{@link InsuranceGatewayClient}；副作用：读/写 yb_encounter_platform_map。</p>
 */
@Component
@RequiredArgsConstructor
public class Op2201Invoker {

    private final InsuranceGatewayClient gatewayClient;
    private final YbEncounterPlatformMapQueryService encounterPlatformMapQueryService;
    private final YbEncounterPlatformMapDomainService encounterPlatformMapDomainService;

    /**
     * 查询本地就诊平台映射，不存在时调用 2201 登记并落库。
     *
     * @param contractBusNo 医保契约 bus_no
     * @param context       门诊预结算上下文（就诊、费用性质、人员编号等）
     * @param invokeContext 操作员与签到上下文
     * @param tenantId      租户 ID
     * @return 医保平台就诊 ID（mdtrt_id）
     */
    public String resolveOrRegister(
            String contractBusNo,
            OpPreSettleCommand context,
            InvokeContext invokeContext,
            Long tenantId) {
        String medType = OpOutpatientSettleInputAssembler.resolveMedTypeForContext(context);
        Optional<String> existingMdtrtId = encounterPlatformMapQueryService
            .findOptionalByEncounterContractMedType(
                context.getEncounterId(), context.getContractId(), medType, tenantId)
            .map(row -> row.getMdtrtId());
        if (existingMdtrtId.isPresent()) {
            return existingMdtrtId.get();
        }
        Op2201Input input2201 = Op2201InputAssembler.assemble(context);
        ResultBody body2201 = gatewayClient.invoke(
            contractBusNo, ChsInfNo.OUTPATIENT_REG, input2201, invokeContext);
        Op2201Output output2201 = Op2201OutputParser.requireResult(body2201);
        String mdtrtId = Op2201OutputParser.resolveMdtrtId(output2201);
        encounterPlatformMapDomainService.saveAfterPlatformRegistration(
            context.getEncounterId(),
            context.getContractId(),
            medType,
            mdtrtId.trim(),
            context.getPsnNo(),
            context.getEncounterBusNo(),
            tenantId);
        return mdtrtId.trim();
    }
}
