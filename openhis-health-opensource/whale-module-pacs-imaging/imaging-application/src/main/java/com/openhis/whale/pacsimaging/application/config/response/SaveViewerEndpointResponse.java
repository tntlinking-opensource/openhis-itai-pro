/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.pacsimaging.application.config.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 保存阅片端点响应。
 */
@Getter
@Setter
public class SaveViewerEndpointResponse {

    /** 主键。 */
    private Long id;

    /** 机构主键。 */
    private Long orgId;

    /** 模态码。 */
    private String modalityCode;

    /** 阅片根地址。 */
    private String baseUrl;

    /** 是否启用。 */
    private Boolean enabled;
}
