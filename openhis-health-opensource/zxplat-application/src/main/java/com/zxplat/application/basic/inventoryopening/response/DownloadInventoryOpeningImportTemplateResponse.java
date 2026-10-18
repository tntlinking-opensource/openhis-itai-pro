/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.inventoryopening.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 下载期初导入模板响应（IF-BAS-INV-OPN-12）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "期初导入模板文件")
public class DownloadInventoryOpeningImportTemplateResponse {

    /** 建议下载文件名。 */
    @Schema(description = "文件名")
    private String fileName;

    /** MIME 类型。 */
    @Schema(description = "内容类型")
    private String contentType;

    /** xlsx 字节。 */
    @Schema(description = "文件字节")
    private byte[] fileBytes;
}
