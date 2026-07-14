/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.bridge.register.invoker;

import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import com.openhis.wi.domain.register.access.Build2301PayloadInvoker;
import com.openhis.wi.domain.register.command.Build2301PayloadCommand;
import com.openhis.wi.domain.share.WiErrorCodes;
import com.openhis.wi.domain.settle.command.WiSettleContext;
import com.openhis.wi.domain.entity.YbWiEncounterAnchor;
import com.openhis.wi.domain.loader.WorkInjurySettleContextLoader;
import com.openhis.wi.domain.service.YbWiEncounterAnchorDomainService;
import com.openhis.wi.gateway.scheme.shared.assembler.WiPlatformInputAssembler;
import com.openhis.wi.gateway.scheme.shared.model.WiJsonSupport;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 【2301】审批上传 input 组装 Invoker。
 *
 * <p>实现 domain {@link Build2301PayloadInvoker}；不发 Win，仅返回待下发业务体。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.work-injury.enabled", havingValue = "true")
public class Build2301PayloadInvokerImpl implements Build2301PayloadInvoker {

    private final YbWiEncounterAnchorDomainService anchorDomainService;
    private final ObjectProvider<WorkInjurySettleContextLoader> settleContextOperationsProvider;

    /**
     * 组装 2301 Win input Map（不发 Win）。
     *
     * @param command encounterId 必填；2301 表单字段可选
     * @return Win 业务体 input Map
     */
    @Override
    public Map<String, Object> invoke(Build2301PayloadCommand command) {
        if (command == null || command.getEncounterId() == null) {
            throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "encounterId 不能为空");
        }
        WiSettleContext ctx = loadRegisterContext(command.getEncounterId(), command.getContractId());
        YbWiEncounterAnchor anchor = anchorDomainService.ensureAnchor(
                command.getEncounterId(), command.getContractId(), ctx);
        return WiJsonSupport.toInputMap(
                WiPlatformInputAssembler.assemble2301(ctx, anchor, to2301FormContext(command)));
    }

    /** 加载登记链 HIS 上下文（loader 未装配时返回 null）。 */
    private WiSettleContext loadRegisterContext(Long encounterId, Long contractId) {
        WorkInjurySettleContextLoader loader = settleContextOperationsProvider.getIfAvailable();
        if (loader == null) {
            return null;
        }
        return loader.loadRegisterContext(encounterId, contractId);
    }

    /** Build2301PayloadCommand → WiPlatformInputAssembler 表单 Map。 */
    private Map<String, Object> to2301FormContext(Build2301PayloadCommand command) {
        Map<String, Object> form = new HashMap<>();
        if (isNotEmpty(command.getApproveType())) {
            form.put("approveType", command.getApproveType());
        }
        if (isNotEmpty(command.getStartDate())) {
            form.put("startDate", command.getStartDate());
        }
        if (isNotEmpty(command.getEndDate())) {
            form.put("endDate", command.getEndDate());
        }
        if (isNotEmpty(command.getTerminReason())) {
            form.put("terminReason", command.getTerminReason());
        }
        if (isNotEmpty(command.getInjureAffirmId())) {
            form.put("injureAffirmId", command.getInjureAffirmId());
        }
        if (isNotEmpty(command.getOrgCode())) {
            form.put("orgCode", command.getOrgCode());
        }
        if (isNotEmpty(command.getOrgName())) {
            form.put("orgName", command.getOrgName());
        }
        return form;
    }
}
