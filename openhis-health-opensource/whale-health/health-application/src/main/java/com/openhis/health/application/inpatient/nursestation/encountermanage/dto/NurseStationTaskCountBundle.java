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

package com.openhis.health.application.inpatient.nursestation.encountermanage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 单就诊维度的护士站任务计数（BR-IP-NUR-004/005）。
 */
@Data
@Accessors(chain = true)
/** 护士站任务计数。 */
@Schema(description = "护士站任务计数")
public class NurseStationTaskCountBundle {

    @Schema(description = "待校对条数")
    private Integer pendingAuditCount;

    @Schema(description = "待执行条数")
    private Integer pendingExecuteCount;
}
