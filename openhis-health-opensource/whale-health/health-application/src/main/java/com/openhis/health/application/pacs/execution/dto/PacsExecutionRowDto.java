/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.pacs.execution.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 检查执行工作台列表行。
 */
@Getter
@Setter
@Schema(description = "检查执行列表行")
public class PacsExecutionRowDto {

    @Schema(description = "工单主键")
    private Long workItemId;

    @Schema(description = "检查号")
    private String accessionNumber;

    @Schema(description = "患者主键")
    private Long patientId;

    @Schema(description = "患者姓名")
    private String patientName;

    @Schema(description = "就诊主键")
    private Long encounterId;

    @Schema(description = "科室")
    private Long organizationId;

    @Schema(description = "状态")
    private String statusEnum;

    @Schema(description = "模态")
    private String modalityCode;

    @Schema(description = "部位")
    private String bodySiteCode;

    @Schema(description = "计划时间")
    private LocalDateTime scheduledTime;

    @Schema(description = "完成时间")
    private LocalDateTime performedTime;

    @Schema(description = "关联医嘱数")
    private Integer orderCount;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
