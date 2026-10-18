/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.devicecatalog.dto;

import lombok.Data;

/**
 * 启动耗材目录异步导入响应。
 */
@Data
public class StartDeviceCatalogImportResponse {

    /** 任务 ID，用于轮询进度。 */
    private String jobId;
}
