/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.pacs.registration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 登记工作台列表行（含 Patient 姓名，无 PHI 落库）。
 */
@Getter
@Setter
@Schema(description = "检查登记列表行")
public class PacsRegistrationRowDto {

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

    @Schema(description = "登记/执行科室")
    private Long organizationId;

    @Schema(description = "状态枚举 code")
    private String statusEnum;

    @Schema(description = "模态码")
    private String modalityCode;

    @Schema(description = "检查部位码")
    private String bodySiteCode;

    @Schema(description = "计划检查时间")
    private LocalDateTime scheduledTime;

    @Schema(description = "关联医嘱数")
    private Integer orderCount;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
