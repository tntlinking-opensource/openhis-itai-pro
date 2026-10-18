/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.report.shared.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 报告页关键图预览行。
 */
@Getter
@Setter
@Schema(description = "报告关键图预览行")
public class ReportKeyImageDto {

    @Schema(description = "Media 主键")
    private Long mediaId;

    @Schema(description = "内容类型")
    private String contentType;

    @Schema(description = "来源枚举")
    private String sourceEnum;

    @Schema(description = "预签名预览 URL")
    private String previewUrl;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
