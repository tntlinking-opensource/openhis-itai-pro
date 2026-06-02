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

package com.openhis.health.domain.workflow.slot.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 标暂定占用命令（DM-WKF-010）。
 */
@Getter
@Setter
public class MarkTentativeCommand extends BatchCommand<MarkTentativeCommand.MarkTentativeItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

    @Getter
    @Setter
    public static class MarkTentativeItemCommand {
        /** 号源时段标识。 */
        private Long slotId;

        /** 超时配置（可选，聚合可不落库，仅用于上层调度口径透传）。 */
        private String timeoutConfig;
    }
}

