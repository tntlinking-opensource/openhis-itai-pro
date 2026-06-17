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

package com.openhis.health.application.outpatient.doctorstation.inpatientadmission.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-REQ-02：按门诊就诊查询是否已存在住院申请。
 */
@Data
@Accessors(chain = true)
/** 门诊医生站-住院申请-查询是否已存在住院申请请求。 */
@Schema(description = "门诊医生站-住院申请-查询是否已存在住院申请请求")
public class CheckInpatientAdmissionExistsRequest {

    /** 门诊就诊标识。 */
    @NotNull(message = "门诊就诊标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "源门诊就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long ambEncounterId;
}
