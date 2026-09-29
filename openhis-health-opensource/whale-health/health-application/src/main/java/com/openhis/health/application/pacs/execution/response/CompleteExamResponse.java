/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.pacs.execution.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 确认完成响应。
 */
@Getter
@Setter
@Schema(description = "检查执行-确认完成响应")
public class CompleteExamResponse {

    @Schema(description = "工单主键")
    private Long workItemId;

    @Schema(description = "检查号")
    private String accessionNumber;

    @Schema(description = "状态")
    private String statusEnum;

    @Schema(description = "完成时间")
    private LocalDateTime performedTime;
}
