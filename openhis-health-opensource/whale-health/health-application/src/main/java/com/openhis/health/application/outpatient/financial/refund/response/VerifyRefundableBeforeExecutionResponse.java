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

package com.openhis.health.application.outpatient.financial.refund.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 执行前可退门禁响应（IF-REF-04）。
 */
@Data
@Accessors(chain = true)
/** 执行前可退门禁响应。 */
@Schema(description = "执行前可退门禁响应")
public class VerifyRefundableBeforeExecutionResponse {

    @Schema(description = "是否允许继续执行退费")
    /** 是否允许继续执行退费（业务字段）。 */
    private boolean allowed;

    /** 拒绝原因语义码（如 EX-REF-201）；允许时为 null。 */
    @Schema(description = "拒绝原因语义码，如 EX-REF-201")
    /** 拒绝原因语义码，如 EX-REF-201（业务字段）。 */
    private String rejectReasonCode;
}
