/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.notice.support.dto;

import com.zxplat.plugin.chs.domain.notice.model.ApplyNoticeDocResult;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 异步应用任务（内存态）：按行进度可轮询；支持多文件串行。
 */
@Data
public class NoticeApplyJob {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";

    private String jobId;
    private volatile String status = STATUS_PENDING;
    private volatile int total;
    private volatile int processed;
    private volatile String message;
    private Long tenantId;
    /** 当前/首个文件（兼容单文件进度展示）。 */
    private Long docId;
    private Long operatorId;
    private volatile Long currentDocId;
    private volatile int docTotal;
    private volatile int docProcessed;
    private final List<Long> remainingDocIds = new ArrayList<>();
    private volatile int appliedLineCount;
    private volatile int failedLineCount;
    private volatile int writebackCount;
    private volatile int syncedUnchargedCount;

    /**
     * 拷贝领域结果到任务计数（覆盖）。
     *
     * @param result 领域结果
     */
    public void fillFromResult(ApplyNoticeDocResult result) {
        if (result == null) {
            return;
        }
        this.appliedLineCount = result.getAppliedLineCount();
        this.failedLineCount = result.getFailedLineCount();
        this.writebackCount = result.getWritebackCount();
        this.syncedUnchargedCount = result.getSyncedUnchargedCount();
    }

    /**
     * 累加领域结果（多文件串行）。
     *
     * @param result 领域结果
     */
    public void addFromResult(ApplyNoticeDocResult result) {
        if (result == null) {
            return;
        }
        this.appliedLineCount += result.getAppliedLineCount();
        this.failedLineCount += result.getFailedLineCount();
        this.writebackCount += result.getWritebackCount();
        this.syncedUnchargedCount += result.getSyncedUnchargedCount();
    }
}
