/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.outpatient.financial.registration.response;

import com.openhis.health.application.outpatient.financial.registration.dto.RegisterContractMatchDto;
import com.openhis.health.application.outpatient.financial.registration.dto.StudentEligibilityPreviewDto;
import com.openhis.health.application.outpatient.financial.registration.dto.StudentRegisterBenefitPreviewDto;
import com.openhis.health.application.outpatient.financial.registration.dto.YbRegisterBenefitPreviewDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/** 挂号待遇预览响应。 */
@Data
@Accessors(chain = true)
@Schema(description = "挂号待遇预览响应")
public class LoadRegisterBenefitPreviewResponse {

    @Schema(description = "分支：INSURANCE / STUDENT / NONE")
    private String benefitKind;

    @Schema(description = "医保预览")
    private YbRegisterBenefitPreviewDto ybPreview;

    @Schema(description = "学生档案预览")
    private StudentRegisterBenefitPreviewDto studentPreview;

    @Schema(description = "学生公费资格")
    private StudentEligibilityPreviewDto studentEligibility;

    @Schema(description = "费用性质与参保地匹配")
    private RegisterContractMatchDto contractMatch;
}
