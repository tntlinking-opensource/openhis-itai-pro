/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.audit.invoker;

import com.openhis.chs.bridge.audit.support.AuditInvokeSupport;
import com.openhis.chs.domain.audit.access.Audit3103Invoker;
import com.openhis.chs.domain.audit.command.AuditFeedback3103Command;
import com.openhis.chs.domain.audit.service.YbDetailAuditDomainService;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.gateway.client.InsuranceGatewayClient;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.gateway.scheme.audit.assembler.Audit3103InputAssembler;
import com.openhis.chs.gateway.support.ChsPlatformResponseSupport;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 【3103】事前提醒服务反馈 Invoker。
 *
 * <p>平台成功看 {@code infcode=0}（output 常为 null）；成功后追加反馈落库。</p>
 */
@Component
@RequiredArgsConstructor
public class Audit3103InvokerImpl implements Audit3103Invoker {

    private final InsuranceGatewayClient gatewayClient;
    private final AuditInvokeSupport auditInvokeSupport;
    private final YbDetailAuditDomainService detailAuditDomainService;

    /**
     * 调用平台 3103 并落库反馈。
     *
     * @param command 反馈命令
     */
    @Override
    public void invoke(AuditFeedback3103Command command) {
        // Gate：warns / dspo 校验
        auditInvokeSupport.validateFeedback3103(command);
        String busNo = auditInvokeSupport.resolveBusNo(command.getContractBusNo(), command.getContractId());
        // Cmd：出站 3103
        ResultBody body = gatewayClient.invoke(
            busNo,
            ChsInfNo.DETAIL_AUDIT_FEEDBACK,
            Audit3103InputAssembler.assemble(command),
            auditInvokeSupport.buildInvokeContext());
        // Gate：平台失败阻断
        ChsPlatformResponseSupport.requireSuccess(body, ChsInfNo.DETAIL_AUDIT_FEEDBACK);
        // Cmd：追加反馈落库（非 upsert）
        Long operatorId = auditInvokeSupport.requireOperatorId();
        detailAuditDomainService.persistFeedback(
            command, TenantUtils.getCurrentTenantId(), busNo, operatorId);
    }
}
