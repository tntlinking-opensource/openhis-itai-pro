/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.audit.invoker;

import com.openhis.chs.bridge.audit.mapper.Audit3101ResultMapper;
import com.openhis.chs.bridge.audit.support.AuditInvokeSupport;
import com.openhis.chs.domain.audit.access.Audit3101Invoker;
import com.openhis.chs.domain.audit.command.AuditAnalyze3101Command;
import com.openhis.chs.domain.audit.command.AuditEncounterCommand;
import com.openhis.chs.domain.audit.command.AuditPatientCommand;
import com.openhis.chs.domain.audit.result.AuditAnalyzeResult;
import com.openhis.chs.domain.audit.service.YbDetailAuditDomainService;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.gateway.client.InsuranceGatewayClient;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.gateway.scheme.audit.assembler.Audit3101InputAssembler;
import com.openhis.chs.gateway.scheme.audit.model.Audit3101Output;
import com.openhis.chs.gateway.scheme.audit.parser.Audit3101OutputParser;
import com.openhis.chs.gateway.support.ChsPlatformResponseSupport;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 【3101】明细审核事前提醒分析 Invoker。
 *
 * <p>出站：{@link InsuranceGatewayClient}；成功后结构化落库 {@code yb_detail_audit_hit*}。</p>
 */
@Component
@RequiredArgsConstructor
public class Audit3101InvokerImpl implements Audit3101Invoker {

    private final InsuranceGatewayClient gatewayClient;
    private final AuditInvokeSupport auditInvokeSupport;
    private final YbDetailAuditDomainService detailAuditDomainService;

    /**
     * 调用平台 3101 并落库违规摘要。
     *
     * @param command 分析命令
     * @return 违规结果
     */
    @Override
    public AuditAnalyzeResult invoke(AuditAnalyze3101Command command) {
        // Pre：补 syscode / taskId / 契约校验
        auditInvokeSupport.prepareAnalyze3101(command);
        String busNo = auditInvokeSupport.resolveBusNo(command.getContractBusNo(), command.getContractId());
        // Cmd：出站 3101
        ResultBody body = gatewayClient.invoke(
            busNo,
            ChsInfNo.DETAIL_AUDIT_PRIOR,
            Audit3101InputAssembler.assemble(command),
            auditInvokeSupport.buildInvokeContext());
        // Gate：平台失败阻断（与收费链一致）
        ChsPlatformResponseSupport.requireSuccess(body, ChsInfNo.DETAIL_AUDIT_PRIOR);
        // Asm：解析 output → 领域 Result
        Audit3101Output output = Audit3101OutputParser.parse(body);
        AuditAnalyzeResult result = Audit3101ResultMapper.toResult(
            output, command.getTaskId(), command.getTrigScen(), busNo);
        Long operatorId = auditInvokeSupport.requireOperatorId();
        String drCodg = null;
        String drName = null;
        String deptCodg = null;
        String deptName = null;
        // Pre：从首个就诊取医生/科室冗余，供统计页筛选
        if (command.getPatients() != null && !command.getPatients().isEmpty()) {
            AuditPatientCommand patient = command.getPatients().get(0);
            if (patient.getEncounters() != null && !patient.getEncounters().isEmpty()) {
                AuditEncounterCommand encounter = patient.getEncounters().get(0);
                drCodg = encounter.getDrCodg();
                deptCodg = encounter.getAdmDeptCodg();
                deptName = encounter.getAdmDeptName();
            }
        }
        // Cmd：结构化落库 hit/hit_line
        detailAuditDomainService.persistAnalyzeResult(
            result, TenantUtils.getCurrentTenantId(), operatorId, drCodg, drName, deptCodg, deptName);
        return result;
    }
}
