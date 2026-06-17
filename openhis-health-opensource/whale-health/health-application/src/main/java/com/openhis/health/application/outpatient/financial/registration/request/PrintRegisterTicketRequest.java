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

package com.openhis.health.application.outpatient.financial.registration.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 补打挂号单请求。
 */
@Data
@Accessors(chain = true)
/** 补打挂号单请求。 */
@Schema(description = "补打挂号单请求")
public class PrintRegisterTicketRequest {

    @Schema(description = "就诊ID")
    /** 就诊ID（业务字段）。 */
    private Long encounterId;

    /** 挂号单号。 */
    @Schema(description = "挂号单号")
    /** 挂号单号（业务字段）。 */
    private String slipNo;

    /** 打印份数。 */
    @Schema(description = "打印份数")
    /** 打印份数（业务字段）。 */
    private Integer printCopies;
}
