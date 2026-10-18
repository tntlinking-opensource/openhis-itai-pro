/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacsimaging.application.capture.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 采集详情请求（IF-PACS-CAP-03）。
 */
@Getter
@Setter
public class GetCaptureDetailRequest {

    /** 检查工单主键。 */
    private Long workItemId;
}
