/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.chs.domain.catalog.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 目录下载游标 DTO。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医保目录下载游标")
public class CatalogDownloadCursorDto {

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "游标主键")
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "费用性质/契约主键")
    private Long contractId;

    @Schema(description = "接口号 infno")
    private String infno;

    @Schema(description = "本地版本 local_ver")
    private String localVer;

    @Schema(description = "上次文件查询号 last_file_qury_no")
    private String lastFileQuryNo;

    @Schema(description = "上次文件名 last_filename")
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
