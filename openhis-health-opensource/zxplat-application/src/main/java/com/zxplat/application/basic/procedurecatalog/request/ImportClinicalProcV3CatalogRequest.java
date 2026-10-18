/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.procedurecatalog.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 国家临床版手术 3.0 Excel 导入请求（网关无法注入 MultipartFile，入参为文件字节）。
 */
@Data
@Schema(description = "国家临床版手术3.0导入请求")
public class ImportClinicalProcV3CatalogRequest {

    @Schema(description = "文件字节（base64）")
    private byte[] fileBytes;

    @Schema(description = "文件名")
    private String fileName;
}
