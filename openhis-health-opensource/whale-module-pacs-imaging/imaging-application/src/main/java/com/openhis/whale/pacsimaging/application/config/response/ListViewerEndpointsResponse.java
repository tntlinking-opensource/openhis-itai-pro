/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.pacsimaging.application.config.response;

import com.openhis.whale.pacsimaging.application.config.dto.ViewerEndpointRowDto;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 阅片端点列表响应。
 */
@Getter
@Setter
public class ListViewerEndpointsResponse {

    /** 端点行。 */
    private List<ViewerEndpointRowDto> records = new ArrayList<>();
}
