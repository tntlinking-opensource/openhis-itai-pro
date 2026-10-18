/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacsimaging.application.config.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 匹配阅片端点响应。
 */
@Getter
@Setter
public class ResolveViewerEndpointResponse {

    /** 命中的根地址。 */
    private String baseUrl;

    /**
     * 匹配来源：{@code ORG_MODALITY} / {@code ORG_DEFAULT} / {@code GLOBAL}。
     */
    private String matchedBy;
}
