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

package com.openhis.health.application.inpatient.nursestation.orderexecute.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 批量执行响应（IF-IP-EXE-02）。
 *
 * <p>{@code processedCount} 为本次同事务内成功完成的槽位数；任一条失败则整批回滚，计数为 0。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "批量执行响应")
public class ExecuteNurseStationOrdersResponse {

    /** 成功处理的槽位数。 */
    @Schema(description = "成功处理的槽位数")
    private Integer processedCount;
}
