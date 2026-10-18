/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.catalog.download.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 目录异步入库进度查询（UI 入参）。
 * <p>对应 {@code InsuranceCatalogDownloadAppService#getCatalogDownloadJobProgress}，
 * 用于轮询 {@code startCatalogDownload} 提交的后台入库任务状态。</p>
 */
@Data
@Schema(description = "目录异步入库进度查询")
public class GetCatalogDownloadJobProgressRequest {

    /** {@code startCatalogDownload} 返回的任务 ID */
    @Schema(description = "startCatalogDownload 返回的任务 ID")
    private String jobId;
}
