/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.nursestation;

import com.zxplat.domain.clinical.procedure.command.AppendProcedurePerformerCommand;
import com.zxplat.domain.clinical.procedure.service.ProcedureDomainService;
import com.zxplat.domain.share.constants.ClinicalProcedureConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 护士站 Procedure 执行人写编排：须在终态前向 {@code cln_procedure_performer} 登记执行人。
 *
 * <p>门诊/住院护士站执行与取消、医技工作站执行与取消、取消划价、住院划价诊疗、住院滚费长期执行共用本类；
 * 入参须为 {@code adm_practitioner.id}（B.6.5）。</p>
 * <p>调用方编排顺序统一为 {@code create→start→本类登记→complete/cancel}（DM-CLN-004
 * {@code assertPerformerAppendable}）；不执行路径在领域 {@code skipProcedureRecord} 内同构挂 PRIMARY。</p>
 * <p>普通路径：主执行人 PRIMARY（1:1）；手术/麻醉弹窗：多角色拷贝（AS-IP-NUR-016）。</p>
 */
@Component
@RequiredArgsConstructor
public class ProcedureExecutionPerformerOrchestrator {

    /** 处置领域服务：追加执行人写路径。 */
    private final ProcedureDomainService procedureDomainService;

    /**
     * 登记主执行人（与执行记录 1:1，写入 {@code cln_procedure_performer}）。
     *
     * @param procedureId     执行记录主键
     * @param practitionerId  执行医务人员主键（{@code adm_practitioner.id}）
     * @param operatorId      操作人主键（同 B.6.5 医务人员口径）
     * @param executionTime   执行发生时刻；允许 null 时由领域层按当前时刻处理
     */
    public void registerPrimaryPerformer(
        Long procedureId,
        Long practitionerId,
        Long operatorId,
        LocalDateTime executionTime) {
        // 缺主键或执行人则跳过，避免无效 performer 行
        if (procedureId == null || practitionerId == null) {
            return;
        }
        AppendProcedurePerformerCommand command = new AppendProcedurePerformerCommand();
        command.setProcedureId(procedureId);
        command.setOperatorId(operatorId);
        AppendProcedurePerformerCommand.ProcedurePerformerItemCommand item =
            new AppendProcedurePerformerCommand.ProcedurePerformerItemCommand();
        item.setPractitionerId(practitionerId);
        item.setFunctionCode(ClinicalProcedureConstants.PROCEDURE_PERFORMER_FUNCTION_PRIMARY);
        item.setStartTime(executionTime);
        command.setItem(item);
        this.procedureDomainService.appendProcedurePerformer(command);
    }

    /**
     * 手术/麻醉执行：按角色批量挂入执行人（空 id 跳过；BR-IP-SAE-004/005）。
     *
     * <p>角色码一律 {@link ClinicalProcedureConstants}：SURGEON / FIRST_ASSISTANT / SECOND_ASSISTANT /
     * ANESTHETIST / NURSE；禁止院内别名。</p>
     *
     * @param procedureId        执行记录主键
     * @param operatorId         操作人
     * @param executionTime      performer 开始时刻
     * @param surgeonId          主刀（可空）
     * @param firstAssistantId   一助（可空）
     * @param secondAssistantId  二助（可空）
     * @param anesthetistId      麻醉医师（可空）
     * @param nurseId            配合护士（可空；弹窗可覆盖申请）
     */
    public void registerSurgeryAnesthPerformers(
        Long procedureId,
        Long operatorId,
        LocalDateTime executionTime,
        Long surgeonId,
        Long firstAssistantId,
        Long secondAssistantId,
        Long anesthetistId,
        Long nurseId) {
        // 无执行记录主键则无法挂入
        if (procedureId == null) {
            return;
        }
        List<AppendProcedurePerformerCommand.ProcedurePerformerItemCommand> items = new ArrayList<>();
        this.appendRoleIfPresent(
            items, surgeonId, ClinicalProcedureConstants.PROCEDURE_PERFORMER_FUNCTION_SURGEON, executionTime);
        this.appendRoleIfPresent(
            items,
            firstAssistantId,
            ClinicalProcedureConstants.PROCEDURE_PERFORMER_FUNCTION_FIRST_ASSISTANT,
            executionTime);
        this.appendRoleIfPresent(
            items,
            secondAssistantId,
            ClinicalProcedureConstants.PROCEDURE_PERFORMER_FUNCTION_SECOND_ASSISTANT,
            executionTime);
        this.appendRoleIfPresent(
            items, anesthetistId, ClinicalProcedureConstants.PROCEDURE_PERFORMER_FUNCTION_ANESTHETIST, executionTime);
        this.appendRoleIfPresent(
            items, nurseId, ClinicalProcedureConstants.PROCEDURE_PERFORMER_FUNCTION_NURSE, executionTime);
        // 申请与弹窗均无人员时不写库
        if (items.isEmpty()) {
            return;
        }
        AppendProcedurePerformerCommand command = new AppendProcedurePerformerCommand();
        command.setProcedureId(procedureId);
        command.setOperatorId(operatorId);
        command.setItems(items);
        this.procedureDomainService.appendProcedurePerformer(command);
    }

    /**
     * 非空人员主键时追加一条角色行。
     *
     * @param items         待写入项
     * @param practitionerId 医务人员主键
     * @param functionCode  角色码
     * @param executionTime 开始时刻
     */
    private void appendRoleIfPresent(
        List<AppendProcedurePerformerCommand.ProcedurePerformerItemCommand> items,
        Long practitionerId,
        String functionCode,
        LocalDateTime executionTime) {
        // 空 id 跳过该角色（BR-IP-SAE-004）
        if (practitionerId == null) {
            return;
        }
        AppendProcedurePerformerCommand.ProcedurePerformerItemCommand item =
            new AppendProcedurePerformerCommand.ProcedurePerformerItemCommand();
        item.setPractitionerId(practitionerId);
        item.setFunctionCode(functionCode);
        item.setStartTime(executionTime);
        items.add(item);
    }
}
