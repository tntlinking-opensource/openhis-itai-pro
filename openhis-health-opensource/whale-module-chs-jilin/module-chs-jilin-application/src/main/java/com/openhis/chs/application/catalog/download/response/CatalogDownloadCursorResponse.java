/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.catalog.download.response;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 目录下载游标单行（UI 出参）。
 * <p>反映 1301/1305 等文件型接口的增量下载进度：本地版本号、上次成功文件信息与同步时间。</p>
 */
@Data
@Accessors(chain = true)
public class CatalogDownloadCursorResponse {

    /** 费用性质/契约主键 */
    private Long contractId;

    /** 医保接口编号（如 1301、1305） */
    private String infno;

    /** 本地版本号，作为下次请求 data.ver */
    private String localVer;

    /** 上次成功下载的文件查询号 */
    private String lastFileQuryNo;

    /** 上次成功下载的文件名 */
    private String lastFilename;

    /** 上次成功下载时间（ISO 字符串） */
    private String lastSuccessAt;
}
