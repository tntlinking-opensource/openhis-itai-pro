/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.notice.response;

import lombok.Data;

/**
 * 启动异步确认入库响应。
 */
@Data
public class StartConfirmNoticeScanImportResponse {

    /** 任务 ID。 */
    private String jobId;
}
