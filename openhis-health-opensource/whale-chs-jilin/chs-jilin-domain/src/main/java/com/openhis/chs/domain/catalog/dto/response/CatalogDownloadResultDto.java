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

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 目录文件下载执行结果。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医保目录文件下载结果")
public class CatalogDownloadResultDto {

    @Schema(description = "接口号 infno")
    private String infno;

    @Schema(description = "本次 upsert 行数")
    private int upsertCount;

    @Schema(description = "下载版本批次数")
    private int versionBatches;

    @Schema(description = "本次处理版本总数（同 versionBatches）")
    private int totalVersionCount;

    @Schema(description = "各版本摘要")
    private java.util.List<CatalogDownloadVersionItemDto> versions;

    @Schema(description = "更新后的本地版本 local_ver（最新游标快照）")
    private String localVer;

    @Schema(description = "本次写入的最新游标记录 id")
    private Long latestCursorId;

    @Schema(description = "本次追加的游标快照条数")
    private int cursorSnapshotCount;

    @Schema(description = "流水线阶段摘要")
    private java.util.List<CatalogDownloadPhaseSummaryDto> phaseSummaries;

    @Schema(description = "是否可能仍有新版本可拉取")
    private boolean hasMore;

    @Schema(description = "最后一次文件查询号")
    private String lastFileQuryNo;

    @Schema(description = "最后一次文件名")
    private String lastFilename;
}
