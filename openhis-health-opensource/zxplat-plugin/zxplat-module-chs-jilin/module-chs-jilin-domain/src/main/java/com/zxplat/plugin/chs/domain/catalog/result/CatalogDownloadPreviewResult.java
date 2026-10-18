/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.catalog.result;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 目录下载版本探测结果（仅阶段 1）301+ZIP，不入库） */
@Data
@Accessors(chain = true)
@Schema(description = "目录下载版本探测")
public class CatalogDownloadPreviewResult {

    @Schema(description = "接口号")
    private String infno;

    @Schema(description = "起始本地版本")
    private String startLocalVer;

    @Schema(description = "起始版本展示用短文本")
    private String startLocalVerShort;

    @Schema(description = "本次探测到的待处理版本总数")
    private int pendingVersionCount;

    @Schema(description = "是否可能仍有更多版本（受 maxVersions 限制）")
    private boolean hasMore;

    @Schema(description = "版本明细")
    private List<CatalogDownloadVersionItemResult> versions;
}
