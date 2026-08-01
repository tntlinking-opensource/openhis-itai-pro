/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.procedurecatalog.support;

import lombok.Getter;
import lombok.Setter;

/**
 * 医保手术与操作异步导入任务状态。
 */
@Getter
@Setter
public class GeneralOpCatalogImportJob {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";

    public static final String STAGE_LOADING = "LOADING";
    public static final String STAGE_WRITING = "WRITING";
    public static final String STAGE_DONE = "DONE";

    private String jobId;
    private String status;
    private String stage;
    private int percent;
    private int total;
    private int processed;
    private int successCount;
    private String message;
    private Long tenantId;
}
