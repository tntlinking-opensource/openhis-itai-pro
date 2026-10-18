/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.medicaltech.charging.support;

import com.zxplat.application.shared.nursestation.ProcedureExecutionPerformerOrchestrator;
import com.zxplat.domain.clinical.procedure.command.CancelProcedureRecordCommand;
import com.zxplat.domain.clinical.procedure.command.CompleteProcedureRecordCommand;
import com.zxplat.domain.clinical.procedure.command.CreateProcedureRecordCommand;
import com.zxplat.domain.clinical.procedure.command.StartProcedureExecutionCommand;
import com.zxplat.domain.clinical.procedure.queries.model.ProcedureRecordQueryModel;
import com.zxplat.domain.clinical.procedure.service.ProcedureDomainService;
import com.zxplat.domain.clinical.procedure.service.ProcedureQueryService;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.MedicalTechChargingConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.enums.EventStatus;
import com.zxplat.domain.share.enums.TherapyType;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 医技划价诊疗 Procedure 写编排（AS-MT-CHG-001 IF-MT-CHG-07/04）。
 *
 * <p>确认划价：{@code create→start→挂主执行人→complete}（登录 TECH 医务人员；
 * {@code Procedure.org_id}=登录 TECH）。取消划价：对 COMPLETED 行追加 CANCEL 对冲。
 * 与护士划价 Procedure 同构，仅来源文案与包边界不同（禁止跨包 import 护士站 support）。</p>
 */
@Component
@RequiredArgsConstructor
public class MedicalTechChargingProcedureOrchestrator {

    private final ProcedureDomainService procedureDomainService;
    private final ProcedureQueryService procedureQueryService;
    private final ProcedureExecutionPerformerOrchestrator procedureExecutionPerformerOrchestrator;

    /**
     * 医技划价诊疗：创建并完成 Procedure，写入记录人与主执行人。
     *
     * @param serviceRequestId 医技划价诊疗请求主键
     * @param encounterId      就诊主键
     * @param patientId        患者主键
     * @param practitionerId   当前登录医务人员
     * @param occurrenceTime   执行发生时刻；可空时取当前时刻
     * @param loginTechOrgId   登录 TECH（写入 Procedure.org_id）
     * @return 已完成 Procedure 主键，供 Charge 挂 procedure_id
     */
    public Long registerCompletedMedicalTechPricingProcedure(
        Long serviceRequestId,
        Long encounterId,
        Long patientId,
        Long practitionerId,
        LocalDateTime occurrenceTime,
        Long loginTechOrgId) {
        // Gate：执行人须由 App 层从登录会话解析
        if (practitionerId == null) {
            throw new BusinessException(
                ErrorCodeConstants.PROCEDURE_INVALID_ARGUMENT,
                "划价执行人缺失，请重新登录后重试");
        }
        LocalDateTime effectiveTime = occurrenceTime;
        if (effectiveTime == null) {
            effectiveTime = LocalDateTime.now();
        }
        // Pre：组装 create 命令，锚定 wkf_service_request + TEMPORARY；org_id=登录 TECH
        CreateProcedureRecordCommand createCmd = new CreateProcedureRecordCommand();
        createCmd.setOperatorId(practitionerId);
        createCmd.setRequestId(serviceRequestId);
        createCmd.setRequestTable(TableNameConstants.WKF_SERVICE_REQUEST);
        createCmd.setEncounterId(encounterId);
        createCmd.setPatientId(patientId);
        createCmd.setCategoryEnum(TherapyType.TEMPORARY.getCode());
        createCmd.setOccurrenceTime(effectiveTime);
        createCmd.setOrgId(loginTechOrgId);
        // Cmd：创建 → 启动 → 挂主执行人 → 完成
        Long procedureId = this.procedureDomainService.createProcedureRecord(createCmd);
        StartProcedureExecutionCommand startCmd = new StartProcedureExecutionCommand();
        startCmd.setProcedureId(procedureId);
        startCmd.setOperatorId(practitionerId);
        this.procedureDomainService.startProcedureExecution(startCmd);
        this.procedureExecutionPerformerOrchestrator.registerPrimaryPerformer(
            procedureId, practitionerId, practitionerId, effectiveTime);
        CompleteProcedureRecordCommand completeCmd = new CompleteProcedureRecordCommand();
        completeCmd.setProcedureId(procedureId);
        completeCmd.setOperatorId(practitionerId);
        completeCmd.setResultText(MedicalTechChargingConstants.MEDICAL_TECH_PRICING_PROCEDURE_RESULT_TEXT);
        completeCmd.setReportJson(null);
        completeCmd.setRecordedTime(effectiveTime);
        this.procedureDomainService.completeProcedureRecord(completeCmd);
        return procedureId;
    }

