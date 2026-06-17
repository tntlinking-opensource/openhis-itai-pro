/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.insurance.coordination.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.health.application.insurance.coordination.dto.OutpatientOpSettleCoordinationSnapshot;
import com.openhis.health.application.insurance.outpatient.OutpatientInsuranceSettlementCompensation;
import com.openhis.health.application.outpatient.financial.charge.service.OutpatientSettleLocalCompletionService;
import com.openhis.health.domain.financial.paymentreconciliation.command.RevertOutpatientPaymentFromSettlingCommand;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationDomainService;
import com.openhis.health.domain.insurance.coordination.command.AppendYbCoordinationStepCommand;
import com.openhis.health.domain.insurance.coordination.command.TransitionYbCoordinationCommand;
import com.openhis.health.domain.insurance.coordination.entity.YbCoordination;
import com.openhis.health.domain.insurance.coordination.repository.YbCoordinationStepRepository;
import com.openhis.health.domain.insurance.coordination.service.YbCoordinationDomainService;
import com.openhis.health.domain.share.constants.InsuranceCoordinationConstants;
import com.openhis.health.domain.share.enums.YbCoordinationBizType;
import com.openhis.health.domain.share.enums.YbCoordinationState;
import com.openhis.whale.chs.jilin.port.OutpatientInsuranceSettlementPort;
import com.openhis.whale.chs.jilin.port.model.OpInsuranceConfirmCommand;
import com.openhis.whale.chs.jilin.port.model.OpInsuranceConfirmResult;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 门诊医保结算协同单执行器（OP_SETTLE：2207 平台 + 本地落账）。
 */
@Slf4j
@Component
@ConditionalOnBean(OutpatientInsuranceSettlementPort.class)
@RequiredArgsConstructor
public class OpSettleCoordinationExecutor implements YbCoordinationStepExecutor {

    private final YbCoordinationDomainService coordinationDomainService;
    private final YbCoordinationStepRepository stepRepository;
    private final OutpatientSettleLocalCompletionService localCompletionService;
    private final OutpatientInsuranceSettlementCompensation settlementCompensation;
    private final PaymentReconciliationDomainService paymentReconciliationDomainService;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private OutpatientInsuranceSettlementPort outpatientInsuranceSettlementPort;

    @Override
    public YbCoordinationBizType bizType() {
        return YbCoordinationBizType.OP_SETTLE;
    }

    @Override
    public void dispatch(YbCoordination coordination) {
        if (coordination == null || coordination.getId() == null) {
            return;
        }
        YbCoordinationState state = coordination.stateEnum();
        if (YbCoordinationState.CREATED.equals(state)) {
            this.runPlatformPhase(coordination);
            return;
        }
        if (YbCoordinationState.LOCAL_PENDING.equals(state)) {
            this.runLocalPhase(coordination);
        }
    }

    private void runPlatformPhase(YbCoordination coordination) {
        this.transition(coordination.getId(), YbCoordinationState.PLATFORM_RUNNING, null, null, null);
        OutpatientOpSettleCoordinationSnapshot snapshot = this.readSnapshot(coordination.getRequestSnapshot());
        if (outpatientInsuranceSettlementPort == null || snapshot == null) {
            this.failPlatform(coordination, "医保 Port 未启用或协同单上下文缺失");
            return;
        }
        List<String> chrgBchnos = snapshot.getChrgBchnos();
        if (chrgBchnos == null || chrgBchnos.isEmpty()) {
            this.failPlatform(coordination, "缺少医保收费批次号");
            return;
        }
        int succeededBatches = 0;
        try {
            List<String> setlIds = new ArrayList<>();
            int stepOrder = stepRepository.countByCoordinationIdAndIsDeletedFalse(coordination.getId());
            for (String chrgBchno : chrgBchnos) {
                if (StringUtils.isBlank(chrgBchno)) {
                    continue;
                }
                OpInsuranceConfirmCommand command = new OpInsuranceConfirmCommand();
                command.setPaymentDraftId(snapshot.getPaymentDraftId());
                command.setChrgBchno(chrgBchno.trim());
                command.setEncounterId(snapshot.getEncounterId());
                OpInsuranceConfirmResult ybResult = outpatientInsuranceSettlementPort.confirmBeforeLocalSettle(command);
                String setlId;
                if (ybResult != null) {
                    setlId = ybResult.getSetlId();
                } else {
                    setlId = null;
                }
                stepOrder += 1;
                AppendYbCoordinationStepCommand stepCmd = new AppendYbCoordinationStepCommand();
                stepCmd.setCoordinationId(coordination.getId());
                stepCmd.setStepOrder(stepOrder);
                stepCmd.setInfno(InsuranceCoordinationConstants.OP_SETTLE_PLATFORM_INFNO);
                stepCmd.setMsgid(coordination.getMsgid());
                stepCmd.setState(YbCoordinationState.COMPLETED.getCode());
                stepCmd.setResponseSnapshot(setlId);
                coordinationDomainService.appendStep(stepCmd);
                if (StringUtils.isNotBlank(setlId)) {
                    setlIds.add(setlId.trim());
                }
                succeededBatches += 1;
            }
            if (setlIds.isEmpty()) {
                this.failPlatform(coordination, "医保正式结算未返回 setl_id");
                return;
            }
            String joinedSetlIds = String.join(",", setlIds);
            this.transition(
                coordination.getId(),
                YbCoordinationState.PLATFORM_OK,
                null,
                joinedSetlIds,
                joinedSetlIds);
            YbCoordination refreshed = coordinationDomainService.requireById(coordination.getId());
            this.runLocalPhase(refreshed);
        } catch (BusinessException ex) {
            if (succeededBatches > 0) {
                this.compensatePartialPlatformSuccess(snapshot.getPaymentDraftId());
            }
            this.failPlatform(coordination, ex.getMessage());
        } catch (RuntimeException ex) {
            if (succeededBatches > 0) {
                this.compensatePartialPlatformSuccess(snapshot.getPaymentDraftId());
            }
            log.warn("OP_SETTLE platform unknown: coordinationId={}", coordination.getId(), ex);
            this.transition(
                coordination.getId(),
                YbCoordinationState.UNKNOWN,
                ex.getMessage(),
                null,
                null);
        }
    }

