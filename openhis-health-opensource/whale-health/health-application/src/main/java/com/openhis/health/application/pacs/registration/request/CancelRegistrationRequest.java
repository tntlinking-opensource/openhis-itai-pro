/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.pacs.registration.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 作废登记请求（IF-PACS-REG-03）。
 */
@Getter
@Setter
@Schema(description = "检查登记-作废请求")
public class CancelRegistrationRequest {

    @NotNull
    @Schema(description = "工单主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long workItemId;

    @Schema(description = "作废原因")
    private String reason;
}
