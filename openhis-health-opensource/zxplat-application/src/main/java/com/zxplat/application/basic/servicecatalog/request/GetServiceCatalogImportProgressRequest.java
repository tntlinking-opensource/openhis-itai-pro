/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.servicecatalog.request;

import lombok.Data;

/**
 * 轮询诊疗目录导入进度请求。
 */
@Data
public class GetServiceCatalogImportProgressRequest {

    /** {@code startImportServiceCatalog} 返回的任务 ID。 */
    private String jobId;
}