    /**
     * 取消划价：对指定诊疗请求下尚未对冲的 COMPLETED Procedure 追加 CANCEL 行。
     *
     * @param serviceRequestIds 待取消医技划价诊疗请求主键集合
     * @param operatorId        当前登录医务人员
     */
    public void cancelCompletedMedicalTechPricingProcedures(
        Collection<Long> serviceRequestIds,
        Long operatorId) {
        if (serviceRequestIds == null || serviceRequestIds.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (Long serviceRequestId : serviceRequestIds) {
            if (serviceRequestId == null) {
                continue;
            }
            List<ProcedureRecordQueryModel> procedures = this.procedureQueryService.getProcedureRecords(
                serviceRequestId, TableNameConstants.WKF_SERVICE_REQUEST);
            for (ProcedureRecordQueryModel completedRow : procedures) {
                if (completedRow == null || completedRow.getId() == null) {
                    continue;
                }
                // 仅对冲 COMPLETED 行
                if (!EventStatus.COMPLETED.getCode().equals(completedRow.getStatusEnum())) {
                    continue;
                }
                // 已被对冲的不再重复
                if (this.isProcedureAlreadyOffset(serviceRequestId, completedRow.getId())) {
                    continue;
                }
                this.appendProcedureCancelOffset(completedRow, operatorId, completedRow.getId(), now);
            }
        }
    }

    /**
     * 判断 COMPLETED 行是否已被同请求下的 CANCEL 行对冲。
     *
     * @param serviceRequestId     诊疗请求主键
     * @param completedProcedureId COMPLETED 行主键
     * @return 已对冲时 true
     */
    private boolean isProcedureAlreadyOffset(Long serviceRequestId, Long completedProcedureId) {
        List<ProcedureRecordQueryModel> siblings = this.procedureQueryService.getProcedureRecords(
            serviceRequestId, TableNameConstants.WKF_SERVICE_REQUEST);
        for (ProcedureRecordQueryModel sibling : siblings) {
            if (sibling == null) {
                continue;
            }
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
     * 追加 CANCEL 对冲 Procedure。
     *
     * @param completedRow               被对冲 COMPLETED 行
     * @param operatorId                 操作人
     * @param offsetCompletedProcedureId 被对冲 COMPLETED 行主键
     * @param now                        当前时刻
     */
    private void appendProcedureCancelOffset(
        ProcedureRecordQueryModel completedRow,
        Long operatorId,
        Long offsetCompletedProcedureId,
        LocalDateTime now) {
        CreateProcedureRecordCommand createCmd = new CreateProcedureRecordCommand();
        createCmd.setOperatorId(operatorId);
        createCmd.setSkipDuplicateRequestAnchorCheck(Boolean.TRUE);
        createCmd.setSkipOccurrenceSlotCheck(Boolean.TRUE);
        createCmd.setRequestId(completedRow.getRequestId());
        createCmd.setRequestTable(completedRow.getRequestTable());
        createCmd.setEncounterId(completedRow.getEncounterId());
        createCmd.setPatientId(completedRow.getPatientId());
        createCmd.setCategoryEnum(completedRow.getCategoryEnum());
        LocalDateTime occurrenceTime = completedRow.getOccurrenceTime();
        if (occurrenceTime == null) {
            occurrenceTime = now;
        }
        createCmd.setOccurrenceTime(occurrenceTime);
        // CANCEL 对冲须同侧：继承被对冲 COMPLETED 的执行科室
        createCmd.setOrgId(completedRow.getOrgId());
        Long newProcedureId = this.procedureDomainService.createProcedureRecord(createCmd);
        StartProcedureExecutionCommand startCmd = new StartProcedureExecutionCommand();
        startCmd.setProcedureId(newProcedureId);
        startCmd.setOperatorId(operatorId);
        this.procedureDomainService.startProcedureExecution(startCmd);
        this.procedureExecutionPerformerOrchestrator.registerPrimaryPerformer(
            newProcedureId, operatorId, operatorId, now);
        CancelProcedureRecordCommand cancelCmd = new CancelProcedureRecordCommand();
        cancelCmd.setProcedureId(newProcedureId);
        cancelCmd.setStatusReasonText(MedicalTechChargingConstants.MEDICAL_TECH_PRICING_CANCEL_PROCEDURE_REASON);
        cancelCmd.setRefundId(offsetCompletedProcedureId);
        cancelCmd.setOperatorId(operatorId);
        this.procedureDomainService.cancelProcedureRecord(cancelCmd);
    }
}
