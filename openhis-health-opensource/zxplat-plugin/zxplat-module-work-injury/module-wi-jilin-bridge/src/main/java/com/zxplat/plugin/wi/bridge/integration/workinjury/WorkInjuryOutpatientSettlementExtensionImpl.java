/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.bridge.integration.workinjury;

import com.zxplat.plugin.wi.bridge.context.WorkInjuryContractResolver;
import com.zxplat.plugin.wi.bridge.settle.workflow.WorkInjuryOutpatientSettleOrchestrator;
import com.zxplat.domain.infrastructure.integration.workinjury.WorkInjuryOutpatientSettlementExtension;
import com.zxplat.domain.infrastructure.integration.workinjury.ExtensionIn.WorkInjuryConfirmSettleExtensionIn;
import com.zxplat.domain.infrastructure.integration.workinjury.ExtensionIn.WorkInjuryReverseExtensionIn;
import com.zxplat.domain.infrastructure.integration.workinjury.ExtensionIn.WorkInjuryPreSettleQueryExtensionIn;
import com.zxplat.domain.infrastructure.integration.workinjury.ExtensionIn.WorkInjurySettleExtensionIn;
import com.zxplat.domain.infrastructure.integration.workinjury.ExtensionOut.WorkInjuryConfirmSettleExtensionOut;
import com.zxplat.domain.infrastructure.integration.workinjury.ExtensionOut.WorkInjuryPreSettleQueryExtensionOut;
import com.zxplat.domain.infrastructure.integration.workinjury.ExtensionOut.WorkInjurySettlementRecordExtensionOut;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.zxplat.domain.infrastructure.integration.workinjury.ExtensionOut.PrepareWorkInjuryInvokeExtensionOut;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 吉林工伤门诊收银 SPI 实现。
 *
 * <p><b>SPI</b>：{@link WorkInjuryOutpatientSettlementExtension}</p>
 * <p><b>Health 调用方</b>：{@code OutpatientChargeAppService}、{@code OutpatientRefundAppService}</p>
 * <p><b>Bridge 委托</b>：{@link WorkInjuryOutpatientSettleOrchestrator}、{@link WorkInjuryContractResolver}</p>
 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(name = "zxplat.modules.work-injury.enabled", havingValue = "true", matchIfMissing = true)
public class WorkInjuryOutpatientSettlementExtensionImpl implements WorkInjuryOutpatientSettlementExtension {

    private final WorkInjuryOutpatientSettleOrchestrator workInjuryOutpatientSettleOrchestrator;
    private final WorkInjuryContractResolver contractResolver;

    /** 费用性质 contract 是否走工伤结算。 */
    @Override
    public boolean supportsWorkInjuryAccount(Long contractId) {
        return contractResolver.isWorkInjuryContract(contractId);
    }

    /** 预结算前校验 qualification（WI-004）。 */
    @Override
    public void assertQualificationBeforePreSettle(Long encounterId) {
        workInjuryOutpatientSettleOrchestrator.assertQualificationBeforePreSettle(encounterId);
    }

    /** 【2201→2207】启动 OP_SETTLE invoke 首步 prepare。 */
    @Override
    public PrepareWorkInjuryInvokeExtensionOut startSettleSession(WorkInjurySettleExtensionIn command) {
        return WorkInjuryIntegrationMapper.toNeutralPrepareResult(
                workInjuryOutpatientSettleOrchestrator.startPreSettle(
                        WorkInjuryIntegrationMapper.toStartPreSettleCommand(command)));
    }

    /** invoke 链完成后 confirmSettle（绑定 paymentId）。 */
    @Override
    public WorkInjuryConfirmSettleExtensionOut confirmSettle(WorkInjuryConfirmSettleExtensionIn command) {
        return WorkInjuryIntegrationMapper.toNeutralConfirmResult(
                workInjuryOutpatientSettleOrchestrator.confirmSettle(
                        WorkInjuryIntegrationMapper.toConfirmSettleCommand(command)));
    }

    /** 查 ACTIVE 2206 预结算快照。 */
    @Override
    public Optional<WorkInjuryPreSettleQueryExtensionOut> findActivePreSettleByPaymentDraft(
            WorkInjuryPreSettleQueryExtensionIn query) {
        if (query == null || query.getPaymentDraftId() == null) {
            return Optional.empty();
        }
        return workInjuryOutpatientSettleOrchestrator.findActivePreSettleByPaymentDraft(query.getPaymentDraftId())
                .map(WorkInjuryIntegrationMapper::toNeutralPreSettleSnapshot);
    }

    /** 【2208】启动 OP_REVERSE invoke 首步 prepare。 */
    @Override
    public PrepareWorkInjuryInvokeExtensionOut startReverseSession(WorkInjuryReverseExtensionIn command) {
        return WorkInjuryIntegrationMapper.toNeutralPrepareResult(
                workInjuryOutpatientSettleOrchestrator.startReverse(
                        WorkInjuryIntegrationMapper.toStartReverseCommand(command)));
    }

    /** 按 payment 列工伤结算回执（退费页）。 */
    @Override
    public List<WorkInjurySettlementRecordExtensionOut> listSettlementsByPayment(Long paymentId) {
        List<WorkInjurySettlementRecordExtensionOut> results = new ArrayList<>();
        for (var row : workInjuryOutpatientSettleOrchestrator.listSettlementsByPayment(paymentId)) {
            WorkInjurySettlementRecordExtensionOut mapped = WorkInjuryIntegrationMapper.toNeutralSettlementRecord(row);
            if (mapped != null) {
                results.add(mapped);
            }
        }
        return results;
    }
}
