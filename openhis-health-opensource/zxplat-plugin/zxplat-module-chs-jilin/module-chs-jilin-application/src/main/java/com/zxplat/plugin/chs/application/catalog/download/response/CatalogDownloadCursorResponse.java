/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.catalog.download.response;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 目录下载游标单行（UI 出参）。
 * <p>反映 1301/1305 等文件型接口的增量下载进度：本地版本号、上次成功文件信息与同步时间。</p>
 * <p>{@code id}/{@code contractId} 使用字符串，避免前端 JS 大整数精度丢失。</p>
 */
@Data
@Accessors(chain = true)
public class CatalogDownloadCursorResponse {

    /** 游标主键（字符串） */
    private String id;

    /** 费用性质/契约主键（字符串） */
    private String contractId;

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

    /** 接口号中文说明 */
    private String infnoLabel;

    /** 本地版本短显示 */
    private String localVerShort;

    /** 同步状态说明 */
    private String syncStatusText;

    /** 末次文件名短显示 */
    private String lastFilenameShort;

    /** 末次 ZIP 是否已在本地缓存（未探测时可为 null） */
    private Boolean lastZipCached;

    /** 本地镜像行数（该契约 + 目录 list_type） */
    private Long localCatalogRowCount;
}
