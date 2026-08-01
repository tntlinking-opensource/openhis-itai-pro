/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.devicecatalog.dto;

import lombok.Data;

/**
 * 耗材目录导入相关文件下载响应（模板或错误标注文件字节流包装）。
 */
@Data
public class DeviceCatalogImportFileResponse {

    /** 建议保存或 Content-Disposition 使用的文件名。 */
    private String fileName;

    /** HTTP Content-Type（如 xlsx MIME）。 */
    private String contentType;

    /** 文件二进制内容。 */
    private byte[] fileBytes;
}
