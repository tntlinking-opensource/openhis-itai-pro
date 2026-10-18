/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.inpatientbilling.support;

import com.zxplat.application.inpatient.nursestation.inpatientbilling.dto.AutoRollLineContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * IF-IP-ARG-08：单条滚费落账（IF-IP-EXE-02 长期医嘱执行轨；Charge {@code order-execute}）。
 *
 * <p>单条 {@code REQUIRES_NEW}：Procedure 与 Charge 同事务提交；失败整单回滚，禁止半成功占槽。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NurseStationAutoRollLinePoster {

    private final InpatientAutoRollOrderExecutionOrchestrator inpatientAutoRollOrderExecutionOrchestrator;
    private final AutoRollBillingActorSecuritySupport autoRollBillingActorSecuritySupport;
    private final PlatformTransactionManager platformTransactionManager;

    /**
     * 单条滚费执行落账。
     *
     * <p>操作人与主执行人均为计费人（配置项）或责任护士（护理分支）；审计 {@code created_by}
     * 亦写入计费人绑定的系统用户，禁止回落种子 admin。</p>
     *
     * @param line                  落账行（含 occurrenceTime）
     * @param billingPractitionerId 计费人医务人员主键（同时作为 operator / performer）
     * @return 执行结果
     */
    public InpatientAutoRollOrderExecutionOrchestrator.Outcome postLine(
        AutoRollLineContext line,
        Long billingPractitionerId) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(platformTransactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        InpatientAutoRollOrderExecutionOrchestrator.Outcome outcome = transactionTemplate.execute(status -> {
            try {
                InpatientAutoRollOrderExecutionOrchestrator.Outcome result =
                    autoRollBillingActorSecuritySupport.runAsBillingPractitioner(
                        billingPractitionerId,
                        () -> this.executeSlot(line, billingPractitionerId));
                if (result == InpatientAutoRollOrderExecutionOrchestrator.Outcome.FAILED) {
                    // 失败必须回滚，避免只留下 Procedure、下次空跳过
                    status.setRollbackOnly();
                }
                return result;
            } catch (RuntimeException ex) {
                status.setRollbackOnly();
                throw ex;
            }
        });
        if (outcome == null) {
            return InpatientAutoRollOrderExecutionOrchestrator.Outcome.FAILED;
        }
        return outcome;
    }

    /**
     * 组装命令并执行单槽位（须已在事务与计费人安全上下文中）。
     *
     * @param line                  落账行
     * @param billingPractitionerId 计费人
     * @return 执行结果
     */
    private InpatientAutoRollOrderExecutionOrchestrator.Outcome executeSlot(
        AutoRollLineContext line,
        Long billingPractitionerId) {
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
            command.setOccurrenceTime(line.getOccurrenceTime());
            // 计费人同时写入 operator / performer（BR-IP-ARG-019/021）
            command.setPerformerId(billingPractitionerId);
            command.setOperatorId(billingPractitionerId);
            return inpatientAutoRollOrderExecutionOrchestrator.executeLongTermServiceOrderSlot(command);
        } catch (RuntimeException ex) {
            log.warn(
                "住院滚费单条落账失败 encounterId={} serviceRequestId={} chargeDefinitionId={} billingPractitionerId={}",
                line.getEncounterId(),
                line.getServiceRequestId(),
                line.getChargeDefinitionId(),
                billingPractitionerId,
                ex);
            return InpatientAutoRollOrderExecutionOrchestrator.Outcome.FAILED;
        }
    }
}
