/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.registration.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 登记详情请求。
 */
@Getter
@Setter
@Schema(description = "检查登记-详情请求")
public class GetRegistrationDetailRequest {

    @NotNull
    @Schema(description = "工单主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long workItemId;
}
