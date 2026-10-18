/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.report.audit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 报告审核待审列表行。
 */
@Getter
@Setter
@Schema(description = "报告审核列表行")
public class PacsAuditRowDto {

    @Schema(description = "报告主键")
    private Long reportId;

    @Schema(description = "工单主键")
    private Long workItemId;

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

    @Schema(description = "提交时间")
    private LocalDateTime submittedAt;

    @Schema(description = "书写医生")
    private Long authorPractitionerId;

    @Schema(description = "结论摘要")
    private String conclusion;
}
