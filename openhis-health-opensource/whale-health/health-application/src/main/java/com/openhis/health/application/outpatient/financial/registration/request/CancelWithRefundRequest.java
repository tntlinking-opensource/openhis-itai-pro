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
 * 退号并退费请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "退号并退费请求")
public class CancelWithRefundRequest {

    /** 就诊 ID。 */
    @Schema(description = "就诊ID")
    private Long encounterId;

    /** 取消原因。 */
    @Schema(description = "取消原因")
    private String cancelReason;

    /** 退费意图。 */
    @Schema(description = "退费意图")
    private String refundIntent;

    /** 退费请求键。 */
    @Schema(description = "退费请求键")
    private String refundRequestId;
}
