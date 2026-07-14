/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.catalog.result;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/** 目录异步入库进度（解包→解析 →写库） */
@Data
@Accessors(chain = true)
@Schema(description = "医保目录异步入库进度")
public class CatalogDownloadJobProgressResult {

    @Schema(description = "任务 ID")
    private String jobId;

    @Schema(description = "阶段：waiting/import/done/failed")
    private String phase;

    @Schema(description = "ZIP 包总数")
    private int totalVersions;

    @Schema(description = "已处理ZIP 包数")
    private int processedVersions;

    @Schema(description = "已解析行数（累计）")
    private int parsedRows;

    @Schema(description = "已入库行数（累计 upsert）")
    private int upsertedRows;

    @Schema(description = "进度 0-100")
    private int percent;

    @Schema(description = "当前说明")
    private String detailMessage;

    @Schema(description = "是否已结束")
    private boolean finished;

    @Schema(description = "是否成功")
    private boolean success;

    @Schema(description = "完成后结果")
    private CatalogDownloadResult result;

    @Schema(description = "失败原因")
    private String errorMessage;
}
