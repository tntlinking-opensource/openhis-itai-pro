/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.pacs.execution.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 确认检查完成请求（IF-PACS-EXE-05）。
 */
@Getter
@Setter
@Schema(description = "检查执行-确认完成请求")
public class CompleteExamRequest {

    @NotNull
    @Schema(description = "工单主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long workItemId;

    @Schema(description = "实际完成时间；空则当前时间")
    private LocalDateTime performedTime;

    @Schema(description = "执行人")
    private Long performerPractitionerId;

    @Schema(description = "设备")
    private Long deviceId;
}
