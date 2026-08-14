/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.inpatientbilling.support;

import com.openhis.health.application.inpatient.nursestation.inpatientbilling.mapper.NurseStationInpatientBillingMapper;
import com.openhis.health.domain.clinical.procedure.command.CancelProcedureRecordCommand;
import com.openhis.health.domain.clinical.procedure.command.CompleteProcedureRecordCommand;
import com.openhis.health.domain.clinical.procedure.command.CreateProcedureRecordCommand;
import com.openhis.health.domain.clinical.procedure.command.StartProcedureExecutionCommand;
import com.openhis.health.domain.clinical.procedure.queries.model.ProcedureRecordQueryModel;
import com.openhis.health.domain.clinical.procedure.service.ProcedureDomainService;
import com.openhis.health.domain.clinical.procedure.service.ProcedureQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.EventStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.health.application.shared.nursestation.ProcedureExecutionPerformerOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 住院护士划价诊疗 Procedure 写编排（AS-IP-NUR-005 IF-IP-PRC-02/04/06）。
 *
 * <p>确认划价：{@code create→start→complete} 并登记主执行人（登录医务人员）；取消划价：对 COMPLETED 行追加 CANCEL 对冲。
 * 对齐老系统 {@code addInNurseBilling} 诊疗路径与 **BR-RPT-NWL-020**；**耗材划价仍不写 Procedure**。</p>
 */
@Component
@RequiredArgsConstructor
public class NurseStationInpatientBillingProcedureOrchestrator {

    private final ProcedureDomainService procedureDomainService;
    /** B.6.4：按 request 锚点只读回查 Procedure，不写临床聚合。 */
    private final ProcedureQueryService procedureQueryService;
    private final ProcedureExecutionPerformerOrchestrator procedureExecutionPerformerOrchestrator;
    private final NurseStationInpatientBillingMapper nurseStationInpatientBillingMapper;

    /**
     * 护士划价诊疗：创建并完成 Procedure，写入记录人与主执行人（IF-IP-PRC-02/04，BR-IP-PRC-007）。
     *
     * <p>{@code practitionerId} 须为 {@link com.openhis.health.application.shared.validator.CurrentPractitionerResolver}
     * 解析的登录医务人员，同时写入 {@code cln_procedure.recorder_id} 与 {@code cln_procedure_performer}。</p>
     *
     * @param serviceRequestId 护士划价诊疗请求主键（{@code wkf_service_request.id}）
     * @param encounterId      就诊主键
     * @param patientId        患者主键
     * @param practitionerId   当前登录医务人员（{@code adm_practitioner.id}），兼操作人与主执行人
     * @param occurrenceTime   执行发生时刻；可空时取当前时刻
     * @return 已完成 Procedure 主键，供 Charge 挂 {@code procedure_id}
     * @throws BusinessException 执行人缺失时 {@link ErrorCodeConstants#PROCEDURE_INVALID_ARGUMENT}
     */
    public Long registerCompletedNursePricingProcedure(
        Long serviceRequestId,
        Long encounterId,
        Long patientId,
        Long practitionerId,
        LocalDateTime occurrenceTime) {
        // Gate：执行人须由 App 层从登录会话解析，禁止前端透传或空值落库
        if (practitionerId == null) {
            throw new BusinessException(
                ErrorCodeConstants.PROCEDURE_INVALID_ARGUMENT,
                "划价执行人缺失，请重新登录后重试");
        }
        LocalDateTime effectiveTime = occurrenceTime;
        if (effectiveTime == null) {
            // 缺省与 Charge.occurrence_time 同批取当前时刻，保证费用与执行时间对齐
            effectiveTime = LocalDateTime.now();
        }
        // Pre：组装 create 命令，锚定 wkf_service_request + TEMPORARY
        CreateProcedureRecordCommand createCmd = nurseStationInpatientBillingMapper.toCreateNursePricingProcedureRecordCommand(
            serviceRequestId,
            encounterId,
            patientId,
            practitionerId,
            effectiveTime);
        // Cmd：Procedure 三态收口为 COMPLETED；complete 写入 recorder_id/recorded_time
        Long procedureId = procedureDomainService.createProcedureRecord(createCmd);
        StartProcedureExecutionCommand startCmd =
            nurseStationInpatientBillingMapper.toStartProcedureExecutionCommand(procedureId, practitionerId);
        procedureDomainService.startProcedureExecution(startCmd);
        CompleteProcedureRecordCommand completeCmd =
            nurseStationInpatientBillingMapper.toCompleteNursePricingProcedureRecordCommand(
                procedureId, practitionerId, effectiveTime);
        procedureDomainService.completeProcedureRecord(completeCmd);
        // Cmd：主执行人写入 cln_procedure_performer，供 AS-RPT-002 按护士筛选净执行次数
        procedureExecutionPerformerOrchestrator.registerPrimaryPerformer(
            procedureId, practitionerId, practitionerId, effectiveTime);
        return procedureId;
    }

