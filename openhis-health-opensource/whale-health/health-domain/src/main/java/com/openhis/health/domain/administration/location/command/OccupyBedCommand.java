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

package com.openhis.health.domain.administration.location.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 占用床位命令：将床位地点与住院就诊建立占用关系。
 */
@Getter
@Setter
public class OccupyBedCommand extends BatchCommand<OccupyBedCommand.OccupyBedItemCommand> {
    /** 操作人主键。 */
    private Long operatorId;

    /** 单条占用项。 */
    @Getter
    @Setter
    public static class OccupyBedItemCommand {
        /** 床位所在位置主键。 */
        private Long locationId;
        /** 住院就诊主键。 */
        private Long encounterId;
        /** 为 true 时自 {@code RESERVED} 占床（预约患者分床，GAP-ATD-007）。 */
        private Boolean occupyFromReservation;
    }
}
