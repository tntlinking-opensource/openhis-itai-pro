/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.documentdetail.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 单据详情导出出参（IF-INV-DDC-{域}-04）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "单据详情导出 IF-INV-DDC-04")
public class ExportDocumentDetailsResponse {

    @Schema(description = "文件名 fileName")
    /** 文件名 fileName。 */
    private String fileName;

    @Schema(description = "MIME contentType")
    /** MIME contentType。 */
    private String contentType;

    @Schema(description = "文件字节 fileBytes")
    /** 文件字节 fileBytes。 */
    private byte[] fileBytes;
}
