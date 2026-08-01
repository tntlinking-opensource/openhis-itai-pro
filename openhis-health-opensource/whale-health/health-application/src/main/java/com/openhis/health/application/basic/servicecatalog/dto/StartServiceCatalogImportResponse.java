/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.servicecatalog.dto;

import lombok.Data;

/**
 * 启动诊疗目录异步导入响应。
 */
@Data
public class StartServiceCatalogImportResponse {

    /** 任务 ID，用于轮询进度。 */
    private String jobId;
}
