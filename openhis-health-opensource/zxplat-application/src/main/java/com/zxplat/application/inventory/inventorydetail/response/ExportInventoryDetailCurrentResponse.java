/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.inventorydetail.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 现库导出 Excel 出参（IF-INV-DTL-06）。
 *
 * <p>{@code truncated=true} 表示命中行数达到导出上限（与汇总同阈），导出文件仅含前 N 行，与 {@link SumInventoryDetailCurrentResponse#getTruncated()} 语义对齐。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "现库库存明细导出 IF-INV-DTL-06")
public class ExportInventoryDetailCurrentResponse {
    /** 下载文件名。 */
    @Schema(description = "下载文件名")
    private String fileName;

    /** 内容类型。 */
    @Schema(description = "内容类型")
    private String contentType;

    /** 工作簿字节流。 */
    @Schema(description = "工作簿字节流")
    private byte[] body;

    /** 导出数据行数。 */
    @Schema(description = "导出数据行数")
    private Integer exportedRowCount;

    /** 是否因行数上限截断。 */
    @Schema(description = "是否因行数上限截断")
    private Boolean truncated;
}
