/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.notice.request;

import lombok.Data;

/**
 * 轮询医保文件目录扫描进度请求。
 */
@Data
public class GetScanNoticeCanonicalDirProgressRequest {

    /** {@code startScanNoticeCanonicalDir} 返回的任务 ID。 */
    private String jobId;
}
