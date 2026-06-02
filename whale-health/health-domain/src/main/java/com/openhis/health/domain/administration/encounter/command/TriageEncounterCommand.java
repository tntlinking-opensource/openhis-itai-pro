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

import java.time.LocalDateTime;

/**
 * 门急诊分诊命令：在就诊上记录分诊护士、级别与时间（与急诊分诊读模型可并存）。
 */
@Getter
@Setter
public class TriageEncounterCommand extends BatchCommand<TriageEncounterCommand.TriageEncounterItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /** 单条分诊项。 */
    @Getter
    @Setter
    public static class TriageEncounterItemCommand {
        /** 就诊主键。 */
        private Long encounterId;
        /** 分诊护士主键。 */
        private Long triageNurseId;
        /** 分诊级别编码。 */
        private String triageLevelCode;
        /** 分诊时间。 */
        private LocalDateTime triageTime;
    }
}
