/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.registration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/** 参保地与所选费用性质（医保通道）匹配结果。 */
@Data
@Accessors(chain = true)
@Schema(description = "费用性质与参保地匹配结果")
public class RegisterContractMatchDto {

    /** 匹配状态：MATCH / IN_PROVINCE / CROSS_PROVINCE / UNKNOWN。 */
    @Schema(description = "MATCH / IN_PROVINCE / CROSS_PROVINCE / UNKNOWN")
    private String matchStatus;

    /** 窗口展示用提示文案。 */
    @Schema(description = "提示文案")
    private String hintMessage;

    /** 当前所选费用性质 id。 */
    @Schema(description = "当前所选费用性质 id")
    private String selectedContractId;

    /** 当前费用性质对应结算统筹区。 */
    @Schema(description = "当前费用性质对应结算统筹区")
    private String selectedTreatmentAreaCode;

    /** 患者参保地医保区划（1101 返回）。 */
    @Schema(description = "患者参保地医保区划")
    private String patientInsuplcAdmdvs;

    /** 系统推荐的费用性质 id。 */
    @Schema(description = "推荐费用性质 id")
    private String recommendedContractId;

    /** 系统推荐的费用性质名称。 */
    @Schema(description = "推荐费用性质名称")
    private String recommendedContractName;

    /** 推荐费用性质类别码。 */
    @Schema(description = "推荐费用性质类别码")
    private String recommendedCategoryCode;

    /** 是否阻断挂号（OFF 模式恒为 false）。 */
    @Schema(description = "是否阻断挂号（OFF 模式恒为 false）")
    private Boolean blockRegister;
}
