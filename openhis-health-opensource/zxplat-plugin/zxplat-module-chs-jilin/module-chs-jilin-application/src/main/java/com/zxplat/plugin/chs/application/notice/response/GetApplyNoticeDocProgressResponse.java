/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.notice.response;

import lombok.Data;

/**
 * 应用进度响应。
 */
@Data
public class GetApplyNoticeDocProgressResponse {

    private String jobId;
    private String status;
    private int total;
    private int processed;
    private int percent;
    private String message;
    private Long currentDocId;
    private int docTotal;
    private int docProcessed;
    private int appliedLineCount;
    private int failedLineCount;
    private int writebackCount;
    private int syncedUnchargedCount;
}
