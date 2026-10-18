/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.bridge.integration.workinjury;

import com.zxplat.plugin.wi.gateway.scheme.shared.model.WiPhase;
import com.zxplat.plugin.wi.bridge.invoke.workflow.WorkInjuryInvokeOrchestrator;
import com.zxplat.plugin.wi.domain.repository.YbWiEncounterAnchorRepository;
import com.zxplat.domain.infrastructure.integration.workinjury.WorkInjuryApprovalUploadExtension;
import com.zxplat.domain.infrastructure.integration.workinjury.ExtensionOut.PrepareWorkInjuryInvokeExtensionOut;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 吉林工伤 2301 审批上传 SPI 实现。
 *
 * <p><b>SPI</b>：{@link WorkInjuryApprovalUploadExtension}</p>
 * <p><b>Health 调用方</b>：挂号页、医生站审批按钮</p>
 * <p><b>Bridge 委托</b>：{@link WorkInjuryInvokeOrchestrator}（phase=REG_APPROVAL）</p>
 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(name = "zxplat.modules.work-injury.enabled", havingValue = "true", matchIfMissing = true)
public class WorkInjuryApprovalUploadExtensionImpl implements WorkInjuryApprovalUploadExtension {

    private final WorkInjuryInvokeOrchestrator workInjuryInvokeOrchestrator;
    private final YbWiEncounterAnchorRepository encounterAnchorRepository;

    /** 就诊已建 anchor 时展示 2301 入口。 */
    @Override
    public boolean supportsApprovalUpload(Long encounterId) {
        if (encounterId == null) {
            return false;
        }
        return encounterAnchorRepository.findByEncounterId(encounterId).isPresent();
    }

    /** 【2301→9102】启动 REG_APPROVAL invoke 首步 prepare。 */
    @Override
    public PrepareWorkInjuryInvokeExtensionOut upload2301(Long encounterId, Long contractId, Long operatorId) {
        return WorkInjuryIntegrationMapper.toNeutralPrepareResult(
                workInjuryInvokeOrchestrator.prepareInvoke(
                        WorkInjuryIntegrationMapper.toRegApprovalPrepareRequest(
                                encounterId, contractId, operatorId, null)
                                .setPhase(WiPhase.REG_APPROVAL.getCode())));
    }
}
