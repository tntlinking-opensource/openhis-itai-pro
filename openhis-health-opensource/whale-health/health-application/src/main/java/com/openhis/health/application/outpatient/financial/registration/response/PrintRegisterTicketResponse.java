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

package com.openhis.health.application.outpatient.financial.registration.response;

import com.openhis.health.application.outpatient.financial.registration.dto.RegistrationSlipDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 补打挂号单响应。
 */
@Data
@Accessors(chain = true)
/** 补打挂号单响应。 */
@Schema(description = "补打挂号单响应")
public class PrintRegisterTicketResponse {

    @Schema(description = "挂号单")
    /** 挂号单（业务字段）。 */
    private RegistrationSlipDto slip;

    /** 打印后累计次数。 */
    @Schema(description = "打印后次数")
    /** 打印后次数（业务字段）。 */
    private Integer printCount;
}
