/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacsimaging.application.viewer.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 打开阅片请求。
 */
@Getter
@Setter
public class LaunchViewerRequest {

    /** 工单主键。 */
    private Long workItemId;
}
