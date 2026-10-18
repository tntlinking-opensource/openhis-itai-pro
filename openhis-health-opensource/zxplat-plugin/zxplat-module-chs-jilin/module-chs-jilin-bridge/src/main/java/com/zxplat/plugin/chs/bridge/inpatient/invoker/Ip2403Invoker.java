/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.inpatient.invoker;

import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;

import com.zxplat.plugin.chs.bridge.inpatient.assembler.InpatientSettleCommandAssembler;
import com.zxplat.plugin.chs.domain.inpatient.command.IpSettleContext;
import com.zxplat.plugin.chs.domain.outpatient.entity.YbEncounterPlatformMap;
import com.zxplat.plugin.chs.domain.outpatient.service.YbEncounterPlatformMapQueryService;
import com.zxplat.plugin.chs.gateway.client.InsuranceGatewayClient;
import com.zxplat.plugin.chs.gateway.client.InvokeContext;
import com.zxplat.plugin.chs.gateway.client.ResultBody;
import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.plugin.chs.gateway.scheme.inpatient.model.Ip2403Input;
import com.zxplat.plugin.chs.gateway.scheme.inpatient.assembler.IpInpatientSettleInputAssembler;
import com.zxplat.plugin.chs.gateway.support.ChsPlatformResponseSupport;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 【2403】住院信息变更 Invoker。
 *
 * <p>出站：{@link InsuranceGatewayClient}；读 yb_encounter_platform_map（解析 mdtrt_id），无写库。</p>
 */
@Component
@RequiredArgsConstructor
public class Ip2403Invoker {

    private final InsuranceGatewayClient gatewayClient;
    private final YbEncounterPlatformMapQueryService encounterPlatformMapQueryService;

    /**
     * 提交住院信息变更至医保平台。
     *
     * @param context 住院结算上下文（含 contractBusNo、就诊及入院信息）
     */
    public void invoke(IpSettleContext context) {
        InpatientSettleCommandAssembler.validateAdmissionContext(context);
        InpatientSettleCommandAssembler.requireContractConfig(context.getContractBusNo());
        InvokeContext invokeContext = InpatientSettleCommandAssembler.buildInvokeContext(context);
        String mdtrtId = resolveExistingMdtrtId(context);
        Ip2403Input input2403 = IpInpatientSettleInputAssembler.assemble2403(context, mdtrtId);
        ResultBody body2403 = gatewayClient.invoke(
            context.getContractBusNo(), ChsInfNo.INPATIENT_INFO_CHANGE, input2403, invokeContext);
        ChsPlatformResponseSupport.requireSuccess(body2403, ChsInfNo.INPATIENT_INFO_CHANGE);
    }

    /**
     * 从 yb_encounter_platform_map 解析已登记的医保就诊 ID。
     *
     * @param context 住院结算上下文（含 encounterId、contractId、medType）
     * @return 平台 mdtrt_id
     */
    private String resolveExistingMdtrtId(IpSettleContext context) {
        Long tenantId = InpatientSettleCommandAssembler.resolveTenantId(context.getTenantId());
        String medType = IpInpatientSettleInputAssembler.resolveMedTypeForContext(context);
        Optional<YbEncounterPlatformMap> mapped = encounterPlatformMapQueryService
            .findOptionalByEncounterContractMedType(
                context.getEncounterId(), context.getContractId(), medType, tenantId);
        if (mapped.isPresent() && isNotEmpty(mapped.get().getMdtrtId())) {
            return mapped.get().getMdtrtId().trim();
        }
        throw new BusinessException("INS-IP-009", "未找到医保就诊ID，请先完成医保入院登记");
    }
}
