/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.registration.response;

import com.openhis.health.application.outpatient.financial.registration.dto.ContractInsuranceProfileDto;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.health.infrastructure.integration.workinjury.ExtensionOut.WorkInjuryCapabilityExtensionOut;
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
@Schema(description = "挂号初始化响应")
public class LoadRegisterFormInitResponse {

    /** 科室选项（业务字段）。 */
    @Schema(description = "科室选项")
    private List<OptionDto> departmentOptions;

    /** 医生选项。 */
    @Schema(description = "医生选项")
    private List<OptionDto> practitionerOptions;

    /** 费用性质选项。 */
    @Schema(description = "费用性质选项")
    private List<OptionDto> contractOptions;

    /** 号源类型选项。 */
    @Schema(description = "号源类型选项")
    private List<OptionDto> serviceTypeOptions;

    /** 号源类型主键（String）→ 挂号费销售价（元）。 */
    @Schema(description = "号源类型挂号费（元）")
    private Map<String, BigDecimal> serviceTypeFeeById;

    /** 性别选项（与患者主档 AdministrativeGender 枚举编码一致）。 */
    @Schema(description = "性别选项")
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

    /** 工伤模块 capability（enabled=false 时为 null）。 */
    @Schema(description = "工伤模块 capability")
    private WorkInjuryCapabilityExtensionOut workInjuryCapability;

    /**
     * 挂号时医生是否必选（全局开关 {@code registrationDoctorRequired}；{@code true}=必选，
     * {@code false}/缺省=可只挂科室）。
     */
    @Schema(description = "挂号时医生是否必选")
    private Boolean registrationDoctorRequired;
}
