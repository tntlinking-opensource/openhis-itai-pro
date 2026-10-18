/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.catalog.access;

import com.zxplat.plugin.chs.domain.catalog.command.StartCatalogDownloadCommand;
import com.zxplat.plugin.chs.domain.catalog.result.CatalogDownloadJobProgressResult;
import com.zxplat.plugin.chs.domain.catalog.result.CatalogDownloadJobStartResult;
import com.zxplat.plugin.chs.domain.catalog.result.CatalogDownloadPreviewResult;

/**
 * 文件型目录下载编排出站端口。
 *
 * <p><b>职责：</b>13xx/9102 Phase1 拉 ZIP 与异步入库任务编排。</p>
 * <p><b>实现位置：</b>{@code com.zxplat.plugin.chs.bridge.catalog.workflow.CatalogDownloadWorkflow}</p>
 * <p><b>调用方：</b>{@code InsuranceCatalogAppService}</p>
 * <p><b>架构边界：</b>application 只依赖本包接口，禁止直接引用 {@code com.zxplat.plugin.chs.gateway.*}</p>
 * <p><b>迁移阶段：</b>P13</p>
 */
public interface CatalogDownloadAccess {

    /**
     * 探测下载：拉 ZIP 预览，不提交入库任务。
     *
     * @param request 下载启动命令
     * @return 预览结果（版本清单等）
     */
    CatalogDownloadPreviewResult previewCatalogDownload(StartCatalogDownloadCommand request);

    /**
     * 启动下载：Phase1 拉 ZIP 后提交异步入库。
     *
     * @param request 下载启动命令
     * @return 任务 ID 与启动摘要
     */
    CatalogDownloadJobStartResult startCatalogDownload(StartCatalogDownloadCommand request);

    /**
     * 查询异步入库任务进度。
     *
     * @param jobId 异步入库任务 ID
     * @return 任务进度与状态
     */
    CatalogDownloadJobProgressResult getCatalogDownloadJobProgress(String jobId);
}
