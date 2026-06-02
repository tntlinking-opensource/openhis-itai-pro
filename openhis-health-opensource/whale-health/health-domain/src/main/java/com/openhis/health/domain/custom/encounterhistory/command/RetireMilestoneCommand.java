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

package com.openhis.health.domain.custom.encounterhistory.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 废弃里程碑命令。
 */
@Getter
@Setter
public class RetireMilestoneCommand {
    /**
     * 就诊历史里程碑主键。
     */
    private Long encounterHistoryId;
    /**
     * 废弃原因说明。
     */
    private String reason;
    /**
     * 操作人标识。
     */
    private Long operatorId;
}
