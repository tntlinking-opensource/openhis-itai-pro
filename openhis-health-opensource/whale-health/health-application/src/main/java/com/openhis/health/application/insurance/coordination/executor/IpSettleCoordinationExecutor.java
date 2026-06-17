/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.insurance.coordination.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.health.application.inpatient.financial.settle.service.InpatientSettleLocalCompletionService;
import com.openhis.health.application.insurance.coordination.dto.InpatientIpSettleCoordinationSnapshot;
import com.openhis.health.domain.financial.paymentreconciliation.command.RevertInpatientPaymentFromSettlingCommand;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationDomainService;
import com.openhis.health.domain.insurance.coordination.command.AppendYbCoordinationStepCommand;
import com.openhis.health.domain.insurance.coordination.command.TransitionYbCoordinationCommand;
import com.openhis.health.domain.insurance.coordination.entity.YbCoordination;
import com.openhis.health.domain.insurance.coordination.repository.YbCoordinationStepRepository;
import com.openhis.health.domain.insurance.coordination.service.YbCoordinationDomainService;
import com.openhis.health.domain.share.constants.InsuranceCoordinationConstants;
import com.openhis.health.domain.share.enums.YbCoordinationBizType;
import com.openhis.health.domain.share.enums.YbCoordinationState;
import com.openhis.whale.chs.jilin.port.InpatientInsuranceSettlementPort;
import com.openhis.whale.chs.jilin.port.model.IpInsuranceConfirmCommand;
import com.openhis.whale.chs.jilin.port.model.IpInsuranceConfirmResult;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 住院医保结算协同单执行器（IP_SETTLE：2304 平台 + 本地落账）。
 */
@Slf4j
@Component
@ConditionalOnBean(InpatientInsuranceSettlementPort.class)
@RequiredArgsConstructor
public class IpSettleCoordinationExecutor implements YbCoordinationStepExecutor {

    private final YbCoordinationDomainService coordinationDomainService;
    private final YbCoordinationStepRepository stepRepository;
    private final InpatientSettleLocalCompletionService localCompletionService;
    private final PaymentReconciliationDomainService paymentReconciliationDomainService;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private InpatientInsuranceSettlementPort inpatientInsuranceSettlementPort;

    @Override
    public YbCoordinationBizType bizType() {
        return YbCoordinationBizType.IP_SETTLE;
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
        InpatientIpSettleCoordinationSnapshot snapshot = this.readSnapshot(coordination.getRequestSnapshot());
        if (inpatientInsuranceSettlementPort == null || snapshot == null) {
            this.failPlatform(coordination, "医保 Port 未启用或协同单上下文缺失");
            return;
        }
        try {
            IpInsuranceConfirmCommand command = new IpInsuranceConfirmCommand();
            command.setPaymentDraftId(snapshot.getPaymentDraftId());
            command.setPreSettleId(snapshot.getPreSettleId());
            command.setEncounterId(snapshot.getEncounterId());
            IpInsuranceConfirmResult ybResult = inpatientInsuranceSettlementPort.confirmBeforeLocalSettle(command);
            String setlId;
            if (ybResult != null) {
                setlId = ybResult.getSetlId();
            } else {
                setlId = null;
            }
            int stepOrder = stepRepository.countByCoordinationIdAndIsDeletedFalse(coordination.getId()) + 1;
            AppendYbCoordinationStepCommand stepCmd = new AppendYbCoordinationStepCommand();
            stepCmd.setCoordinationId(coordination.getId());
            stepCmd.setStepOrder(stepOrder);
            stepCmd.setInfno(InsuranceCoordinationConstants.IP_SETTLE_PLATFORM_INFNO);
            stepCmd.setMsgid(coordination.getMsgid());
            stepCmd.setState(YbCoordinationState.COMPLETED.getCode());
            stepCmd.setResponseSnapshot(setlId);
            coordinationDomainService.appendStep(stepCmd);
            this.transition(
                coordination.getId(),
                YbCoordinationState.PLATFORM_OK,
                null,
                setlId,
                setlId);
            YbCoordination refreshed = coordinationDomainService.requireById(coordination.getId());
            this.runLocalPhase(refreshed);
        } catch (BusinessException ex) {
            this.failPlatform(coordination, ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("IP_SETTLE platform unknown: coordinationId={}", coordination.getId(), ex);
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
        InpatientIpSettleCoordinationSnapshot snapshot = this.readSnapshot(coordination.getRequestSnapshot());
        try {
            InpatientSettleLocalCompletionService.LocalCompletionResult result =
                localCompletionService.completeFromSnapshot(snapshot, coordination.getPlatformSetlId());
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
        if (nextRetry >= InsuranceCoordinationConstants.IP_SETTLE_LOCAL_MAX_RETRIES) {
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

    private void revertPayment(Long paymentId, Long operatorId) {
        if (paymentId == null) {
            return;
        }
        RevertInpatientPaymentFromSettlingCommand revertCmd = new RevertInpatientPaymentFromSettlingCommand();
        revertCmd.setPaymentId(paymentId);
        revertCmd.setOperatorId(operatorId);
        try {
            paymentReconciliationDomainService.revertInpatientPaymentFromSettling(revertCmd);
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

    private InpatientIpSettleCoordinationSnapshot readSnapshot(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, InpatientIpSettleCoordinationSnapshot.class);
        } catch (Exception ex) {
            return null;
        }
    }
}
