/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.devicecatalog.request;

import lombok.Data;

/**
 * 耗材目录批量导入请求（网关无法注入 {@code MultipartFile} 时的字节载体）。
 */
@Data
public class ImportDeviceCatalogRequest {

    /** 上传 xlsx 文件的原始字节。 */
    private byte[] fileBytes;

    /** 原始文件名（审计/日志；可为空）。 */
    private String fileName;
}
