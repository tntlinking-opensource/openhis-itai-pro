/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.upload.hqms.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * HQMS CSV 导出响应（body 为文件字节，前端 base64 下载）。
 */
@Getter
@Setter
@Schema(description = "HQMS CSV 导出响应")
public class ExportHqmsCsvResponse {

    /** 建议下载文件名（含 hqmsts_ 前缀与 .csv）。 */
    @Schema(description = "文件名")
    private String fileName;

    /** MIME，固定 text/csv;charset=GBK。 */
    @Schema(description = "内容类型")
    private String contentType;

    /** CSV 文件字节。 */
    @Schema(description = "文件字节")
    private byte[] body;

    /** 成功写入的数据行数（不含表头）。 */
    @Schema(description = "导出行数")
    private Integer exportedRowCount;
}
