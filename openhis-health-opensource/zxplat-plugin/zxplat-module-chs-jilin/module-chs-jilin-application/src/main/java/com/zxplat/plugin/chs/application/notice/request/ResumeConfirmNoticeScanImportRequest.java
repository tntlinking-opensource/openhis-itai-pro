/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.notice.request;

import lombok.Data;

/**
 * 续跑中断的确认入库任务请求。
 */
@Data
public class ResumeConfirmNoticeScanImportRequest {

    /** 未完成的入库任务 ID。 */
    private String jobId;
}
