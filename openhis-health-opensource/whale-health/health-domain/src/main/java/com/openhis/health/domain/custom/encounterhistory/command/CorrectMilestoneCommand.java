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
 * 更正里程碑命令。
 */
@Getter
@Setter
public class CorrectMilestoneCommand {
    /**
     * 就诊历史里程碑主键。
     */
    private Long encounterHistoryId;
    /**
     * 更正后的转出/原机构标识（可空）。
     */
    private Long originalOrganizationId;
    /**
     * 更正后的转入/目标机构标识（可空）。
     */
    private Long targetOrganizationId;
    /**
     * 更正后的原位置标识（可空）。
     */
    private Long originalLocationId;
    /**
     * 更正后的目标位置标识（可空）。
     */
    private Long targetLocationId;
    /**
     * 离院方式编码（仅离院类里程碑；可空）。
     */
    private String outWayCode;
    /**
     * 更正原因说明。
     */
    private String reason;
    /**
     * 操作人标识。
     */
    private Long operatorId;
}
