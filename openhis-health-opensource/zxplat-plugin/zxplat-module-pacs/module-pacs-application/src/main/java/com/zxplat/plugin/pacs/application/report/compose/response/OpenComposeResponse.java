/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.report.compose.response;

import com.zxplat.plugin.pacs.application.report.shared.dto.ReportImagingAttachmentDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 进入报告编辑响应。
 */
@Getter
@Setter
@Schema(description = "报告书写-进入编辑响应")
public class OpenComposeResponse {

    @Schema(description = "报告主键")
    private Long reportId;

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

    @Schema(description = "报告状态")
    private String statusEnum;

    @Schema(description = "是否待审")
    private Boolean pendingAudit;

    @Schema(description = "所见")
    private String findings;

    @Schema(description = "结论")
    private String conclusion;

    @Schema(description = "PDF 存储键")
    private String pdfStorageKey;

    @Schema(description = "提交时间")
    private LocalDateTime submittedAt;

    @Schema(description = "工单状态")
    private String workItemStatusEnum;

    @Schema(description = "影像附件（关键图预览 + 阅片深链；imaging 未启用时为空）")
    private ReportImagingAttachmentDto imaging;
}
