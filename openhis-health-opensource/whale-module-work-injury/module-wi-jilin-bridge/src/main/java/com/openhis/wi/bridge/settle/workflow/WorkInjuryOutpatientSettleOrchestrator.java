/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.bridge.settle.workflow;

import com.openhis.wi.bridge.invoke.support.WorkInjuryInvokeLogSupport;
import com.openhis.wi.bridge.invoke.workflow.WorkInjuryInvokeOrchestrator;
import com.openhis.wi.gateway.scheme.shared.model.WiPhase;
import com.openhis.chs.domain.settlement.entity.YbSettlementRecord;
import com.openhis.wi.domain.share.WiErrorCodes;
import com.openhis.wi.domain.invoke.command.PrepareWorkInjuryInvokeRequest;
import com.openhis.wi.domain.invoke.result.PrepareWorkInjuryInvokeResponse;
import com.openhis.wi.domain.settle.command.ConfirmWorkInjurySettleCommand;
import com.openhis.wi.domain.settle.result.ConfirmWorkInjurySettleResult;
import com.openhis.wi.domain.settle.command.StartWorkInjuryPreSettleCommand;
import com.openhis.wi.domain.settle.command.StartWorkInjuryReverseCommand;
import com.openhis.wi.domain.settle.result.WorkInjuryPreSettleSnapshot;
import com.openhis.wi.domain.settle.result.WorkInjurySettlementRecordSummary;
import com.openhis.wi.domain.entity.YbWiEncounterAnchor;
import com.openhis.wi.domain.entity.YbWiPreSettle;
import com.openhis.wi.domain.repository.YbWiEncounterAnchorRepository;
import com.openhis.wi.domain.service.YbWiEncounterAnchorDomainService;
import com.openhis.wi.domain.service.YbWiPreSettleDomainService;
import com.openhis.wi.domain.service.YbWiSettlementDomainService;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 门诊工伤收银工作流（【2201→2207】/【2208】入口）。
 *
 * <p>委托 {@link WorkInjuryInvokeOrchestrator} 驱动 Win invoke 链。</p>
 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.work-injury.enabled", havingValue = "true")
public class WorkInjuryOutpatientSettleOrchestrator {

    private final WorkInjuryInvokeOrchestrator invokeOperations;
    private final YbWiEncounterAnchorDomainService anchorDomainService;
    private final YbWiEncounterAnchorRepository encounterAnchorRepository;
    private final YbWiSettlementDomainService settlementDomainService;
    private final YbWiPreSettleDomainService preSettleDomainService;
    private final WorkInjuryInvokeLogSupport invokeLogSupport;

    /**
     * OP_SETTLE 前校验 anchor 审批资格（WI-004）。
     *
     * @param encounterId 就诊 ID
     */
    public void assertQualificationBeforePreSettle(Long encounterId) {
        YbWiEncounterAnchor anchor = encounterAnchorRepository.findByEncounterId(encounterId).orElse(null);
        anchorDomainService.assertQualificationForSettle(anchor);
    }

