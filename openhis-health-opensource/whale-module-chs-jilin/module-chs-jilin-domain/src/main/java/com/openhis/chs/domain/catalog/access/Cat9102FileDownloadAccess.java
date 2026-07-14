/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.catalog.access;

/**
 * 【9102】文件下载出站端口。
 *
 * <p><b>职责：</b>按文件查询号下载目录 ZIP / 对账结果 ZIP 等字节流。</p>
 * <p><b>实现位置：</b>{@code com.openhis.chs.bridge.catalog.invoker.Cat9102FileDownloadAccessImpl}</p>
 * <p><b>调用方：</b>{@code CatalogDownloadPhase1Workflow}、{@code Recon3202Workflow}</p>
 * <p><b>架构边界：</b>application 只依赖本包接口，禁止直接引用 {@code com.openhis.chs.gateway.*}</p>
 * <p><b>迁移阶段：</b>P12</p>
 */
public interface Cat9102FileDownloadAccess {

    /**
     * 按文件查询号与文件名下载 ZIP 字节流。
     *
     * <p>出站上下文在 bridge 实现内组装，调用方无需感知 gateway。</p>
     *
     * @param contractBusNo 契约业务编号
     * @param fileQuryNo    平台文件查询号
     * @param filename      平台文件名
     * @param fixmedinsCode 定点机构编码
     * @return ZIP 文件字节流
     */
    byte[] download(String contractBusNo, String fileQuryNo, String filename, String fixmedinsCode);
}
