/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.group.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 团检名册已建单后加/减项请求（筹备页或登记台复用）。
 */
@Getter
@Setter
public class AdjustGroupRosterServiceRequestsRequest {

    /** 名册主键。 */
    private Long rosterId;

    /** 加项 ActivityDefinition 主键列表。 */
    private List<Long> addActivityIds;

    /** 减项服务申请主键列表。 */
    private List<Long> removeServiceRequestIds;
}
