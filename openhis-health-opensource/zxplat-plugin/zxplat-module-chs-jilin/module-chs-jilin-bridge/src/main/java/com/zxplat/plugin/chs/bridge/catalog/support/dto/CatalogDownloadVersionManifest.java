/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.catalog.support.dto;

import com.zxplat.plugin.chs.gateway.scheme.catalog.model.CatFileDownloadOutput;

/**
 * 阶段一版本清单（轻量摘要，不含 TXT 字节）。
 * <p>供异步入库阶段按清单从 {@link com.zxplat.plugin.chs.bridge.catalog.support.CatalogDownloadZipCache}
 * 读取 ZIP 并流式解析，避免在内存中持有大文件内容。</p>
 *
 * @param requestVer      本轮请求 {@code data.ver}
 * @param meta            13xx 平台返回的文件元数据
 * @param txtVersionName  TXT 版本链名称（下一 ver 推导依据）
 * @param nextLocalVer    入库成功后写入游标的本地版本号
 */
public record CatalogDownloadVersionManifest(
    String requestVer,
    CatFileDownloadOutput meta,
    String txtVersionName,
    String nextLocalVer) {

    /** 从完整版本包提取异步入库所需的轻量清单字段。 */
    public static CatalogDownloadVersionManifest fromBundle(CatalogDownloadVersionBundle bundle) {
        return new CatalogDownloadVersionManifest(
            bundle.requestVer(),
            bundle.meta(),
            bundle.txtVersionName(),
            bundle.nextLocalVer());
    }
}
