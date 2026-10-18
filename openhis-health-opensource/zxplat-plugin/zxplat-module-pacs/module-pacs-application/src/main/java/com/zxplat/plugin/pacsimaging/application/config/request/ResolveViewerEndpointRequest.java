/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacsimaging.application.config.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 匹配阅片端点请求。
 */
@Getter
@Setter
public class ResolveViewerEndpointRequest {

    /** 机构主键；可空。 */
    private Long orgId;

    /** 模态码；可空。 */
    private String modalityCode;
}
