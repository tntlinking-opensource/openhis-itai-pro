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
 * 住院出院命令：办理出院并记录出院方式、时间与摘要。
 */
@Getter
@Setter
public class DischargeInpatientEncounterCommand extends BatchCommand<DischargeInpatientEncounterCommand.DischargeInpatientEncounterItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /** 单条出院项。 */
    @Getter
    @Setter
    public static class DischargeInpatientEncounterItemCommand {
        /** 就诊主键。 */
        private Long encounterId;
        /** 出院方式编码。 */
        private String outWayCode;
        /** 出院时间。 */
        private LocalDateTime dischargedAt;
        /** 出院摘要。 */
        private String summary;
    }
}