    /**
     * 取消划价：对指定诊疗请求下尚未对冲的 COMPLETED Procedure 追加 CANCEL 行（IF-IP-PRC-06，BR-IP-PRC-026）。
     *
     * @param serviceRequestIds 待取消护士划价诊疗请求主键集合
     * @param operatorId        当前登录医务人员（{@code adm_practitioner.id}）
     */
    public void cancelCompletedNursePricingProcedures(Collection<Long> serviceRequestIds, Long operatorId) {
        if (serviceRequestIds == null || serviceRequestIds.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        // Loop：逐诊疗请求查找 COMPLETED 行并追加 CANCEL 对冲，须在 void Charge 之前执行
        for (Long serviceRequestId : serviceRequestIds) {
            if (serviceRequestId == null) {
                continue;
            }
            List<ProcedureRecordQueryModel> procedures = procedureQueryService.getProcedureRecords(
                serviceRequestId, TableNameConstants.WKF_SERVICE_REQUEST);
            for (ProcedureRecordQueryModel completedRow : procedures) {
                if (completedRow == null || completedRow.getId() == null) {
                    continue;
                }
                // 仅对冲 COMPLETED 行；CANCEL/STOPPED 等跳过
                if (!EventStatus.COMPLETED.getCode().equals(completedRow.getStatusEnum())) {
                    continue;
                }
                // 已被 refund_id 指向的 COMPLETED 不再重复对冲
                if (this.isProcedureAlreadyOffset(serviceRequestId, completedRow.getId())) {
                    continue;
                }
                this.appendProcedureCancelOffset(completedRow, operatorId, completedRow.getId(), now);
            }
        }
    }

    /**
     * 判断 COMPLETED 行是否已被同请求下的 CANCEL 行对冲（DM-CLN-004 §6.2.6 净执行口径）。
     *
     * @param serviceRequestId     诊疗请求主键
     * @param completedProcedureId COMPLETED 行主键
     * @return 已对冲时 true
     */
    private boolean isProcedureAlreadyOffset(Long serviceRequestId, Long completedProcedureId) {
        List<ProcedureRecordQueryModel> siblings = procedureQueryService.getProcedureRecords(
            serviceRequestId, TableNameConstants.WKF_SERVICE_REQUEST);
        for (ProcedureRecordQueryModel sibling : siblings) {
            if (sibling == null) {
                continue;
            }
            // 仅 CANCEL 终态且 refund_id 指向原 COMPLETED 时视为已对冲
            if (!EventStatus.isCancelExecutionStatusCode(sibling.getStatusEnum())) {
                continue;
            }
            if (completedProcedureId.equals(sibling.getRefundId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 追加 CANCEL 对冲 Procedure（create → start → cancel，与取消执行链路同构）。
     *
     * @param completedRow               被对冲 COMPLETED 行（occurrence 继承来源）
     * @param operatorId                 当前登录医务人员
     * @param offsetCompletedProcedureId 被对冲 COMPLETED 行主键（写入新行 refund_id）
     * @param now                        当前时刻（occurrence 回落用）
     */
    private void appendProcedureCancelOffset(
        ProcedureRecordQueryModel completedRow,
        Long operatorId,
        Long offsetCompletedProcedureId,
        LocalDateTime now) {
        CreateProcedureRecordCommand createCmd = nurseStationInpatientBillingMapper.toCreateProcedureCancelOffsetCommand(
            completedRow, operatorId, now);
        Long newProcedureId = procedureDomainService.createProcedureRecord(createCmd);
        StartProcedureExecutionCommand startCmd =
            nurseStationInpatientBillingMapper.toStartProcedureExecutionCommand(newProcedureId, operatorId);
        procedureDomainService.startProcedureExecution(startCmd);
        CancelProcedureRecordCommand cancelCmd = nurseStationInpatientBillingMapper.toCancelNursePricingProcedureRecordCommand(
            newProcedureId,
            InpatientNurseStationConstants.INPATIENT_NURSE_PRICING_CANCEL_PROCEDURE_REASON,
            offsetCompletedProcedureId,
            operatorId);
        procedureDomainService.cancelProcedureRecord(cancelCmd);
    }
}
