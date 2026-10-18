/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.catalog.result;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 目录下载游标 DTO */
@Data
@Accessors(chain = true)
@Schema(description = "医保目录下载游标")
public class CatalogDownloadCursorResult {

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "游标主键")
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "费用性质/契约主键")
    private Long contractId;

    @Schema(description = "接口号infno")
    private String infno;

    @Schema(description = "本地版本 local_ver")
    private String localVer;

    @Schema(description = "上次文件查询 last_file_qury_no")
    private String lastFileQuryNo;

    @Schema(description = "上次文件名last_filename")
    private String lastFilename;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "上次成功时间 last_success_at")
    private LocalDateTime lastSuccessAt;

    @Schema(description = "接口号中文说明")
    private String infnoLabel;

    @Schema(description = "本地版本短显示（过长中间省略）")
    private String localVerShort;

    @Schema(description = "同步状态说明")
    private String syncStatusText;

    @Schema(description = "末次文件名短显示")
    private String lastFilenameShort;

    @Schema(description = "末次 ZIP 是否已在本地缓存")
    private Boolean lastZipCached;

    @Schema(description = "本地镜像行数（该契约+目录类型）")
    private Long localCatalogRowCount;
}
