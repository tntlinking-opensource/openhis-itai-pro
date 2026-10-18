/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.notice.response;

import lombok.Data;

/**
 * 启动异步扫描响应。
 */
@Data
public class StartScanNoticeCanonicalDirResponse {

    /** 任务 ID，用于轮询进度。 */
    private String jobId;
}
