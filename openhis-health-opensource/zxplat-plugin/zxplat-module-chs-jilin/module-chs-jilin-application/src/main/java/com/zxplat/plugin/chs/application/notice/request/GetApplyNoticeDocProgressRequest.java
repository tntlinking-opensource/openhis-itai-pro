/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.notice.request;

import lombok.Data;

/**
 * 轮询应用进度。
 */
@Data
public class GetApplyNoticeDocProgressRequest {

    private String jobId;
}
