/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.catalog.result;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 目录下载流水线阶段摘要（UI 展示） */
@Data
@Accessors(chain = true)
@Schema(description = "目录下载阶段摘要")
public class CatalogDownloadPhaseSummaryResult {

    @Schema(description = "阶段序号，从 1 起")
    private int phaseNo;

    @Schema(description = "阶段键：collect|parse|import")
    private String phaseKey;

    @Schema(description = "阶段标题")
    private String title;

    @Schema(description = "阶段说明")
    private String detail;

    @Schema(description = "本阶段处理版本数或行数等指标")
    private int metric;
}
