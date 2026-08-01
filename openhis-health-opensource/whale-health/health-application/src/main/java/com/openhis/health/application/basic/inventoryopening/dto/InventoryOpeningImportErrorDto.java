/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.inventoryopening.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Excel 导入错误项（IF-BAS-INV-OPN-13 整单失败清单）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "期初导入错误项")
public class InventoryOpeningImportErrorDto {

    /** Excel 数据行号（1-based，含表头后的业务行）。 */
    @Schema(description = "Excel 行号")
    private Integer excelRowNum;

    /** 出错列名。 */
    @Schema(description = "列名")
    private String columnName;

    /** 错误码。 */
    @Schema(description = "错误码")
    private String code;

    /** 错误说明。 */
    @Schema(description = "错误说明")
    private String message;
}
