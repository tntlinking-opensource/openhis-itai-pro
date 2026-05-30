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
