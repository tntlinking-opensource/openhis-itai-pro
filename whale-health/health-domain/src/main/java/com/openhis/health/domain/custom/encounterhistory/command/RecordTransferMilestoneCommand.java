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
 * 记录转科里程碑命令。
 */
@Getter
@Setter
public class RecordTransferMilestoneCommand {
    /**
     * 就诊标识。
     */
    private Long encounterId;
    /**
     * 关联申请单主键（与 requestTableName 组成业务引用对）。
     */
    private Long requestId;
    /**
     * 关联申请单所在表名。
     */
    private String requestTableName;
    /**
     * 转出机构标识。
     */
    private Long originalOrganizationId;
    /**
     * 转入机构标识。
     */
    private Long targetOrganizationId;
    /**
     * 转出位置标识（可空）。
     */
    private Long originalLocationId;
    /**
     * 转入位置标识（可空）。
     */
    private Long targetLocationId;
    /**
     * 操作人标识。
     */
    private Long operatorId;
}
