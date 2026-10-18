/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.inventoryopening.response;

import com.zxplat.application.basic.inventoryopening.dto.InventoryOpeningImportErrorDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 导入期初单响应（IF-BAS-INV-OPN-13）；成功仅 busNo，失败含 errors。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "导入期初响应")
public class ImportInventoryOpeningResponse {

    /** 成功时新建草稿单号。 */
    @Schema(description = "单号")
    private String busNo;

    /** 失败时汇总说明。 */
    @Schema(description = "失败说明")
    private String message;

    /** 失败时逐行错误清单（整单失败、零写入）。 */
    @Schema(description = "错误清单")
    private List<InventoryOpeningImportErrorDto> errors;
}
