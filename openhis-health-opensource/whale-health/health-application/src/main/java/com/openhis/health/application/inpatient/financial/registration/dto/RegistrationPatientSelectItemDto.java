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

package com.openhis.health.application.inpatient.financial.registration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * 住院登记弹窗：患者下拉检索列表行（IF-IP-REG-06）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院登记患者检索列表项")
public class RegistrationPatientSelectItemDto {

    /** 患者主键。 */
    @Schema(description = "患者ID")
    private Long patientId;

    /** 患者业务号。 */
    @Schema(description = "患者业务号")
    private String patientBusNo;

    /** 患者姓名。 */
    @Schema(description = "姓名")
    private String name;

    /** 性别 code（{@link com.openhis.health.domain.share.enums.AdministrativeGender#getCode()}）。 */
    @Schema(description = "性别编码")
    private String genderCode;

    /** 性别展示文案。 */
    @Schema(description = "性别展示文案")
    private String genderText;

    /** 出生日期。 */
    @Schema(description = "出生日期")
    private LocalDate birthDate;

    /** 周岁年龄；出生日期缺失时为 null。 */
    @Schema(description = "年龄（周岁）")
    private Integer age;

    /** 证件号（读侧脱敏口径与患者分页列表一致）。 */
    @Schema(description = "脱敏证件号")
    private String idCardNoMasked;

    /** 手机号（读侧脱敏口径与患者分页列表一致）。 */
    @Schema(description = "脱敏手机号")
    private String phoneMasked;
}
