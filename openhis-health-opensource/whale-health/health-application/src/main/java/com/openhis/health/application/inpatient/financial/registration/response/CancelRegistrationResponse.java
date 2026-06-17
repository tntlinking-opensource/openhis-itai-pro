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

package com.openhis.health.application.inpatient.financial.registration.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 取消住院登记响应。
 */
@Data
@Accessors(chain = true)
/** 取消住院登记结果。 */
@Schema(description = "取消住院登记结果")
public class CancelRegistrationResponse {

    @Schema(description = "住院就诊 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 住院就诊 ID（业务字段）。 */
    private Long encounterId;

    /** 取消后状态 code。 */
    @Schema(description = "取消后状态 code（CANCELLED）")
    /** 取消后状态 code（CANCELLED）（业务字段）。 */
    private String statusEnum;

    /** 是否成功。 */
    @Schema(description = "是否成功")
    /** 是否成功（业务字段）。 */
    private Boolean success;
}