    /**
     * 启动 OP_SETTLE invoke 会话（2201→2207 首步 prepare）。
     *
     * @param command encounterId、paymentDraftId、chargeItemIds 必填
     * @return prepare 响应（nextAction=INVOKE_WIN）
     */
    public PrepareWorkInjuryInvokeResponse startPreSettle(StartWorkInjuryPreSettleCommand command) {
        if (command == null || command.getEncounterId() == null) {
            throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "encounterId 不能为空");
        }
        if (command.getPaymentDraftId() == null) {
            throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "paymentDraftId 不能为空");
        }
        if (command.getChargeItemIds() == null || command.getChargeItemIds().isEmpty()) {
            throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "chargeItemIds 不能为空");
        }
        assertQualificationBeforePreSettle(command.getEncounterId());

        Map<String, Object> context = new HashMap<>();
        if (command.getContext() != null) {
            context.putAll(command.getContext());
        }
        context.put("chargeItemIds", command.getChargeItemIds());

        PrepareWorkInjuryInvokeRequest request = new PrepareWorkInjuryInvokeRequest()
                .setPhase(WiPhase.OP_SETTLE.getCode())
                .setEncounterId(command.getEncounterId())
                .setContractId(command.getContractId())
                .setPaymentDraftId(command.getPaymentDraftId())
                .setOperatorId(command.getOperatorId())
                .setContext(context);

        // 平台 2207 已成功、本地无回执：补落单边后直接 COMPLETED，避免再撞 2203「已存在结算」
        if (invokeLogSupport.hasSuccessful2207(command.getEncounterId())) {
            Optional<YbSettlementRecord> recovered = settlementDomainService.recoverUnilateralFromActivePreSettle(
                    command.getEncounterId(),
                    command.getPaymentDraftId(),
                    command.getContractId(),
                    TenantUtils.getCurrentTenantId());
            if (recovered.isPresent()) {
                YbSettlementRecord record = recovered.get();
                encounterAnchorRepository.findByEncounterId(command.getEncounterId()).ifPresent(anchor ->
                        anchorDomainService.apply2207SetlId(anchor, record.getSetlId()));
                return invokeOperations.prepareCompletedUnilateralSession(request, record);
            }
        }
        return invokeOperations.prepareInvoke(request);
    }

    /**
     * submit 成功后继续下一步 prepare（nextAction=PREPARE_NEXT 时由 UI 调用）。
     *
     * @param request 须带 sessionId
     */
    public PrepareWorkInjuryInvokeResponse prepareNext(PrepareWorkInjuryInvokeRequest request) {
        if (request == null || request.getSessionId() == null) {
            throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "sessionId 不能为空");
        }
        return invokeOperations.prepareInvoke(request);
    }

    /**
     * invoke 链完成后绑定 paymentId 并返回 2207 金额摘要（不再次调 Win）。
     *
     * @param command paymentDraftId 必填；paymentId 收银完成后回写
     * @return 结算回执摘要
     */
    public ConfirmWorkInjurySettleResult confirmSettle(ConfirmWorkInjurySettleCommand command) {
        if (command == null || command.getPaymentDraftId() == null) {
            throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "paymentDraftId 不能为空");
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        YbSettlementRecord record = settlementDomainService
                .findByPaymentDraft(command.getPaymentDraftId(), command.getChrgBchno(), tenantId)
                .orElseThrow(() -> new BusinessException(
                        WiErrorCodes.INVALID_INVOKE_SESSION, "未找到 2207 结算回执，请先完成 invoke 链"));
        if (command.getPaymentId() != null) {
            settlementDomainService.bindPaymentId(record.getId(), command.getPaymentId(), tenantId);
            record.setPaymentId(command.getPaymentId());
        }
        return toConfirmResult(record);
    }

    /**
     * 查 ACTIVE 预结算快照（收银页刷新基金/个人金额）。
     *
     * @param paymentDraftId 收银草稿 ID
     */
    public Optional<WorkInjuryPreSettleSnapshot> findActivePreSettleByPaymentDraft(Long paymentDraftId) {
        return preSettleDomainService
                .findActiveOrSettledByPaymentDraft(paymentDraftId, TenantUtils.getCurrentTenantId())
                .map(this::toPreSettleSnapshot);
    }

    /**
     * 启动 OP_REVERSE invoke 会话（【2208】）。
     *
     * @param command settlementRecordId 必填；operatorId 可空（由 prepare 回落当前登录人）
     */
    public PrepareWorkInjuryInvokeResponse startReverse(StartWorkInjuryReverseCommand command) {
        if (command == null || command.getSettlementRecordId() == null) {
            throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "settlementRecordId 不能为空");
        }
        YbSettlementRecord record = settlementDomainService.requireRevocableRecord(
                command.getSettlementRecordId(), TenantUtils.getCurrentTenantId());
        Map<String, Object> context = new HashMap<>();
        context.put("settlementRecordId", record.getId());
        if (command.getPaymentId() != null) {
            context.put("paymentId", command.getPaymentId());
        }
        Long contractId = command.getContractId();
        if (record.getContractId() != null) {
            contractId = record.getContractId();
        }
        PrepareWorkInjuryInvokeRequest request = new PrepareWorkInjuryInvokeRequest()
                .setPhase(WiPhase.OP_REVERSE.getCode())
                .setEncounterId(record.getEncounterId())
                .setContractId(contractId)
                .setOperatorId(command.getOperatorId())
                .setContext(context);
        return invokeOperations.prepareInvoke(request);
    }

    /**
     * 按 payment 列工伤结算回执（退费页）。
     *
     * @param paymentId 正式 payment.id
     */
    public List<WorkInjurySettlementRecordSummary> listSettlementsByPayment(Long paymentId) {
        if (paymentId == null) {
            return List.of();
        }
        List<WorkInjurySettlementRecordSummary> results = new ArrayList<>();
        for (YbSettlementRecord row : settlementDomainService.listWorkInjuryByPayment(
                paymentId, TenantUtils.getCurrentTenantId())) {
            results.add(new WorkInjurySettlementRecordSummary()
                    .setSettlementRecordId(row.getId())
                    .setPaymentId(row.getPaymentId())
                    .setPreSettleId(row.getPreSettleId())
                    .setContractId(row.getContractId())
                    .setChrgBchno(row.getChrgBchno())
                    .setSetlId(row.getSetlId())
                    .setMdtrtId(row.getMdtrtId())
                    .setStatus(row.getStatus())
                    .setMsgid2207(row.getMsgid2207()));
        }
        return results;
    }

    /** {@link YbWiPreSettle} → 2206 金额摘要 DTO。 */
    private WorkInjuryPreSettleSnapshot toPreSettleSnapshot(YbWiPreSettle row) {
        return new WorkInjuryPreSettleSnapshot()
                .setChrgBchno(row.getChrgBchno())
                .setMedfeeSumamt(row.getMedfeeSumamt())
                .setFundPaySumamt(row.getFundPaySumamt())
                .setAcctPay(row.getAcctPay())
                .setPsnCashPay(row.getPsnCashPay());
    }

    /** {@link YbSettlementRecord} → confirmSettle 落账结果 DTO。 */
    private ConfirmWorkInjurySettleResult toConfirmResult(YbSettlementRecord record) {
        return new ConfirmWorkInjurySettleResult()
                .setSettlementRecordId(record.getId())
                .setSetlId(record.getSetlId())
                .setChrgBchno(record.getChrgBchno())
                .setMdtrtId(record.getMdtrtId())
                .setAccountId(record.getAccountId())
                .setMedfeeSumamt(record.getMedfeeSumamt())
                .setFundPaySumamt(record.getFundPaySumamt())
                .setAcctPay(record.getAcctPay())
                .setPsnCashPay(record.getPsnCashPay());
    }
}
