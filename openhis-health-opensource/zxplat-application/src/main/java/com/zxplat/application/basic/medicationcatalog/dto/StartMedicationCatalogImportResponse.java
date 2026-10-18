/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.medicationcatalog.dto;

import lombok.Data;

import java.util.List;

/**
 * 启动药品目录异步导入响应。
 */
@Data
public class StartMedicationCatalogImportResponse {

    /** 任务 ID，用于轮询进度。 */
    private String jobId;
}
