/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.devicecatalog.request;

import lombok.Data;

/**
 * 下载耗材目录导入错误标注文件请求。
 */
@Data
public class DownloadDeviceCatalogImportErrorRequest {

    /** 导入失败时返回的短期 token。 */
    private String token;
}
