/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.catalog.dto;

import com.openhis.chs.api.dto.catalog.CatalogFileDownloadOutput;

/**
 * 阶段一单个版本包：平台文件元数据 + 本包行数/版本链（TXT 不落内存，入库从 ZIP 缓存读）。
 */
public record CatalogDownloadVersionBundle(
    String requestVer,
    CatalogFileDownloadOutput meta,
    boolean zipFromCache,
    int txtLineCount,
    String txtVersionName,
    String nextLocalVer) {
}
