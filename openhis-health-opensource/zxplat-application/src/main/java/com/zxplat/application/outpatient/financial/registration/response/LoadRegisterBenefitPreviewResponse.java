/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.registration.response;

import com.zxplat.application.outpatient.financial.registration.dto.RegisterContractMatchDto;
import com.zxplat.application.outpatient.financial.registration.dto.StudentEligibilityPreviewDto;
import com.zxplat.application.outpatient.financial.registration.dto.StudentRegisterBenefitPreviewDto;
import com.zxplat.application.outpatient.financial.registration.dto.YbRegisterBenefitPreviewDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/** 挂号待遇预览响应。 */
@Data
@Accessors(chain = true)
@Schema(description = "挂号待遇预览响应")
public class LoadRegisterBenefitPreviewResponse {

    /** 分支：INSURANCE / STUDENT / NONE。 */
    @Schema(description = "分支：INSURANCE / STUDENT / NONE")
    private String benefitKind;

    /** 医保预览。 */
    @Schema(description = "医保预览")
    private YbRegisterBenefitPreviewDto ybPreview;

    /** 学生档案预览。 */
    @Schema(description = "学生档案预览")
    private StudentRegisterBenefitPreviewDto studentPreview;

    /** 学生公费资格。 */
    @Schema(description = "学生公费资格")
    private StudentEligibilityPreviewDto studentEligibility;

    /** 费用性质与参保地匹配。 */
    @Schema(description = "费用性质与参保地匹配")
    private RegisterContractMatchDto contractMatch;

    /** 工伤分支提示。 */
    @Schema(description = "工伤分支提示")
    private String hintMessage;
}
