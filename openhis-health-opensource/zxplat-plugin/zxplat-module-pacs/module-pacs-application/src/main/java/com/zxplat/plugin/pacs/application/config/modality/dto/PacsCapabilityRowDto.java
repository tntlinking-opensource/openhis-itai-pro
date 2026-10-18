/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.config.modality.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 模态能力配置行。
 */
@Getter
@Setter
@Schema(description = "模态能力配置行")
public class PacsCapabilityRowDto {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "ActivityDefinition 主键")
    private Long activityId;

    @Schema(description = "模态码")
    private String modalityCode;

    @Schema(description = "是否启用")
    private Boolean enabledFlag;

    @Schema(description = "科室范围")
    private Long organizationId;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
