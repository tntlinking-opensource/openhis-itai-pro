/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.procedurecatalog.support;

import lombok.Data;

/**
 * 国家临床版手术 3.0 异步导入任务（内存态）。
 */
@Data
public class ClinicalProcV3CatalogImportJob {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";

    public static final String STAGE_PARSING = "PARSING";
    public static final String STAGE_WRITING = "WRITING";
    public static final String STAGE_DONE = "DONE";

    private String jobId;
    private volatile String status = STATUS_PENDING;
    private volatile String stage;
    private volatile int percent;
    private volatile int total;
    private volatile int processed;
    private volatile int successCount;
    private volatile String message;
    private Long tenantId;
    private String fileName;
    private byte[] fileBytes;
}
