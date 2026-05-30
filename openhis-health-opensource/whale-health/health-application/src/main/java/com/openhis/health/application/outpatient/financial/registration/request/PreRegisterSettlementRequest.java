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

import java.time.LocalDate;

/**
 * 挂号预结算请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "挂号预结算请求")
public class PreRegisterSettlementRequest {

    /** 患者 ID。 */
    @Schema(description = "患者ID")
    private Long patientId;

    /** 患者姓名。 */
    @Schema(description = "患者姓名")
    private String name;

    /** 性别编码。 */
    @Schema(description = "性别")
    private String gender;

    /** 身份证号。 */
    @Schema(description = "身份证号")
    private String idCardNo;

    /** 手机号。 */
    @Schema(description = "手机号")
    private String phone;

    /** 出生日期。 */
    @Schema(description = "出生日期")
    private LocalDate birthDate;

    /** 费用性质 ID。 */
    @Schema(description = "费用性质ID")
    private Long contractId;

    /** 科室 ID。 */
    @Schema(description = "科室ID")
    private Long organizationId;

    /** 医生 ID。 */
    @Schema(description = "医生ID")
    private Long practitionerId;

    /** 号源类型 ID。 */
    @Schema(description = "号源类型ID")
    private Long serviceTypeId;
}
