/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.nursestation;

import com.openhis.health.domain.clinical.procedure.command.AppendProcedurePerformerCommand;
import com.openhis.health.domain.clinical.procedure.service.ProcedureDomainService;
import com.openhis.health.domain.share.constants.ClinicalProcedureConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 护士站 Procedure 执行人写编排：完成执行后向 {@code cln_procedure_performer} 登记主执行人（暂 1:1）。
 *
 * <p>门诊/住院护士站执行路径共用；入参须为 {@code adm_practitioner.id}（B.6.5）。</p>
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
}
