/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.catalog.result;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 待下载 已探测的单个目录版本摘要（UI 列表） */
@Data
@Accessors(chain = true)
@Schema(description = "目录下载版本摘要")
public class CatalogDownloadVersionItemResult {

    @Schema(description = "序号（从 1 起）")
    private int index;

    @Schema(description = "本次 13xx 文件下载请求使用的本地版本 ver")
    private String requestVer;

    @Schema(description = "请求版本展示用短文本")
    private String requestVerShort;

    @Schema(description = "TXT「版本名称」（本包版本）")
    private String txtVersionName;

    @Schema(description = "本包版本展示用短文本")
    private String txtVersionNameShort;

    @Schema(description = "ZIP 内解析出的下一版本号（txtVersionName，兼容字段）")
    private String nextVersion;

    @Schema(description = "下一版本展示用短文本")
    private String nextVersionShort;

    @Schema(description = "下一包13xx data.ver；为空表示版本链结束")
    private String nextDownloadVer;

    @Schema(description = "下一包13xx ver 展示用短文本")
    private String nextDownloadVerShort;

    @Schema(description = "本版本解析入库行数")
    private int parsedRowCount;

    @Schema(description = "文件查询号")
    private String fileQuryNo;

    @Schema(description = "文件名")
    private String filename;

    @Schema(description = "平台返回 data_cnt")
    private Long dataCnt;

    @Schema(description = "是否命中本地 ZIP 缓存（未9102）")
    private boolean zipFromCache;
}
