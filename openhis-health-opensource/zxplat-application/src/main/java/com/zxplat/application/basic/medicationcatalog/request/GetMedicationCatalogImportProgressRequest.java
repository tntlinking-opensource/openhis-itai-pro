/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.medicationcatalog.request;

import lombok.Data;

/**
 * 轮询药品目录导入进度请求。
 */
@Data
public class GetMedicationCatalogImportProgressRequest {

    /** {@code startImportMedicationCatalog} 返回的任务 ID。 */
    private String jobId;
}
