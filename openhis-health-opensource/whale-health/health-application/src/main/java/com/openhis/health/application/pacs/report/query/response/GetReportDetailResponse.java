/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.pacs.report.query.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 报告只读详情响应。
 */
@Getter
@Setter
@Schema(description = "报告查询-详情响应")
public class GetReportDetailResponse {

    @Schema(description = "报告主键")
    private Long id;

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

    @Schema(description = "所见")
    private String findings;

    @Schema(description = "结论")
    private String conclusion;

    @Schema(description = "PDF 存储键")
    private String pdfStorageKey;

    @Schema(description = "书写医生")
    private Long authorPractitionerId;

    @Schema(description = "提交时间")
    private LocalDateTime submittedAt;

    @Schema(description = "签发时间")
    private LocalDateTime issuedAt;

    @Schema(description = "审核医生")
    private Long auditorPractitionerId;

    @Schema(description = "模态")
    private String modalityCode;
}
