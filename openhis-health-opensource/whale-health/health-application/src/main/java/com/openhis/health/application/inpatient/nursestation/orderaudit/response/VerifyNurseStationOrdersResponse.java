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

package com.openhis.health.application.inpatient.nursestation.orderaudit.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 批量校对通过响应（IF-IP-AUD-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "批量校对通过响应")
public class VerifyNurseStationOrdersResponse {

    /** 整批成功为 true。 */
    @Schema(description = "整批成功为 true")
    private Boolean success;

    /** 成功处理条数。 */
    @Schema(description = "成功处理条数")
    private Integer processedCount;
}
