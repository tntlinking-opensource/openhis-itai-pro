/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.registration.request;

import lombok.Data;

import java.util.List;

/**
 * 体检项目加/减项请求（REG-04）。
 */
@Data
public class AdjustServiceRequestsRequest {

    /** 就诊主键。 */
    private Long encounterId;

    /** 加项 ActivityDefinition 主键列表。 */
    private List<Long> addActivityIds;

    /** 减项服务申请主键列表。 */
    private List<Long> removeServiceRequestIds;
}
