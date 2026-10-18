/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.diagnosiscatalog.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 国家临床版 2.0 Excel 导入请求（网关无法注入 MultipartFile，入参为文件字节）。
 */
@Data
@Schema(description = "国家临床版2.0导入请求")
public class ImportClinicalV2CatalogRequest {

    /** 上传 xls/xlsx 文件原始字节（JSON 中为 base64）。 */
    @Schema(description = "文件字节（base64）")
    private byte[] fileBytes;

    /** 原始文件名（可选，审计用）。 */
    @Schema(description = "文件名")
    private String fileName;
}
