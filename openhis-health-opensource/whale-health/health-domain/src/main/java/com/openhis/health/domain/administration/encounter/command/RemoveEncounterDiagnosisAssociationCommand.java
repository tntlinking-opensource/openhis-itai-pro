/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.encounter.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 移除就诊诊断关联命令：按就诊与病情主键解除关联并记录原因。
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
        /** 就诊主键。 */
        private Long encounterId;
        /** 病情/诊断条件主键。 */
        private Long conditionId;
        /** 移除原因。 */
        private String removeReason;
    }
}
