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
 * 释放床位命令：解除床位与就诊的占用关系（出院或转床前置）。
 */
@Getter
@Setter
public class ReleaseBedCommand extends BatchCommand<ReleaseBedCommand.ReleaseBedItemCommand> {
    /** 操作人主键。 */
    private Long operatorId;

    /** 单条释放项。 */
    @Getter
    @Setter
    public static class ReleaseBedItemCommand {
        /** 床位位置主键。 */
        private Long locationId;
        /** 原占用就诊主键。 */
        private Long encounterId;
    }
}
