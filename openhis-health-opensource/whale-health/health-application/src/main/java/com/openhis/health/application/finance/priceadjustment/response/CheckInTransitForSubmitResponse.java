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

package com.openhis.health.application.finance.priceadjustment.response;

import com.openhis.health.application.finance.priceadjustment.dto.InTransitCheckDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 提交前在途预检响应（IF-PRC-08）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "提交前在途预检响应")
public class CheckInTransitForSubmitResponse {

    /** 在途预检结果。 */
    @Schema(description = "在途预检结果")
    private InTransitCheckDto check;
}
