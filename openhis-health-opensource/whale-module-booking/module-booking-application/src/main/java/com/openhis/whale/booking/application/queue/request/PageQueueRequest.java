/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.queue.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 候诊队列分页请求（5.6）。
 */
@Getter
@Setter
public class PageQueueRequest {

    /** 机构主键。 */
    @NotNull
    private Long organizationId;

    /** 诊室/去向地点主键。 */
    @NotNull
    private Long destinationLocationId;

    /** 页码（1-based）。 */
    @NotNull
    private Integer pageNo;

    /** 页大小。 */
    @NotNull
    private Integer pageSize;
}
