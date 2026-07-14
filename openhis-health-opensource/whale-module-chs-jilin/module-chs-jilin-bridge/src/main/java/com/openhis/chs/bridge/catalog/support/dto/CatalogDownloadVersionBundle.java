/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.catalog.support.dto;

import com.openhis.chs.gateway.scheme.catalog.model.CatFileDownloadOutput;

/**
 * 阶段一单个版本包：平台文件元数据与本包统计信息。
 * <p>TXT 内容不落内存，异步入库从 ZIP 缓存读取并流式解析。</p>
 *
 * @param requestVer      本轮请求 {@code data.ver}
 * @param meta            13xx 平台返回的文件元数据（含 file_qury_no、filename 等）
 * @param zipFromCache    本轮 ZIP 是否来自本地缓存（跳过 9102 下载）
 * @param txtLineCount    本包 TXT 有效行数（供进度展示）
 * @param txtVersionName  TXT 版本链名称列取值
 * @param nextLocalVer    本包解析出的下一本地游标版本号
 */
public record CatalogDownloadVersionBundle(
    String requestVer,
    CatFileDownloadOutput meta,
    boolean zipFromCache,
    int txtLineCount,
    String txtVersionName,
    String nextLocalVer) {
}
