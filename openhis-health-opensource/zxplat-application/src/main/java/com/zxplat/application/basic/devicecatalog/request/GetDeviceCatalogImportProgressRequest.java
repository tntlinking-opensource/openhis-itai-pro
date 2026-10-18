/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.devicecatalog.request;

import lombok.Data;

/**
 * 轮询耗材目录导入进度请求。
 */
@Data
public class GetDeviceCatalogImportProgressRequest {

    /** {@code startImportDeviceCatalog} 返回的任务 ID。 */
    private String jobId;
}
