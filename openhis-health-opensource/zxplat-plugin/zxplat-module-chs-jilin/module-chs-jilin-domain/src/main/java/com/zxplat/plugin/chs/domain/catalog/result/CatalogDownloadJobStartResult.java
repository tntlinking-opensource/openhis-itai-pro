/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.catalog.result;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/** 目录 ZIP 拉取完成后的异步入库任务启动结果 */
@Data
@Accessors(chain = true)
@Schema(description = "医保目录异步入库任务启动结果")
public class CatalogDownloadJobStartResult {

    @Schema(description = "入库任务 ID，用于轮询进度")
    private String jobId;

    @Schema(description = "接口号infno")
    private String infno;

    @Schema(description = "待处理ZIP 包数")
    private int totalVersions;

    @Schema(description = "说明")
    private String message;
}
