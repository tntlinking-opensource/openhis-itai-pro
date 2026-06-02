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
 * 记录离院里程碑命令。
 */
@Getter
@Setter
public class RecordDischargeMilestoneCommand {
    /**
     * 就诊标识。
     */
    private Long encounterId;
    /**
     * 关联申请单主键。
     */
    private Long requestId;
    /**
     * 关联申请单所在表名。
     */
    private String requestTableName;
    /**
     * 离院方式编码。
     */
    private String outWayCode;
    /**
     * 离院前机构标识（可空）。
     */
    private Long originalOrganizationId;
    /**
     * 离院后去向机构标识（可空）。
     */
    private Long targetOrganizationId;
    /**
     * 离院前位置标识（可空）。
     */
    private Long originalLocationId;
    /**
     * 离院后位置标识（可空）。
     */
    private Long targetLocationId;
    /**
     * 操作人标识。
     */
    private Long operatorId;
}
