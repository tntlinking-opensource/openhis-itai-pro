/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.catalog.download.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 目录下载游标列表（UI 出参）。
 * <p>按 infno 汇总本地增量下载游标，供目录下载管理页展示同步进度。</p>
 */
@Data
@Accessors(chain = true)
public class ListCatalogDownloadCursorsResponse {

    /** 各 infno 游标行列表 */
    private List<CatalogDownloadCursorResponse> rows = new ArrayList<>();
}
