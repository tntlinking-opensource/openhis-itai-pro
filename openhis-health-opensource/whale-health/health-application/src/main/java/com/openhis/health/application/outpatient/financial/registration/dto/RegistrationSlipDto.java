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

package com.openhis.health.application.outpatient.financial.registration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@Accessors(chain = true)
/** 挂号单详情。 */
@Schema(description = "挂号单详情")
public class RegistrationSlipDto {

    /** 挂号单号。 */
    @Schema(description = "挂号单号")
    /** 挂号单号（业务字段）。 */
    private String slipNo;

    /** 就诊 ID。 */
    @Schema(description = "就诊ID")
    /** 就诊ID（业务字段）。 */
    private Long encounterId;

    /** 患者姓名。 */
    @Schema(description = "患者姓名")
    /** 患者姓名（业务字段）。 */
    private String patientName;

    /** 科室名称。 */
    @Schema(description = "科室名称")
    /** 科室名称（业务字段）。 */
    private String organizationName;

    /** 医生姓名。 */
    @Schema(description = "医生姓名")
    /** 医生姓名（业务字段）。 */
    private String doctorName;

    /** 挂号类型名称。 */
    @Schema(description = "挂号类型")
    /** 挂号类型（业务字段）。 */
    private String serviceTypeName;

    /** 状态文本。 */
    @Schema(description = "状态文本")
    /** 状态文本（业务字段）。 */
    private String statusText;

    /** 金额。 */
    @Schema(description = "金额")
    /** 金额（业务字段）。 */
    private BigDecimal amount;

    /** 打印次数。 */
    @Schema(description = "打印次数")
    /** 打印次数（业务字段）。 */
    private Integer printCount;
}
