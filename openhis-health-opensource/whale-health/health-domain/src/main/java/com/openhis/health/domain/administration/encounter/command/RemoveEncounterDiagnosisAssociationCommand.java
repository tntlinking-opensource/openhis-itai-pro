/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.encounter.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 移除就诊诊断关联命令：按就诊诊断行主键软删关联并记录原因。
 */
@Getter
@Setter
public class RemoveEncounterDiagnosisAssociationCommand extends BatchCommand<RemoveEncounterDiagnosisAssociationCommand.RemoveEncounterDiagnosisAssociationItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /** 单条移除项。 */
    @Getter
    @Setter
    public static class RemoveEncounterDiagnosisAssociationItemCommand {
        /** 就诊主键 {@code adm_encounter.id}，与行主键一并校验归属。 */
        private Long encounterId;
        /** 就诊诊断关联行主键 {@code adm_encounter_diagnosis.id}。 */
        private Long encounterDiagnosisId;
        /** 移除原因。 */
        private String removeReason;
    }
}
