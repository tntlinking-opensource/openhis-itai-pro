/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.notice.support.dto;

import lombok.Data;

/**
 * 异步扫描任务进度（内存态，供前端轮询）。
 */
@Data
public class NoticeScanJob {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";

    /** 任务 ID。 */
    private String jobId;

    /** PENDING / RUNNING / SUCCEEDED / FAILED。 */
    private volatile String status = STATUS_PENDING;

    /** 一级子目录总数。 */
    private volatile int total;

    /** 已处理个数。 */
    private volatile int processed;

    /** 当前正在解析的子目录名。 */
    private volatile String currentPackDir;

    /** 失败或阶段提示。 */
    private volatile String message;

    /** 成功时的扫描会话（含 scanToken）。 */
    private volatile NoticeScanSession session;
}
