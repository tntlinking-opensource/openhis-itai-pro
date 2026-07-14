/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.catalog.download.request;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 查询目录下载游标列表（UI 入参）。
 * <p>对应 {@code InsuranceCatalogDownloadAppService#listDownloadCursors}，
 * 返回各 infno（1301/1305 等）的增量下载进度与本地版本号。</p>
 */
@Data
@Accessors(chain = true)
public class ListDownloadCursorsRequest {

    /** 契约业务编号（与 contractId 二选一，由应用层解析为契约主键） */
    private String contractBusNo;
}
