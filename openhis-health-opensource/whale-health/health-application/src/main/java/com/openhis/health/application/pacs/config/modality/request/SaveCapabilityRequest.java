/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.pacs.config.modality.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 保存模态能力请求。
 */
@Getter
@Setter
@Schema(description = "模态能力-保存请求")
public class SaveCapabilityRequest {

    @Schema(description = "主键；空=新建")
    private Long id;

    @NotNull
    @Schema(description = "ActivityDefinition 主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long activityId;

    @NotBlank
    @Schema(description = "模态码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String modalityCode;

    @Schema(description = "是否启用")
    private Boolean enabledFlag;

    @Schema(description = "科室范围")
    private Long organizationId;
}
