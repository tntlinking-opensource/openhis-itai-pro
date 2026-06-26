/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.catalog.dto;

import com.openhis.chs.api.dto.catalog.CatalogFileDownloadOutput;

/** 阶段一版本清单（不含 TXT 字节，供异步入库从 ZIP 缓存读取）。 */
public record CatalogDownloadVersionManifest(
    String requestVer,
    CatalogFileDownloadOutput meta,
    String txtVersionName,
    String nextLocalVer) {

    public static CatalogDownloadVersionManifest fromBundle(CatalogDownloadVersionBundle bundle) {
        return new CatalogDownloadVersionManifest(
            bundle.requestVer(),
            bundle.meta(),
            bundle.txtVersionName(),
            bundle.nextLocalVer());
    }
}
