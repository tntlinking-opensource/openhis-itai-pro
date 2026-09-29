/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.pacs.report.shared.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 报告页影像附件（关键图 + 阅片深链）。
 */
@Getter
@Setter
@Schema(description = "报告影像附件")
public class ReportImagingAttachmentDto {

    @Schema(description = "工单是否已有影像/关键图标记")
    private Boolean hasMedia = Boolean.FALSE;

    @Schema(description = "是否已绑定 Study")
    private Boolean hasStudy = Boolean.FALSE;

    @Schema(description = "Study Instance UID")
    private String studyInstanceUid;

    @Schema(description = "是否可打开阅片")
    private Boolean viewerAvailable = Boolean.FALSE;

    @Schema(description = "OHIF 启动 URL（含短期 Token）")
    private String viewerLaunchUrl;

    @Schema(description = "阅片 Token 过期时间 ISO-8601")
    private String viewerExpiresAt;

    @Schema(description = "关键图预览列表")
    private List<ReportKeyImageDto> keyImages = new ArrayList<>();
}
