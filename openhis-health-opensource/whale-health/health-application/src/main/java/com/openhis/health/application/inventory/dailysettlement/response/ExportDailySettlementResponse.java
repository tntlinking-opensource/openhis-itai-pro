/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.dailysettlement.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 库房日结导出出参（IF-INV-SET-03）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "库房日结导出 IF-INV-SET-03")
public class ExportDailySettlementResponse {

    /** 下载文件名。 */
    @Schema(description = "下载文件名")
    private String fileName;

    /** MIME 类型。 */
    @Schema(description = "MIME 类型")
    private String contentType;

    /** Excel 二进制。 */
    @Schema(description = "Excel 二进制")
    private byte[] fileBytes;

    /** 是否触顶导出行上限。 */
    @Schema(description = "是否触顶导出行上限")
    private Boolean truncated;

    /** 实际导出行数。 */
    @Schema(description = "实际导出行数")
    private Integer exportedRowCount;
}
