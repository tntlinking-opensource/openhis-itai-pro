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

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 挂号支付响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "挂号支付响应")
public class PayRegisterFeeResponse {

    /** 支付 ID。 */
    @Schema(description = "支付ID")
    private Long paymentId;

    /** 发票 ID。 */
    @Schema(description = "发票ID")
    private Long invoiceId;

    /** 支付状态。 */
    @Schema(description = "状态")
    private String status;
}
