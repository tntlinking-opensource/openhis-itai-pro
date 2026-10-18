/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.bridge.register.workflow;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;

import com.zxplat.plugin.wi.bridge.invoke.workflow.WorkInjuryInvokeOrchestrator;
import com.zxplat.plugin.wi.gateway.scheme.shared.model.WiPhase;
import com.zxplat.plugin.wi.domain.share.WiErrorCodes;
import com.zxplat.plugin.wi.domain.settle.command.WiSettleContext;
import com.zxplat.plugin.wi.domain.invoke.command.PrepareWorkInjuryInvokeRequest;
import com.zxplat.plugin.wi.domain.invoke.result.PrepareWorkInjuryInvokeResponse;
import com.zxplat.plugin.wi.domain.register.access.Build2301PayloadInvoker;
import com.zxplat.plugin.wi.domain.register.command.BindQualificationCommand;
import com.zxplat.plugin.wi.domain.register.command.Build2301PayloadCommand;
import com.zxplat.plugin.wi.domain.entity.YbWiEncounterAnchor;
import com.zxplat.plugin.wi.domain.loader.WorkInjurySettleContextLoader;
import com.zxplat.plugin.wi.domain.service.YbWiEncounterAnchorDomainService;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 登记链工作流（【9102】/【2301】/ bindQualification）。
 *
 * <p>本类不调 Win；单步报文经 {@link com.zxplat.plugin.wi.bridge.invoke.workflow.WorkInjuryInvokeOrchestrator}。</p>
 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(name = "zxplat.modules.work-injury.enabled", havingValue = "true", matchIfMissing = true)
public class WorkInjuryRegisterOrchestrator {

    private final WorkInjuryInvokeOrchestrator invokeOperations;
    private final YbWiEncounterAnchorDomainService anchorDomainService;
    private final Build2301PayloadInvoker build2301PayloadInvoker;
    private final ObjectProvider<WorkInjurySettleContextLoader> settleContextOperationsProvider;

    /**
     * UI 手动绑定 qualification_id（9102 单行或人工选择）。
     *
     * @param command encounterId、qualificationId 必填
     */
    @Transactional
    public void bindQualification(BindQualificationCommand command) {
        if (command == null || command.getEncounterId() == null) {
            throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "encounterId 不能为空");
        }
        if (isEmpty(command.getQualificationId())) {
            throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "qualification_id 不能为空");
        }
        WiSettleContext ctx = loadRegisterContext(command.getEncounterId(), command.getContractId());
        YbWiEncounterAnchor anchor = anchorDomainService.ensureAnchor(
                command.getEncounterId(), command.getContractId(), ctx);
        anchorDomainService.bindQualification(anchor, command.getQualificationId());
    }

    /**
     * 组装 2301 Win input Map（预览；不发 Win）。
     *
     * @param command 2301 表单字段
     * @return Win 业务体 input
     */
    public Map<String, Object> build2301Payload(Build2301PayloadCommand command) {
        return build2301PayloadInvoker.invoke(command);
    }

    /**
     * 启动 REG_QUERY invoke 会话（【1101】→【9102】首步 prepare）。
     *
     * @param request encounter/contract；phase 会被覆盖为 REG_QUERY
     */
    public PrepareWorkInjuryInvokeResponse start9102(PrepareWorkInjuryInvokeRequest request) {
        PrepareWorkInjuryInvokeRequest resolved = copyPrepareRequest(request);
        resolved.setPhase(WiPhase.REG_QUERY.getCode());
        return invokeOperations.prepareInvoke(resolved);
    }

    /** 复制 prepare 请求（避免 mutate 调用方对象）。 */
    private PrepareWorkInjuryInvokeRequest copyPrepareRequest(PrepareWorkInjuryInvokeRequest request) {
        if (request == null) {
            return new PrepareWorkInjuryInvokeRequest();
        }
        return new PrepareWorkInjuryInvokeRequest()
                .setSessionId(request.getSessionId())
                .setPhase(request.getPhase())
                .setEncounterId(request.getEncounterId())
                .setContractId(request.getContractId())
                .setPaymentDraftId(request.getPaymentDraftId())
                .setOperatorId(request.getOperatorId())
                .setContext(request.getContext());
    }

    /** 加载登记链 HIS 上下文（loader 未装配时返回 null）。 */
    private WiSettleContext loadRegisterContext(Long encounterId, Long contractId) {
        WorkInjurySettleContextLoader loader = settleContextOperationsProvider.getIfAvailable();
        if (loader == null) {
            return null;
        }
        return loader.loadRegisterContext(encounterId, contractId);
    }
}
