/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.upload.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 按日志 ID 重试上传。
 */
@Getter
@Setter
public class RetryUploadRequest {

    /** 上传日志主键。 */
    private Long uploadLogId;
}
