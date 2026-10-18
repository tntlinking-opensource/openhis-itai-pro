/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.inpatient.workflow;

import com.zxplat.plugin.chs.domain.inpatient.result.IpAdmissionResult;
import com.zxplat.plugin.chs.domain.inpatient.command.IpSettleContext;
import com.zxplat.plugin.chs.bridge.inpatient.assembler.InpatientSettleCommandAssembler;
import com.zxplat.plugin.chs.bridge.inpatient.invoker.Ip2401Invoker;
import com.zxplat.plugin.chs.gateway.client.InvokeContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 【2401】住院入院登记编排。
 *
 * <p>出站由 {@link Ip2401Invoker} 完成；本类不调 gateway。</p>
 */
@Component
@RequiredArgsConstructor
public class IpAdmissionOrchestrator {

    private final Ip2401Invoker ip2401Invoker;

    /** 确保住院就诊已在平台完成 2401 入院登记，返回 mdtrt_id。 */
    public IpAdmissionResult ensureAdmission(IpSettleContext context) {
        InpatientSettleCommandAssembler.validateAdmissionContext(context);
        InpatientSettleCommandAssembler.requireContractConfig(context.getContractBusNo());
        InvokeContext invokeContext = InpatientSettleCommandAssembler.buildInvokeContext(context);
        Long tenantId = InpatientSettleCommandAssembler.resolveTenantId(context.getTenantId());
        String mdtrtId = ip2401Invoker.resolveOrRegister(
            context.getContractBusNo(), context, invokeContext, tenantId);
        return new IpAdmissionResult().setMdtrtId(mdtrtId);
    }
}
