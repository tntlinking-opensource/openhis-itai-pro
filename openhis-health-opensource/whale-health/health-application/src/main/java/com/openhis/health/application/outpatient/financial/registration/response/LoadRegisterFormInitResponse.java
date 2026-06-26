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

package com.openhis.health.application.outpatient.financial.registration.response;

import com.openhis.health.application.outpatient.financial.registration.dto.ContractInsuranceProfileDto;
import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 挂号初始化响应。
 */
@Data
@Accessors(chain = true)
/** 挂号初始化响应。 */
@Schema(description = "挂号初始化响应")
public class LoadRegisterFormInitResponse {

    @Schema(description = "科室选项")
    /** 科室选项（业务字段）。 */
    private List<OptionDto> departmentOptions;

    /** 医生选项。 */
    @Schema(description = "医生选项")
    /** 医生选项（业务字段）。 */
    private List<OptionDto> practitionerOptions;

    /** 费用性质选项。 */
    @Schema(description = "费用性质选项")
    /** 费用性质选项（业务字段）。 */
    private List<OptionDto> contractOptions;

    /** 号源类型选项。 */
    @Schema(description = "号源类型选项")
    /** 号源类型选项（业务字段）。 */
    private List<OptionDto> serviceTypeOptions;

    /** 号源类型主键（String）→ 挂号费销售价（元）。 */
    @Schema(description = "号源类型挂号费（元）")
    /** 号源类型挂号费（元）（业务字段）。 */
    private Map<String, BigDecimal> serviceTypeFeeById;

    /** 性别选项（与患者主档 AdministrativeGender 枚举编码一致）。 */
    @Schema(description = "性别选项")
    /** 性别选项（业务字段）。 */
    private List<OptionDto> genderOptions;

    /** 费用性质主键（String）→ 合同类别编码（ContractCategory.code）。 */
    @Schema(description = "费用性质类别映射")
    private Map<String, String> contractCategoryById;

    /** 费用性质主键 → 医保通道概要。 */
    @Schema(description = "费用性质医保通道概要")
    private Map<String, ContractInsuranceProfileDto> contractInsuranceProfileById;

    /** 支付渠道选项（477 ∩ PaymentChannel）。 */
    @Schema(description = "支付渠道选项")
    private List<OptionDto> paymentChannelOptions;
}
