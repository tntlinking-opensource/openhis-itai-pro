/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.pacsimaging.application.config.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 阅片端点列表请求。
 */
@Getter
@Setter
public class ListViewerEndpointsRequest {

    /** 机构主键；空=列全局行。 */
    private Long orgId;
}
