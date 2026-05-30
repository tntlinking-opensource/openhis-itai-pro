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

package com.openhis.health.application.inpatient.financial.registration.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 登记上下文加载请求（IF-IP-REG-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院登记上下文加载请求")
public class LoadRegistrationContextRequest {

    /** 住院就诊 ID；无档登记时为空。 */
    @Schema(description = "住院就诊 ID；无档登记时为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 已选住院科室（预拉费用性质与病区用）。 */
    @Schema(description = "已选住院科室 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long organizationId;
}
