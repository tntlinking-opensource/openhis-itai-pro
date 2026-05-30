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

package com.openhis.health.application.outpatient.doctorstation.inpatientadmission.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-REQ-02：查询是否已存在住院申请响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊医生站-住院申请-查询是否已存在住院申请响应")
public class CheckInpatientAdmissionExistsResponse {

    /** 是否已有关联未取消住院线。 */
    @Schema(description = "是否已存在未取消的住院就诊")
    private boolean exists;

    /** 住院就诊主键；不存在时为 null。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "住院就诊主键；不存在时为空")
    private Long inpatientEncounterId;

    /** 住院线状态码（如 PLANNED、IN_PROGRESS）。 */
    @Schema(description = "住院就诊状态枚举编码")
    private String statusEnum;
}
