/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.inventoryopening.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 上传导入期初单请求（IF-BAS-INV-OPN-13）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "导入期初单", requiredProperties = {"locationId", "fileBytes"})
public class ImportInventoryOpeningRequest {

    /** 目标库房主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "库房主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long locationId;

    /** Excel 文件字节（前端可传 base64 解码后的字节或直接二进制约定）。 */
    @Schema(description = "文件字节", requiredMode = Schema.RequiredMode.REQUIRED)
    private byte[] fileBytes;

    /** 原始文件名（可选，仅展示）。 */
    @Schema(description = "文件名")
    private String fileName;
}
