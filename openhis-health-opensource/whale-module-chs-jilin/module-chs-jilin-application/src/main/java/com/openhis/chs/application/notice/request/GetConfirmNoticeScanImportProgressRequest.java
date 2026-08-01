/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.notice.request;

import lombok.Data;

/**
 * 轮询确认入库进度请求。
 */
@Data
public class GetConfirmNoticeScanImportProgressRequest {

    /** {@code startConfirmNoticeScanImport} 返回的任务 ID。 */
    private String jobId;
}