    private void runLocalPhase(YbCoordination coordination) {
        this.transition(coordination.getId(), YbCoordinationState.LOCAL_RUNNING, null, null, null);
        OutpatientOpSettleCoordinationSnapshot snapshot = this.readSnapshot(coordination.getRequestSnapshot());
        try {
            OutpatientSettleLocalCompletionService.LocalCompletionResult result =
                localCompletionService.completeFromConfirmRequest(
                    snapshot.getConfirmRequest(),
                    snapshot.getOperatorId(),
                    coordination.getPlatformSetlId());
            String responseJson = objectMapper.writeValueAsString(result);
            this.transition(
                coordination.getId(),
                YbCoordinationState.COMPLETED,
                null,
                coordination.getPlatformSetlId(),
                responseJson);
        } catch (BusinessException ex) {
            this.scheduleLocalRetry(coordination, ex.getMessage());
        } catch (Exception ex) {
            this.scheduleLocalRetry(coordination, ex.getMessage());
        }
    }

    private void scheduleLocalRetry(YbCoordination coordination, String errorMsg) {
        int nextRetry;
        if (coordination.getRetryCount() != null) {
            nextRetry = coordination.getRetryCount() + 1;
        } else {
            nextRetry = 1;
        }
        TransitionYbCoordinationCommand cmd = new TransitionYbCoordinationCommand();
        cmd.setCoordinationId(coordination.getId());
        if (nextRetry >= InsuranceCoordinationConstants.OP_SETTLE_LOCAL_MAX_RETRIES) {
            cmd.setTargetState(YbCoordinationState.FAILED);
            cmd.setErrorMsg(errorMsg);
            this.revertPayment(coordination.getPaymentId(), coordination.getOperatorId());
        } else {
            cmd.setTargetState(YbCoordinationState.LOCAL_PENDING);
            cmd.setErrorMsg(errorMsg);
            cmd.setRetryCount(nextRetry);
            int backoffSeconds = InsuranceCoordinationConstants.LOCAL_RETRY_BACKOFF_BASE_SECONDS
                * (1 << Math.min(nextRetry - 1, 4));
            cmd.setNextRetryAt(LocalDateTime.now().plusSeconds(backoffSeconds));
        }
        coordinationDomainService.transition(cmd);
    }

    private void failPlatform(YbCoordination coordination, String errorMsg) {
        this.transition(coordination.getId(), YbCoordinationState.FAILED, errorMsg, null, null);
        this.revertPayment(coordination.getPaymentId(), coordination.getOperatorId());
    }

    private void compensatePartialPlatformSuccess(Long paymentDraftId) {
        if (settlementCompensation == null || paymentDraftId == null) {
            return;
        }
        try {
            settlementCompensation.compensateSettlementsByPayment(paymentDraftId);
        } catch (RuntimeException ex) {
            log.warn("OP_SETTLE partial 2208 compensation failed paymentId={}", paymentDraftId, ex);
        }
    }

    private void revertPayment(Long paymentId, Long operatorId) {
        if (paymentId == null) {
            return;
        }
        RevertOutpatientPaymentFromSettlingCommand revertCmd = new RevertOutpatientPaymentFromSettlingCommand();
        revertCmd.setPaymentId(paymentId);
        revertCmd.setOperatorId(operatorId);
        try {
            paymentReconciliationDomainService.revertOutpatientPaymentFromSettling(revertCmd);
        } catch (BusinessException ex) {
            log.warn("revert SETTLING skipped paymentId={}: {}", paymentId, ex.getMessage());
        }
    }

    private void transition(
        Long coordinationId,
        YbCoordinationState target,
        String errorMsg,
        String platformSetlId,
        String responseSnapshot) {
        TransitionYbCoordinationCommand cmd = new TransitionYbCoordinationCommand();
        cmd.setCoordinationId(coordinationId);
        cmd.setTargetState(target);
        cmd.setErrorMsg(errorMsg);
        cmd.setPlatformSetlId(platformSetlId);
        cmd.setResponseSnapshot(responseSnapshot);
        coordinationDomainService.transition(cmd);
    }

    private OutpatientOpSettleCoordinationSnapshot readSnapshot(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, OutpatientOpSettleCoordinationSnapshot.class);
        } catch (Exception ex) {
            return null;
        }
    }
}
