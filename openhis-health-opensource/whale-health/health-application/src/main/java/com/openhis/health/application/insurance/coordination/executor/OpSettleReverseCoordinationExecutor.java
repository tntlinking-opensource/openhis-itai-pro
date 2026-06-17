/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.insurance.coordination.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.health.application.insurance.coordination.dto.OutpatientOpSettleReverseCoordinationSnapshot;
import com.openhis.health.domain.insurance.coordination.command.AppendYbCoordinationStepCommand;
import com.openhis.health.domain.insurance.coordination.command.TransitionYbCoordinationCommand;
import com.openhis.health.domain.insurance.coordination.entity.YbCoordination;
import com.openhis.health.domain.insurance.coordination.repository.YbCoordinationStepRepository;
import com.openhis.health.domain.insurance.coordination.service.YbCoordinationDomainService;
import com.openhis.health.domain.share.constants.InsuranceCoordinationConstants;
import com.openhis.health.domain.share.enums.YbCoordinationBizType;
import com.openhis.health.domain.share.enums.YbCoordinationState;
import com.openhis.whale.chs.jilin.port.OutpatientInsuranceSettlementPort;
import com.openhis.whale.chs.jilin.port.model.OpInsuranceRevokeSettleCommand;
import com.openhis.whale.chs.jilin.port.model.OpInsuranceSettleRecordResult;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 门诊医保结算撤销协同单执行器（OP_SETTLE_REVERSE：2208）。
 */
@Slf4j
@Component
@ConditionalOnBean(OutpatientInsuranceSettlementPort.class)
@RequiredArgsConstructor
public class OpSettleReverseCoordinationExecutor implements YbCoordinationStepExecutor {

    private final YbCoordinationDomainService coordinationDomainService;
    private final YbCoordinationStepRepository stepRepository;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private OutpatientInsuranceSettlementPort outpatientInsuranceSettlementPort;

    @Override
    public YbCoordinationBizType bizType() {
        return YbCoordinationBizType.OP_SETTLE_REVERSE;
    }

    @Override
    public void dispatch(YbCoordination coordination) {
        if (coordination == null || coordination.getId() == null) {
            return;
        }
        if (YbCoordinationState.CREATED.equals(coordination.stateEnum())) {
            this.runPlatformPhase(coordination);
        }
    }

    private void runPlatformPhase(YbCoordination coordination) {
        this.transition(coordination.getId(), YbCoordinationState.PLATFORM_RUNNING, null, null, null);
        OutpatientOpSettleReverseCoordinationSnapshot snapshot =
            this.readSnapshot(coordination.getRequestSnapshot());
        if (outpatientInsuranceSettlementPort == null || snapshot == null) {
            this.failPlatform(coordination, "医保 Port 未启用或协同单上下文缺失");
            return;
        }
        List<Long> recordIds = snapshot.getSettlementRecordIds();
        if (recordIds == null || recordIds.isEmpty()) {
            List<OpInsuranceSettleRecordResult> records =
                outpatientInsuranceSettlementPort.listSettlementsByPayment(snapshot.getOriginalPaymentId());
            recordIds = new ArrayList<>();
            for (OpInsuranceSettleRecordResult row : records) {
                if (row != null && row.getSettlementRecordId() != null) {
                    recordIds.add(row.getSettlementRecordId());
                }
            }
        }
        if (recordIds.isEmpty()) {
            this.transition(coordination.getId(), YbCoordinationState.COMPLETED, null, null, "NO_OP");
            return;
        }
        try {
            List<String> revokedSetlIds = new ArrayList<>();
            int stepOrder = stepRepository.countByCoordinationIdAndIsDeletedFalse(coordination.getId());
            for (Long recordId : recordIds) {
                OpInsuranceRevokeSettleCommand command = new OpInsuranceRevokeSettleCommand();
                command.setSettlementRecordId(recordId);
                command.setPaymentId(snapshot.getOriginalPaymentId());
                var result = outpatientInsuranceSettlementPort.revokeSettle(command);
                stepOrder += 1;
                AppendYbCoordinationStepCommand stepCmd = new AppendYbCoordinationStepCommand();
                stepCmd.setCoordinationId(coordination.getId());
                stepCmd.setStepOrder(stepOrder);
                stepCmd.setInfno(InsuranceCoordinationConstants.OP_SETTLE_REVERSE_PLATFORM_INFNO);
                stepCmd.setMsgid(coordination.getMsgid());
                stepCmd.setState(YbCoordinationState.COMPLETED.getCode());
                String responseSnapshot;
                if (result != null) {
                    responseSnapshot = result.getSetlId();
                } else {
                    responseSnapshot = null;
                }
                stepCmd.setResponseSnapshot(responseSnapshot);
                coordinationDomainService.appendStep(stepCmd);
                if (result != null && StringUtils.isNotBlank(result.getSetlId())) {
                    revokedSetlIds.add(result.getSetlId().trim());
                }
            }
            String joined = String.join(",", revokedSetlIds);
            this.transition(coordination.getId(), YbCoordinationState.COMPLETED, null, joined, joined);
        } catch (BusinessException ex) {
            this.failPlatform(coordination, ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("OP_SETTLE_REVERSE platform unknown: coordinationId={}", coordination.getId(), ex);
            this.transition(
                coordination.getId(),
                YbCoordinationState.UNKNOWN,
                ex.getMessage(),
                null,
                null);
        }
    }

    private void failPlatform(YbCoordination coordination, String errorMsg) {
        this.transition(coordination.getId(), YbCoordinationState.FAILED, errorMsg, null, null);
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

    private OutpatientOpSettleReverseCoordinationSnapshot readSnapshot(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, OutpatientOpSettleReverseCoordinationSnapshot.class);
        } catch (Exception ex) {
            return null;
        }
    }
}
