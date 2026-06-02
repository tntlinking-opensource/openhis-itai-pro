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

import java.time.LocalDate;

@Data
@Accessors(chain = true)
@Schema(description = "患者列表项")
public class PatientItemDto {

    /** 患者 ID。 */
    @Schema(description = "患者ID")
    private Long patientId;

    /** 患者业务号。 */
    @Schema(description = "患者业务号")
    private String patientBusNo;

    /** 患者姓名。 */
    @Schema(description = "姓名")
    private String name;

    /** 性别编码（AdministrativeGender#getCode()）。 */
    @Schema(description = "性别编码")
    private String genderCode;

    /** 性别展示文案。 */
    @Schema(description = "性别展示文案")
    private String genderText;

    /** 出生日期。 */
    @Schema(description = "出生日期")
    private LocalDate birthDate;

    /**
     * 年龄（周岁，由出生日期推算；出生日期缺失时为 {@code null}）。
     * <p>与患者主数据分页列表提示口径一致，仅供界面展示。</p>
     */
    @Schema(description = "年龄（周岁）")
    private Integer age;

    /** 脱敏证件号。 */
    @Schema(description = "脱敏证件号")
    private String idCardNoMasked;

    /** 脱敏手机号。 */
    @Schema(description = "脱敏手机号")
    private String phoneMasked;
}
