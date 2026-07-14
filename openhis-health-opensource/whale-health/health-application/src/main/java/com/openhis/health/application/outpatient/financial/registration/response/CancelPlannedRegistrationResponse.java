/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.registration.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 待挂号（PLANNED）撤销响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "待挂号撤销响应")
public class CancelPlannedRegistrationResponse {

    /** 撤销后的就诊状态（通常为 CANCELLED）。 */
    @Schema(description = "就诊状态")
    private String encounterStatus;

    /** 是否已向医保平台撤销挂号登记（2202）。 */
    @Schema(description = "是否已撤销医保平台挂号（2202）")
    private Boolean insurancePlatformRevoked;
}
