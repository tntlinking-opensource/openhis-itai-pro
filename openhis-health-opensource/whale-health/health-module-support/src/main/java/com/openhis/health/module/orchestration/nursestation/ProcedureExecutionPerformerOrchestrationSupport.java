/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.module.orchestration.nursestation;

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
public class ProcedureExecutionPerformerOrchestrationSupport {

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
