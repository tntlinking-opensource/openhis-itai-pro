/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.notice.response;

import lombok.Data;

/**
 * 医保文件目录扫描进度；SUCCEEDED 时带完整扫描结果。
 */
@Data
public class GetScanNoticeCanonicalDirProgressResponse {

    /** 任务 ID。 */
    private String jobId;

    /** PENDING / RUNNING / SUCCEEDED / FAILED。 */
    private String status;

    /** 一级子目录总数。 */
    private int total;

    /** 已处理个数。 */
    private int processed;

    /** 0～100。 */
    private int percent;

    /** 当前子目录名。 */
    private String currentPackDir;

    /** 失败或阶段提示。 */
    private String message;

    /** 成功时同 {@link ScanNoticeCanonicalDirResponse}。 */
    private ScanNoticeCanonicalDirResponse result;
}
