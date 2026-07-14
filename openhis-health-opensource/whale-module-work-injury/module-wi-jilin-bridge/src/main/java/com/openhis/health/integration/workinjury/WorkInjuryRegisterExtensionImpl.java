/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.integration.workinjury;

import com.openhis.wi.domain.repository.YbWiEncounterAnchorRepository;
import com.openhis.health.infrastructure.integration.workinjury.WorkInjuryRegisterExtension;
import com.openhis.wi.bridge.register.workflow.WorkInjuryRegisterOrchestrator;
import com.openhis.health.infrastructure.integration.workinjury.ExtensionIn.WorkInjury9102ExtensionIn;
import com.openhis.health.infrastructure.integration.workinjury.ExtensionOut.PrepareWorkInjuryInvokeExtensionOut;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 吉林工伤挂号/9102 SPI 实现。
 *
 * <p><b>SPI</b>：{@link WorkInjuryRegisterExtension}</p>
 * <p><b>Health 调用方</b>：{@code OutpatientRegistrationAppService}</p>
 * <p><b>Bridge 委托</b>：{@link com.openhis.wi.bridge.register.workflow.WorkInjuryRegisterOrchestrator}</p>
 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.work-injury.enabled", havingValue = "true")
public class WorkInjuryRegisterExtensionImpl implements WorkInjuryRegisterExtension {

    private final WorkInjuryRegisterOrchestrator workInjuryRegisterOrchestrator;
    private final YbWiEncounterAnchorRepository encounterAnchorRepository;

    /** 就诊是否已建 anchor（改费用性质=工伤后的门控）。 */
    @Override
    public boolean requiresWorkInjuryFlow(Long encounterId) {
        if (encounterId == null) {
            return false;
        }
        if (encounterAnchorRepository.findByEncounterId(encounterId).isPresent()) {
            return true;
        }
        return false;
    }

    /** 【9102】启动 REG_QUERY invoke 首步 prepare。 */
    @Override
    public PrepareWorkInjuryInvokeExtensionOut start9102(WorkInjury9102ExtensionIn command) {
        return WorkInjuryIntegrationMapper.toNeutralPrepareResult(
                workInjuryRegisterOrchestrator.start9102(WorkInjuryIntegrationMapper.toPrepareRequest(command)));
    }

    /** 9102 后 UI 手动绑定 qualification_id。 */
    @Override
    public void bindQualification(Long encounterId, Long contractId, String qualificationId) {
        workInjuryRegisterOrchestrator.bindQualification(
                WorkInjuryIntegrationMapper.toBindQualificationCommand(encounterId, contractId, qualificationId));
    }

    /** 工伤挂号费一律自费（WI-005）。 */
    @Override
    public boolean assertRegistrationFeeSelfPay(Long encounterId) {
        return true;
    }
}
