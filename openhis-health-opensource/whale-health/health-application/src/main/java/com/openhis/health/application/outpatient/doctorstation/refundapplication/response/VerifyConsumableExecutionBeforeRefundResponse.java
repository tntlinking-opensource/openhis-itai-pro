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

package com.openhis.health.application.outpatient.doctorstation.refundapplication.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-DRA-02：耗材执行门禁响应。
 */
@Data
@Accessors(chain = true)
/** 门诊医生站-申请退费-耗材执行门禁响应。 */
@Schema(description = "门诊医生站-申请退费-耗材执行门禁响应")
public class VerifyConsumableExecutionBeforeRefundResponse {

    @Schema(description = "true 表示通过门禁")
    private boolean allowed;

    @Schema(description = "若不通过，填充稳定语义码（如 EX-DRA-201）")
    private String rejectReasonCode;
}
