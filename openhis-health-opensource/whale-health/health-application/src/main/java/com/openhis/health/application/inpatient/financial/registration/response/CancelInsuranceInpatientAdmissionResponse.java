/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.registration.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 待入科（REGISTERED）医保退入院响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "待入科医保退入院响应")
public class CancelInsuranceInpatientAdmissionResponse {

    /** 撤销后的就诊状态（通常为 CANCELLED）。 */
    @Schema(description = "撤销后的就诊状态（通常为 CANCELLED）")
    private String encounterStatus;

    /** 是否已向医保平台撤销入院登记（2404）。 */
    @Schema(description = "是否已向医保平台撤销入院登记（2404）")
    private Boolean insurancePlatformRevoked;
}
