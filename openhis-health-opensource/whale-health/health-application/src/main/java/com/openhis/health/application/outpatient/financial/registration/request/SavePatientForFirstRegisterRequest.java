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
 * 首次建档请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "首次建档请求")
public class SavePatientForFirstRegisterRequest {

    /** 患者姓名。 */
    @Schema(description = "姓名")
    private String name;

    /** 性别编码。 */
    @Schema(description = "性别")
    private String gender;

    /** 身份证号码。 */
    @Schema(description = "身份证号")
    private String idCardNo;

    /** 手机号码。 */
    @Schema(description = "手机号")
    private String phone;

    /** 出生日期。 */
    @Schema(description = "出生日期")
    private LocalDate birthDate;
}
