/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.catalog.result;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 目录文件下载执行结果 */
@Data
@Accessors(chain = true)
@Schema(description = "医保目录文件下载结果")
public class CatalogDownloadResult {

    @Schema(description = "接口号infno")
    private String infno;

    @Schema(description = "本次 upsert 行数")
    private int upsertCount;

    @Schema(description = "下载版本批次数")
    private int versionBatches;

    @Schema(description = "本次处理版本总数（同 versionBatches）")
    private int totalVersionCount;

    @Schema(description = "各版本摘要")
    private java.util.List<CatalogDownloadVersionItemResult> versions;

    @Schema(description = "更新后的本地版本 local_ver（最新游标快照）")
    private String localVer;

    @Schema(description = "本次写入的最新游标记id")
    private Long latestCursorId;

    @Schema(description = "本次追加的游标快照条数")
    private int cursorSnapshotCount;

    @Schema(description = "流水线阶段摘要")
    private java.util.List<CatalogDownloadPhaseSummaryResult> phaseSummaries;

    @Schema(description = "是否可能仍有新版本可拉取")
    private boolean hasMore;

    @Schema(description = "最后一次文件查询号")
    private String lastFileQuryNo;

    @Schema(description = "最后一次文件名")
    private String lastFilename;
}
