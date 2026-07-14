/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.inpatientbilling.support;

import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.AutoRollLineContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * IF-IP-ARG-08：单条滚费落账（IF-IP-EXE-02 长期医嘱执行轨；Charge {@code order-execute}）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NurseStationAutoRollLinePoster {

    private final InpatientAutoRollOrderExecutionOrchestrator inpatientAutoRollOrderExecutionOrchestrator;

    /**
     * 单条滚费执行落账。
     *
     * @param line        落账行
     * @param operatorId  任务操作人
     * @param performerId 主执行人（责任护士）
     * @return 执行结果
     */
    public InpatientAutoRollOrderExecutionOrchestrator.Outcome postLine(
        AutoRollLineContext line,
        Long operatorId,
        Long performerId) {
        try {
            InpatientAutoRollServiceOrderExecuteSpec command = new InpatientAutoRollServiceOrderExecuteSpec();
            command.setServiceRequestId(line.getServiceRequestId());
            command.setEncounterId(line.getEncounterId());
            command.setPatientId(line.getPatientId());
            command.setAccountId(line.getAccountId());
            command.setOrganizationId(line.getOrganizationId());
            command.setBillDate(line.getBillDate());
            command.setQuantity(line.getQuantity());
            command.setQuantityUnit(line.getUnitCode());
            command.setPreferredDefinitionId(line.getChargeDefinitionId());
            command.setPerformerId(performerId);
            command.setOperatorId(operatorId);
            return inpatientAutoRollOrderExecutionOrchestrator.executeLongTermServiceOrderSlot(command);
        } catch (RuntimeException ex) {
            log.warn(
                "住院滚费单条落账失败 encounterId={} serviceRequestId={} chargeDefinitionId={}",
                line.getEncounterId(),
                line.getServiceRequestId(),
                line.getChargeDefinitionId(),
                ex);
            return InpatientAutoRollOrderExecutionOrchestrator.Outcome.FAILED;
        }
    }
}
